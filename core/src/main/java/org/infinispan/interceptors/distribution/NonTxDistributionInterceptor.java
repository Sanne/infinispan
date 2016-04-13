package org.infinispan.interceptors.distribution;

import org.infinispan.commands.CommandInvocationId;
import org.infinispan.commands.functional.*;
import org.infinispan.commands.read.AbstractDataCommand;
import org.infinispan.commands.read.GetCacheEntryCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.read.RemoteFetchingCommand;
import org.infinispan.commands.write.DataWriteCommand;
import org.infinispan.commands.write.DataWriteCommandResponse;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.util.Util;
import org.infinispan.container.EntryFactory;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.util.ReadOnlySegmentAwareMap;
import org.infinispan.distribution.util.ReadOnlySegmentAwareSet;
import org.infinispan.remoting.RemoteException;
import org.infinispan.remoting.inboundhandler.DeliverOrder;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.rpc.RpcOptions;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.concurrent.CompletableFutures;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Non-transactional interceptor used by distributed caches that support concurrent writes.
 * It is implemented based on lock forwarding. E.g.
 * - 'k' is written on node A, owners(k)={B,C}
 * - A forwards the given command to B
 * - B acquires a lock on 'k' then it forwards it to the remaining owners: C
 * - C applies the change and returns to B (no lock acquisition is needed)
 * - B applies the result as well, releases the lock and returns the result of the operation to A.
 * <p/>
 * Note that even though this introduces an additional RPC (the forwarding), it behaves very well in conjunction with
 * consistent-hash aware hotrod clients which connect directly to the lock owner.
 *
 * @author Mircea Markus
 * @deprecated Since 8.2, no longer public API.
 */
@Deprecated
public class NonTxDistributionInterceptor extends BaseDistributionInterceptor {

   private static Log log = LogFactory.getLog(NonTxDistributionInterceptor.class);
   private static final boolean trace = log.isTraceEnabled();
   private final Map<CommandInvocationId, BackupOwnerAckCollector> collectorMap;

   public NonTxDistributionInterceptor() {
      collectorMap = new ConcurrentHashMap<>();
   }

   @Override
   public Object visitGetKeyValueCommand(InvocationContext ctx, GetKeyValueCommand command) throws Throwable {
      return visitGetCommand(ctx, command);
   }

   @Override
   public Object visitGetCacheEntryCommand(InvocationContext ctx, GetCacheEntryCommand command) throws Throwable {
      return visitGetCommand(ctx, command);
   }

   private <T extends AbstractDataCommand & RemoteFetchingCommand> Object visitGetCommand(
         InvocationContext ctx, T command) throws Throwable {
      if (ctx.isOriginLocal()) {
         Object key = command.getKey();
         CacheEntry entry = ctx.lookupEntry(key);
         if (valueIsMissing(entry)) {
            // First try to fetch from remote owners
            InternalCacheEntry remoteEntry = null;
            if (readNeedsRemoteValue(ctx, command)) {
               if (trace) log.tracef("Doing a remote get for key %s", key);
               remoteEntry = retrieveFromRemoteSource(key, ctx, false, command, false);
               command.setRemotelyFetchedValue(remoteEntry);
               if (remoteEntry != null) {
                  entryFactory.wrapExternalEntry(ctx, key, remoteEntry, EntryFactory.Wrap.STORE, false);
               }
            }
            if (remoteEntry == null) {
               // Then search for the entry in the local data container, in case we became an owner after
               // EntryWrappingInterceptor and the local node is now the only owner.
               // TODO Check fails if the entry was passivated
               InternalCacheEntry localEntry = fetchValueLocallyIfAvailable(dm.getReadConsistentHash(), key);
               if (localEntry != null) {
                  entryFactory.wrapExternalEntry(ctx, key, localEntry, EntryFactory.Wrap.STORE, false);
               }
            }
         }
      }
      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) throws Throwable {
      return threeStepsWrite(ctx, command);
   }

