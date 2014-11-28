package org.infinispan.commands.read;

import static org.infinispan.commons.util.Util.toStr;

import java.util.Set;

import org.infinispan.commands.Visitor;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;

public final class ContainsKeyValueCommand extends AbstractDataCommand implements RemoteFetchingCommand {

   public static final byte COMMAND_ID = 44;

   private InternalCacheEntry remotelyFetchedValue;

   public ContainsKeyValueCommand(Object key, Set<Flag> flags) {
      this.key = key;
      this.flags = flags;
   }

   public ContainsKeyValueCommand() {
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitContainsKeyValueCommand(ctx, this);
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      CacheEntry entry = ctx.lookupEntry(key);
      if (entry == null) {
        //unknown: don't return FALSE or it might prevent loading entry from elsewhere
         return null;
      }
      else if (entry.isNull() || entry.isRemoved()) {
         return Boolean.FALSE;
      }
      else {
         return Boolean.TRUE;
      }
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public void setParameters(int commandId, Object[] parameters) {
      if (commandId != COMMAND_ID) throw new IllegalStateException("Invalid method id");
      key = parameters[0];
      flags = (Set<Flag>) parameters[1];
   }

   @Override
   public Object[] getParameters() {
      return new Object[]{key, Flag.copyWithoutRemotableFlags(flags)};
   }

   /**
    * @see #getRemotelyFetchedValue()
    */
   public void setRemotelyFetchedValue(InternalCacheEntry remotelyFetchedValue) {
      this.remotelyFetchedValue = remotelyFetchedValue;
   }

   /**
    * If the cache needs to go remotely in order to obtain the value associated to this key, then the remote value
    * is stored in this field.
    * TODO: this method should be able to removed with the refactoring from ISPN-2177
    */
   public InternalCacheEntry getRemotelyFetchedValue() {
      return remotelyFetchedValue;
   }

   public String toString() {
      return new StringBuilder()
            .append("ContainsKeyValueCommand {key=")
            .append(toStr(key))
            .append(", flags=").append(flags)
            .append("}")
            .toString();
   }

}
