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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;

import org.hibernate.search.backend.IndexingMonitor;
import org.hibernate.search.backend.LuceneWork;
import org.hibernate.search.backend.spi.BackendQueueProcessor;
import org.hibernate.search.indexes.impl.DirectoryBasedIndexManager;
import org.hibernate.search.infinispan.CacheManagerServiceProvider;
import org.hibernate.search.spi.WorkerBuildContext;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.backend.ComponentRegistryServiceProvider;
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
   private ComponentRegistry componentsRegistry;
   private ConsistentHash hashService;

   @Override
   public void initialize(Properties props, WorkerBuildContext context, DirectoryBasedIndexManager indexManager) {
      this.context = context;
      this.cacheManager = context.requestService(CacheManagerServiceProvider.class);
      this.componentsRegistry = context.requestService(ComponentRegistryServiceProvider.class);
      this.indexName = indexManager.getIndexName();
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
      System.out.println(workList);
   }

   @Override
   public void applyStreamWork(LuceneWork singleOperation, IndexingMonitor monitor) {
      System.out.println(singleOperation);
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
         final Address primaryLocation;
         if (hashService == null) { // REPL
            //TODO think about splitting this implementation, or use this same approach for both configurations?
            List<Address> members = transport.getMembers();
            int elementIndex = (indexName.hashCode() % members.size());
            primaryLocation = members.get(elementIndex);
         }
         else { //DIST
            primaryLocation = hashService.primaryLocation(indexName);
         }
         Address localAddress = transport.getAddress();
         return localAddress.equals(primaryLocation);
      }
   }

}
