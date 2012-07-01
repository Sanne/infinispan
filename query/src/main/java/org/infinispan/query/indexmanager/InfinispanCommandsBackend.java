/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.query.indexmanager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;

import org.hibernate.search.backend.IndexingMonitor;
import org.hibernate.search.backend.LuceneWork;
import org.hibernate.search.backend.spi.BackendQueueProcessor;
import org.hibernate.search.indexes.impl.DirectoryBasedIndexManager;
import org.hibernate.search.indexes.serialization.spi.LuceneWorkSerializer;
import org.hibernate.search.infinispan.CacheManagerServiceProvider;
import org.hibernate.search.spi.WorkerBuildContext;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.backend.ComponentRegistryServiceProvider;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
* @author Sanne Grinovero
*/
public class InfinispanCommandsBackend implements BackendQueueProcessor {

   private static final Log log = LogFactory.getLog(InfinispanCommandsBackend.class);

   private EmbeddedCacheManager cacheManager;
   private WorkerBuildContext context;
   private String indexName;
   private ConsistentHash hashService;
   private RpcManager rpcManager;
   private String cacheName;
   private DirectoryBasedIndexManager indexManager;

   @Override
   public void initialize(Properties props, WorkerBuildContext context, DirectoryBasedIndexManager indexManager) {
      this.context = context;
      this.indexManager = indexManager;
      this.cacheManager = context.requestService(CacheManagerServiceProvider.class);
      final ComponentRegistry componentsRegistry = context.requestService(ComponentRegistryServiceProvider.class);
      this.indexName = indexManager.getIndexName();
      this.rpcManager = componentsRegistry.getComponent(RpcManager.class);
      this.cacheName = componentsRegistry.getCacheName();
      DistributionManager distributionManager = componentsRegistry.getComponent(DistributionManager.class);
      if (distributionManager != null) {
         hashService = distributionManager.getConsistentHash();
      }
   }

   @Override
   public void close() {
      context.releaseService(CacheManagerServiceProvider.class);
      context.releaseService(ComponentRegistryServiceProvider.class);
      context = null;
      cacheManager = null;
   }

   @Override
   public void applyWork(List<LuceneWork> workList, IndexingMonitor monitor) {
      IndexUpdateCommand command = new IndexUpdateCommand(cacheName);
      //Use Searche's custom Avro based serializer as it includes support for back/future compatibility
      byte[] serializedModel = indexManager.getSerializer().toSerializedModel(workList);
      command.setSerializedWorkList(serializedModel);
      command.setIndexName(this.indexName);
      sendCommand(command);
   }

   private void sendCommand(ReplicableCommand command) {
      Collection<Address> recipients = Collections.singleton(getPrimaryNodeAddress());
      rpcManager.invokeRemotely(recipients, command, true);
   }

   @Override
   public void applyStreamWork(LuceneWork singleOperation, IndexingMonitor monitor) {
      applyWork(Collections.singletonList(singleOperation), monitor);
   }

   @Override
   public Lock getExclusiveWriteLock() {
      throw new UnsupportedOperationException("Not Implementable: nonsense on a distributed index.");
   }

   @Override
   public void indexMappingChanged() {
      //FIXME implement me
   }

   public boolean isMasterLocal() {
      Transport transport = cacheManager.getTransport();
      if (transport == null) {
         return true;
      }
      else {
         final Address primaryLocation = getPrimaryNodeAddress();
         Address localAddress = transport.getAddress();
         return localAddress.equals(primaryLocation);
      }
   }

   /**
    * Returns the primary node for this index, or null
    * for non clustered configurations.
    */
   private Address getPrimaryNodeAddress() {
      Transport transport = cacheManager.getTransport();
      if (transport == null) {
         return null;
      }
      if (hashService == null) { // REPL
         //TODO think about splitting this implementation, or use this same approach for both configurations?
         List<Address> members = transport.getMembers();
         int elementIndex = (indexName.hashCode() % members.size());
         return members.get(elementIndex);
      }
      else { //DIST
         return hashService.primaryLocation(indexName);
      }
   }

}