   @Override
   public Object visitPutMapCommand(InvocationContext ctx, PutMapCommand command) throws Throwable {
      Map<Object, Object> originalMap = command.getMap();
      ConsistentHash ch = dm.getConsistentHash();
      Address localAddress = rpcManager.getAddress();
      if (ctx.isOriginLocal()) {
         // TODO: if async we don't need to do futures...
         RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
         Map<Address, Map<Object, Object>> primaryEntries = new HashMap<>();
         for (Entry<Object, Object> entry : originalMap.entrySet()) {
            Object key = entry.getKey();
            Address owner = ch.locatePrimaryOwner(key);
            if (localAddress.equals(owner)) {
               continue;
            }
            Map<Object, Object> currentEntries = primaryEntries.get(owner);
            if (currentEntries == null) {
               currentEntries = new HashMap<>();
               primaryEntries.put(owner, currentEntries);
            }
            currentEntries.put(key, entry.getValue());
         }
         List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(
                 rpcManager.getMembers().size() - 1);
         for (Entry<Address, Map<Object, Object>> ownerEntry : primaryEntries.entrySet()) {
            Map<Object, Object> entries = ownerEntry.getValue();
            if (!entries.isEmpty()) {
               PutMapCommand copy = new PutMapCommand(command);
               copy.setMap(entries);
               CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(ownerEntry.getKey()), copy, options);
               futures.add(future);
            }
         }
         if (futures.size() > 0) {
            CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
            CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
            try {
               compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
               throw new RemoteException("Exception while processing put on primary owner", e.getCause());
            } catch (TimeoutException e) {
               throw new CacheException(e);
            }
         }
      }

      if (!command.isForwarded() && ch.getNumOwners() > 1) {
         Map<Address, Map<Object, Object>> backupOwnerEntries = new HashMap<>();
         for (Entry<Object, Object> entry : originalMap.entrySet()) {
            Object key = entry.getKey();
            List<Address> addresses = ch.locateOwners(key);
            if (localAddress.equals(addresses.get(0))) {
               for (int i = 1; i < addresses.size(); ++i) {
                  Address address = addresses.get(i);
                  Map<Object, Object> entries = backupOwnerEntries.get(address);
                  if (entries == null) {
                     entries = new HashMap<>();
                     backupOwnerEntries.put(address, entries);
                  }
                  entries.put(key, entry.getValue());
               }
            }
         }

         int backupOwnerSize = backupOwnerEntries.size();
         if (backupOwnerSize > 0) {
            List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(backupOwnerSize);
            RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
            command.addFlag(Flag.SKIP_LOCKING);
            command.setForwarded(true);

            for (Entry<Address, Map<Object, Object>> addressEntry : backupOwnerEntries.entrySet()) {
               PutMapCommand copy = new PutMapCommand(command);
               copy.setMap(addressEntry.getValue());
               CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                       Collections.singletonList(addressEntry.getKey()), copy, options);
               futures.add(future);
            }
            command.setForwarded(false);
            if (futures.size() > 0) {
               CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
               CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
               try {
                  compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               } catch (ExecutionException e) {
                  throw new RemoteException("Exception while processing put on backup owner", e.getCause());
               } catch (TimeoutException e) {
                  throw new CacheException(e);
               }
            }
         }
      }

      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitRemoveCommand(InvocationContext ctx, RemoveCommand command) throws Throwable {
      return threeStepsWrite(ctx, command);
   }

   @Override
   public Object visitReplaceCommand(InvocationContext ctx, ReplaceCommand command) throws Throwable {
      return threeStepsWrite(ctx, command);
   }

   @Override
   public Object visitReadWriteKeyValueCommand(InvocationContext ctx, ReadWriteKeyValueCommand command) throws Throwable {
      return handleNonTxWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadWriteKeyCommand(InvocationContext ctx, ReadWriteKeyCommand command) throws Throwable {
      return handleNonTxWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadOnlyKeyCommand(InvocationContext ctx, ReadOnlyKeyCommand command) throws Throwable {
      if (ctx.isOriginLocal()) {
         Object key = command.getKey();
         CacheEntry entry = ctx.lookupEntry(key);
         if (valueIsMissing(entry)) {
            // First try to fetch from remote owners
            InternalCacheEntry remoteEntry;
            if (readNeedsRemoteValue(ctx, command)) {
               if (trace)
                  log.tracef("Doing a remote get for key %s", key);
               remoteEntry = retrieveFromRemoteSource(key, ctx, false, command, false);
               // TODO Do we need to do something else instead of setRemotelyFetchedValue?
               // command.setRemotelyFetchedValue(remoteEntry);
               if (remoteEntry != null) {
                  entryFactory.wrapExternalEntry(ctx, key, remoteEntry, EntryFactory.Wrap.STORE, false);
               }
               return command.perform(remoteEntry);
            }

            // Then search for the entry in the local data container, in case we became an owner after
            // EntryWrappingInterceptor and the local node is now the only owner.
            // TODO Check fails if the entry was passivated
            InternalCacheEntry localEntry = fetchValueLocallyIfAvailable(dm.getReadConsistentHash(), key);
            if (localEntry != null) {
               entryFactory.wrapExternalEntry(ctx, key, localEntry, EntryFactory.Wrap.STORE, false);
            }
            return command.perform(localEntry);
         }
      }
      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitReadOnlyManyCommand(InvocationContext ctx, ReadOnlyManyCommand command) throws Throwable {
      return super.visitReadOnlyManyCommand(ctx, command);    // TODO: Customise this generated block
   }

   @Override
   public Object visitWriteOnlyManyEntriesCommand(InvocationContext ctx, WriteOnlyManyEntriesCommand command) throws Throwable {
      // TODO: Refactor this and visitPutMapCommand...
      // TODO: Could PutMap be reimplemented based on WriteOnlyManyEntriesCommand?
      Map<Object, Object> originalMap = command.getEntries();
      ConsistentHash ch = dm.getConsistentHash();
      Address localAddress = rpcManager.getAddress();
      if (ctx.isOriginLocal()) {
         List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(
            rpcManager.getMembers().size() - 1);
         // TODO: if async we don't need to do futures...
         RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
         for (Address member : rpcManager.getMembers()) {
            if (member.equals(rpcManager.getAddress())) {
               continue;
            }
            Set<Integer> segments = ch.getPrimarySegmentsForOwner(member);
            if (!segments.isEmpty()) {
               Map<Object, Object> segmentEntriesMap =
                  new ReadOnlySegmentAwareMap<>(originalMap, ch, segments);
               if (!segmentEntriesMap.isEmpty()) {
                  WriteOnlyManyEntriesCommand copy = new WriteOnlyManyEntriesCommand(command);
                  copy.setEntries(segmentEntriesMap);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(member), copy, options);
                  futures.add(future);
               }
            }
         }
         if (futures.size() > 0) {
            CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
            CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
            try {
               compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
               throw new RemoteException("Exception while processing put on primary owner", e.getCause());
            } catch (TimeoutException e) {
               throw new CacheException(e);
            }
         }
      }

      if (!command.isForwarded() && ch.getNumOwners() > 1) {
         // Now we find all the segments that we own and map our backups to those
         Map<Address, Set<Integer>> backupOwnerSegments = new HashMap<>();
         int segmentCount = ch.getNumSegments();
         for (int i = 0; i < segmentCount; ++i) {
            Iterator<Address> iter = ch.locateOwnersForSegment(i).iterator();

            if (iter.next().equals(localAddress)) {
               while (iter.hasNext()) {
                  Address backupOwner = iter.next();
                  Set<Integer> segments = backupOwnerSegments.get(backupOwner);
                  if (segments == null) {
                     backupOwnerSegments.put(backupOwner, (segments = new HashSet<>()));
                  }
                  segments.add(i);
               }
            }
         }

         int backupOwnerSize = backupOwnerSegments.size();
         if (backupOwnerSize > 0) {
            List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(backupOwnerSize);
            RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
            command.addFlag(Flag.SKIP_LOCKING);
            command.setForwarded(true);

            for (Entry<Address, Set<Integer>> entry : backupOwnerSegments.entrySet()) {
               Set<Integer> segments = entry.getValue();
               Map<Object, Object> segmentEntriesMap =
                  new ReadOnlySegmentAwareMap<>(originalMap, ch, segments);
               if (!segmentEntriesMap.isEmpty()) {
                  WriteOnlyManyEntriesCommand copy = new WriteOnlyManyEntriesCommand(command);
                  copy.setEntries(segmentEntriesMap);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(entry.getKey()), copy, options);
                  futures.add(future);
               }
            }
            command.setForwarded(false);
            if (futures.size() > 0) {
               CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
               CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
               try {
                  compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               } catch (ExecutionException e) {
                  throw new RemoteException("Exception while processing put on backup owner", e.getCause());
               } catch (TimeoutException e) {
                  throw new CacheException(e);
               }
            }
         }
      }

      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitWriteOnlyManyCommand(InvocationContext ctx, WriteOnlyManyCommand command) throws Throwable {
      // TODO: Refactor this, visitWriteOnlyManyCommand and visitPutMapCommand...
      Set<Object> originalMap = command.getKeys();
      ConsistentHash ch = dm.getConsistentHash();
      Address localAddress = rpcManager.getAddress();
      if (ctx.isOriginLocal()) {
         List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(
            rpcManager.getMembers().size() - 1);
         // TODO: if async we don't need to do futures...
         RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
         for (Address member : rpcManager.getMembers()) {
            if (member.equals(rpcManager.getAddress())) {
               continue;
            }
            Set<Integer> segments = ch.getPrimarySegmentsForOwner(member);
            if (!segments.isEmpty()) {
               Set<Object> segmentKeysSet =
                  new ReadOnlySegmentAwareSet<>(originalMap, ch, segments);
               if (!segmentKeysSet.isEmpty()) {
                  WriteOnlyManyCommand copy = new WriteOnlyManyCommand(command);
                  copy.setKeys(segmentKeysSet);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(member), copy, options);
                  futures.add(future);
               }
            }
         }
         if (futures.size() > 0) {
            CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
            CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
            try {
               compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
               throw new RemoteException("Exception while processing put on primary owner", e.getCause());
            } catch (TimeoutException e) {
               throw new CacheException(e);
            }
         }
      }

      if (!command.isForwarded() && ch.getNumOwners() > 1) {
         // Now we find all the segments that we own and map our backups to those
         Map<Address, Set<Integer>> backupOwnerSegments = new HashMap<>();
         int segmentCount = ch.getNumSegments();
         for (int i = 0; i < segmentCount; ++i) {
            Iterator<Address> iter = ch.locateOwnersForSegment(i).iterator();

            if (iter.next().equals(localAddress)) {
               while (iter.hasNext()) {
                  Address backupOwner = iter.next();
                  Set<Integer> segments = backupOwnerSegments.get(backupOwner);
                  if (segments == null) {
                     backupOwnerSegments.put(backupOwner, (segments = new HashSet<>()));
                  }
                  segments.add(i);
               }
            }
         }

         int backupOwnerSize = backupOwnerSegments.size();
         if (backupOwnerSize > 0) {
            List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(backupOwnerSize);
            RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
            command.addFlag(Flag.SKIP_LOCKING);
            command.setForwarded(true);

            for (Entry<Address, Set<Integer>> entry : backupOwnerSegments.entrySet()) {
               Set<Integer> segments = entry.getValue();
               Set<Object> segmentKeysSet =
                  new ReadOnlySegmentAwareSet<>(originalMap, ch, segments);
               if (!segmentKeysSet.isEmpty()) {
                  WriteOnlyManyCommand copy = new WriteOnlyManyCommand(command);
                  copy.setKeys(segmentKeysSet);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(entry.getKey()), copy, options);
                  futures.add(future);
               }
            }
            command.setForwarded(false);
            if (futures.size() > 0) {
               CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
               CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
               try {
                  compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               } catch (ExecutionException e) {
                  throw new RemoteException("Exception while processing put on backup owner", e.getCause());
               } catch (TimeoutException e) {
                  throw new CacheException(e);
               }
            }
         }
      }

      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitReadWriteManyCommand(InvocationContext ctx, ReadWriteManyCommand command) throws Throwable {
      // TODO: Refactor to avoid code duplication
      Set<Object> originalMap = command.getKeys();
      ConsistentHash ch = dm.getConsistentHash();
      Address localAddress = rpcManager.getAddress();
      if (ctx.isOriginLocal()) {
         List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(
            rpcManager.getMembers().size() - 1);
         // TODO: if async we don't need to do futures...
         RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
         for (Address member : rpcManager.getMembers()) {
            if (member.equals(rpcManager.getAddress())) {
               continue;
            }
            Set<Integer> segments = ch.getPrimarySegmentsForOwner(member);
            if (!segments.isEmpty()) {
               Set<Object> segmentKeysSet =
                  new ReadOnlySegmentAwareSet<>(originalMap, ch, segments);
               if (!segmentKeysSet.isEmpty()) {
                  ReadWriteManyCommand copy = new ReadWriteManyCommand(command);
                  copy.setKeys(segmentKeysSet);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(member), copy, options);
                  futures.add(future);
               }
            }
         }
         if (futures.size() > 0) {
            CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
            CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
            try {
               compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               // NOTE: Variation from WriteOnlyManyCommand, we care about returns!
               // TODO: Take into account when refactoring
               for (CompletableFuture<Map<Address,Response>> future : futures) {
                  Map<Address, Response> responses = future.get();
                  for (Response response : responses.values()) {
                     if (response.isSuccessful()) {
                        SuccessfulResponse success = (SuccessfulResponse) response;
                        command.addAllRemoteReturns((List<?>) success.getResponseValue());
                     }
                  }
               }
            } catch (ExecutionException e) {
               throw new RemoteException("Exception while processing put on primary owner", e.getCause());
            } catch (TimeoutException e) {
               throw new CacheException(e);
            }
         }
      }

      if (!command.isForwarded() && ch.getNumOwners() > 1) {
         // Now we find all the segments that we own and map our backups to those
         Map<Address, Set<Integer>> backupOwnerSegments = new HashMap<>();
         int segmentCount = ch.getNumSegments();
         for (int i = 0; i < segmentCount; ++i) {
            Iterator<Address> iter = ch.locateOwnersForSegment(i).iterator();

            if (iter.next().equals(localAddress)) {
               while (iter.hasNext()) {
                  Address backupOwner = iter.next();
                  Set<Integer> segments = backupOwnerSegments.get(backupOwner);
                  if (segments == null) {
                     backupOwnerSegments.put(backupOwner, (segments = new HashSet<>()));
                  }
                  segments.add(i);
               }
            }
         }

         int backupOwnerSize = backupOwnerSegments.size();
         if (backupOwnerSize > 0) {
            List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(backupOwnerSize);
            RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
            command.addFlag(Flag.SKIP_LOCKING);
            command.setForwarded(true);

            for (Entry<Address, Set<Integer>> entry : backupOwnerSegments.entrySet()) {
               Set<Integer> segments = entry.getValue();
               Set<Object> segmentKeysSet =
                  new ReadOnlySegmentAwareSet<>(originalMap, ch, segments);
               if (!segmentKeysSet.isEmpty()) {
                  ReadWriteManyCommand copy = new ReadWriteManyCommand(command);
                  copy.setKeys(segmentKeysSet);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(entry.getKey()), copy, options);
                  futures.add(future);
               }
            }
            command.setForwarded(false);
            if (futures.size() > 0) {
               CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
               CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
               try {
                  compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               } catch (ExecutionException e) {
                  throw new RemoteException("Exception while processing put on backup owner", e.getCause());
               } catch (TimeoutException e) {
                  throw new CacheException(e);
               }
            }
         }
      }

      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitReadWriteManyEntriesCommand(InvocationContext ctx, ReadWriteManyEntriesCommand command) throws Throwable {
      // TODO: Refactor to avoid code duplication
      Map<Object, Object> originalMap = command.getEntries();
      ConsistentHash ch = dm.getConsistentHash();
      Address localAddress = rpcManager.getAddress();
      if (ctx.isOriginLocal()) {
         List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(
            rpcManager.getMembers().size() - 1);
         // TODO: if async we don't need to do futures...
         RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
         for (Address member : rpcManager.getMembers()) {
            if (member.equals(rpcManager.getAddress())) {
               continue;
            }
            Set<Integer> segments = ch.getPrimarySegmentsForOwner(member);
            if (!segments.isEmpty()) {
               Map<Object, Object> segmentEntriesMap =
                  new ReadOnlySegmentAwareMap<>(originalMap, ch, segments);
               if (!segmentEntriesMap.isEmpty()) {
                  ReadWriteManyEntriesCommand copy = new ReadWriteManyEntriesCommand(command);
                  copy.setEntries(segmentEntriesMap);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(member), copy, options);
                  futures.add(future);
               }
            }
         }
         if (futures.size() > 0) {
            CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
            CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
            try {
               compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               // NOTE: Variation from WriteOnlyManyCommand, we care about returns!
               // TODO: Take into account when refactoring
               for (CompletableFuture<Map<Address,Response>> future : futures) {
                  Map<Address, Response> responses = future.get();
                  for (Response response : responses.values()) {
                     if (response.isSuccessful()) {
                        SuccessfulResponse success = (SuccessfulResponse) response;
                        command.addAllRemoteReturns((List<?>) success.getResponseValue());
                     }
                  }
               }
            } catch (ExecutionException e) {
               throw new RemoteException("Exception while processing put on primary owner", e.getCause());
            } catch (TimeoutException e) {
               throw new CacheException(e);
            }
         }
      }

      if (!command.isForwarded() && ch.getNumOwners() > 1) {
         // Now we find all the segments that we own and map our backups to those
         Map<Address, Set<Integer>> backupOwnerSegments = new HashMap<>();
         int segmentCount = ch.getNumSegments();
         for (int i = 0; i < segmentCount; ++i) {
            Iterator<Address> iter = ch.locateOwnersForSegment(i).iterator();

            if (iter.next().equals(localAddress)) {
               while (iter.hasNext()) {
                  Address backupOwner = iter.next();
                  Set<Integer> segments = backupOwnerSegments.get(backupOwner);
                  if (segments == null) {
                     backupOwnerSegments.put(backupOwner, (segments = new HashSet<>()));
                  }
                  segments.add(i);
               }
            }
         }

         int backupOwnerSize = backupOwnerSegments.size();
         if (backupOwnerSize > 0) {
            List<CompletableFuture<Map<Address, Response>>> futures = new ArrayList<>(backupOwnerSize);
            RpcOptions options = rpcManager.getDefaultRpcOptions(isSynchronous(command));
            command.addFlag(Flag.SKIP_LOCKING);
            command.setForwarded(true);

            for (Entry<Address, Set<Integer>> entry : backupOwnerSegments.entrySet()) {
               Set<Integer> segments = entry.getValue();
               Map<Object, Object> segmentEntriesMap =
                  new ReadOnlySegmentAwareMap<>(originalMap, ch, segments);
               if (!segmentEntriesMap.isEmpty()) {
                  ReadWriteManyEntriesCommand copy = new ReadWriteManyEntriesCommand(command);
                  copy.setEntries(segmentEntriesMap);
                  CompletableFuture<Map<Address, Response>> future = rpcManager.invokeRemotelyAsync(
                     Collections.singletonList(entry.getKey()), copy, options);
                  futures.add(future);
               }
            }
            command.setForwarded(false);
            if (futures.size() > 0) {
               CompletableFuture[] futuresArray = new CompletableFuture[futures.size()];
               CompletableFuture<Void> compFuture = CompletableFuture.allOf(futures.toArray(futuresArray));
               try {
                  compFuture.get(options.timeout(), TimeUnit.MILLISECONDS);
               } catch (ExecutionException e) {
                  throw new RemoteException("Exception while processing put on backup owner", e.getCause());
               } catch (TimeoutException e) {
                  throw new CacheException(e);
               }
            }
         }
      }

      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitWriteOnlyKeyValueCommand(InvocationContext ctx, WriteOnlyKeyValueCommand command) throws Throwable {
      return handleNonTxWriteCommand(ctx, command);
   }

   @Override
   public Object visitWriteOnlyKeyCommand(InvocationContext ctx, WriteOnlyKeyCommand command) throws Throwable {
      return handleNonTxWriteCommand(ctx, command);
   }

   protected void remoteGetBeforeWrite(InvocationContext ctx, WriteCommand command, Object key) throws
         Throwable {
      CacheEntry entry = ctx.lookupEntry(key);
      if (!valueIsMissing(entry)) {
         return;
      }
      InternalCacheEntry remoteEntry = null;
      if (writeNeedsRemoteValue(ctx, command, key)) {
         // First try to fetch from remote owners
         if (!isValueAvailableLocally(dm.getReadConsistentHash(), key)) {
            if (trace) log.tracef("Doing a remote get for key %s", key);
            remoteEntry = retrieveFromRemoteSource(key, ctx, false, command, false);
            if (remoteEntry != null) {
               entryFactory.wrapExternalEntry(ctx, key, remoteEntry, EntryFactory.Wrap.STORE, false);
            }
         }
         if (remoteEntry == null) {
            // Then search for the entry in the local data container, in case we became an owner after
            // EntryWrappingInterceptor and the local node is now the only owner.
            // We can skip the local lookup if the operation doesn't need the previous values
            // TODO Check fails if the entry was passivated
            InternalCacheEntry localEntry = fetchValueLocallyIfAvailable(dm.getReadConsistentHash(), key);
            if (localEntry != null) {
               entryFactory.wrapExternalEntry(ctx, key, localEntry, EntryFactory.Wrap.STORE, false);
            }
         }
      }
   }

   protected boolean writeNeedsRemoteValue(InvocationContext ctx, WriteCommand command, Object key) {
      if (command.hasFlag(Flag.CACHE_MODE_LOCAL)) {
         return false;
      }
      if (ctx.isOriginLocal() && command.hasFlag(Flag.SKIP_REMOTE_LOOKUP)) {
         // Ignore SKIP_REMOTE_LOOKUP if we're already remote
         return false;
      }
      // Most of the time, the previous value only matters on the primary owner,
      // and we always have the existing value on the primary owner.
      // For DeltaAware writes we need the previous value on all the owners.
      // But if the originator is executing the command directly, it means it's the primary owner
      // and so it has the existing value already.
      return !ctx.isOriginLocal() && command.alwaysReadsExistingValues();
   }

   private Object threeStepsWrite(InvocationContext context, DataWriteCommand command) throws Throwable {
      if (context.isInTxScope()) {
         throw new IllegalArgumentException("Attempted execution of non-transactional write command in a transactional invocation context");
      }
      if (command.hasFlag(Flag.CACHE_MODE_LOCAL)) {
         //don't go through the triangle
         return invokeNextInterceptor(context, command);
      }
      final Address primaryOwner = cdl.getPrimaryOwner(command.getKey());
      List<Address> owners = cdl.getOwners(command.getKey());
      if (owners == null) {
         owners = dm.getConsistentHash().getMembers();
      }
      final boolean amIPrimaryOwner = primaryOwner.equals(rpcManager.getAddress());
      final boolean amIBackupOwner = owners.contains(rpcManager.getAddress());

      if (amIPrimaryOwner) {
         return primaryOwnerWrite(context, command, owners);
      } else if (amIBackupOwner) {
         return backupOwnerWrite(context, command, primaryOwner, owners);
      } else {
         return nonOwnerWrite(context, command, primaryOwner, owners);
      }
   }

   private Object nonOwnerWrite(InvocationContext context, DataWriteCommand command, Address primaryOwner, List<Address> owners) throws Exception {
      if (context.isOriginLocal()) {
         return localWriteInvocation(context, command, primaryOwner, owners);
      } else {
         throw new IllegalStateException("Remote write received in a non-owner");
      }
   }

   private Object primaryOwnerWrite(InvocationContext context, DataWriteCommand command, List<Address> owners) throws Throwable {
      //we are the primary owner. we need to execute the command, check if successful, send to backups and reply to originator is needed.
      Object commandResult = invokeNextInterceptor(context, command);
      if (!command.isSuccessful()) {
         return new DataWriteCommandResponse(commandResult);
      }
      RpcOptions options = rpcManager.getDefaultRpcOptions(true);
      CompletableFuture<?> backups = rpcManager.invokeRemotelyAsync(owners, command, options);
      DataWriteCommandResponse response = new DataWriteCommandResponse(commandResult, backups);
      if (context.isOriginLocal()) {
         CompletableFutures.await(backups, options.timeout(), options.timeUnit());
      }
      return response;
   }

   private Object backupOwnerWrite(InvocationContext context, DataWriteCommand command, Address primaryOwner, List<Address> owners) throws Throwable {
      if (context.isOriginLocal()) {
         //we forwards to the coordinator and wait for acks
         return localWriteInvocation(context, command, primaryOwner, owners);
      } else {
         final CommandInvocationId commandInvocationId = command.getCommandInvocationId();
         final Address origin = commandInvocationId.getAddress();
         final boolean local = origin.equals(rpcManager.getAddress());
         DataWriteCommandResponse response = new DataWriteCommandResponse(invokeNextInterceptor(context, command));
         response.getEntriesUpdated().thenRunAsync(() -> {
            if (local) {
               ack(commandInvocationId, origin);
            } else {
               if (!origin.equals(primaryOwner)) {
                  rpcManager.invokeRemotelyAsync(Collections.singleton(origin), cf.buildBackupAckCommand(commandInvocationId), rpcManager.getDefaultRpcOptions(false, DeliverOrder.NONE));
               }
            }
         });
         return response;
      }
   }

   private Object localWriteInvocation(InvocationContext context, DataWriteCommand command, Address primaryOwner, List<Address> owners) throws Exception {
      assert context.isOriginLocal();
      try {
         BackupOwnerAckCollector collector = new BackupOwnerAckCollector(owners);
         collector.ack(primaryOwner); //primary owner not needed!
         collectorMap.put(command.getCommandInvocationId(), collector);
         RpcOptions options = rpcManager.getDefaultRpcOptions(true);
         Map<Address, Response> responseMap = rpcManager.invokeRemotely(Collections.singleton(primaryOwner), command, rpcManager.getDefaultRpcOptions(true));
         DataWriteCommandResponse result = null;
         if (!responseMap.isEmpty()) {
            Response response = responseMap.get(primaryOwner);
            if (response instanceof ExceptionResponse) {
               throw ((ExceptionResponse) response).getException();
            } else if (response instanceof SuccessfulResponse) {
               result = (DataWriteCommandResponse) ((SuccessfulResponse) response).getResponseValue();
            } else
               throw new IllegalStateException("Unexpected response received!");
         }
         assert result != null;
         if (!result.isSuccessful()) {
            if (trace) {
               log.tracef("Unsuccessful conditional command. Skip waiting from backup owners. CommandId=%s", command.getCommandInvocationId());
            }
            return result.getReturnValue();
         }
         if (trace) {
            log.tracef("Waiting for acks for command %s. Missing are %s", command.getCommandInvocationId(), collector.confirmationNeeded);
         }
         collector.await(options.timeout(), options.timeUnit());
         return result.getReturnValue();
      } finally {
         collectorMap.remove(command.getCommandInvocationId());
      }
   }

   //TODO should it be a new class?
   private static class BackupOwnerAckCollector {
      private final Set<Address> confirmationNeeded;
      private final CountDownLatch barrier;

      private BackupOwnerAckCollector(Collection<Address> confirmationNeeded) {
         this.confirmationNeeded = ConcurrentHashMap.newKeySet();
         this.confirmationNeeded.addAll(confirmationNeeded);
         barrier = new CountDownLatch(1);
      }

      public void ack(Address from) {
         confirmationNeeded.remove(from);
         if (confirmationNeeded.isEmpty()) {
            if (trace) {
               log.trace("Last ack received!");
            }
            barrier.countDown();
         }
      }

      public void await(long timeout, TimeUnit timeUnit) throws InterruptedException {
         if (!barrier.await(timeout, timeUnit)) {
            throw log.timeoutWaitingForAcks(Util.prettyPrintTime(timeout, timeUnit), String.valueOf(confirmationNeeded));
         }
      }
   }

   public void ack(CommandInvocationId id, Address from) {
      if (trace) {
         log.tracef("Receive ack from %s for command %s.", from, id);
      }
      BackupOwnerAckCollector collector = collectorMap.get(id);
      if (collector != null) {
         collector.ack(from);
      }
   }
}
