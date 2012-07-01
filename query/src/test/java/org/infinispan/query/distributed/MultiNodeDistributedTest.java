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
package org.infinispan.query.distributed;

import java.util.ArrayList;
import java.util.List;

import javax.transaction.TransactionManager;

import junit.framework.Assert;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.hibernate.search.infinispan.impl.InfinispanDirectoryProvider;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.test.Person;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

/**
 * @author Sanne Grinovero
 */
@Test(groups = "functional", testName = "query.distributed.MultiNodeDistributedTest")
public class MultiNodeDistributedTest extends MultipleCacheManagersTest {

   private Cache<String, Person> cache1;
   private Cache<String, Person> cache2;

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder cacheCfg = getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, transactionsEnabled());
      cacheCfg
         .clustering()
            .stateTransfer()
               .fetchInMemoryState(true)
         .indexing()
            .enable()
            //only originator of each write takes care for index write
            .indexLocalOnly(true)
            //use our custom IndexManager to wire up the remoting delegation via custom commands
            .addProperty("hibernate.search.default.indexmanager", org.infinispan.query.indexmanager.InfinispanIndexManager.class.getName())
            //specify the managed index is to be shared across the nodes
            .addProperty("hibernate.search.default.directory_provider", "infinispan")
            .addProperty("hibernate.search.​default.​exclusive_index_use", "false")
            .addProperty("hibernate.search.default.reader.strategy", "not-shared")
            .addProperty("hibernate.search.lucene_version", "LUCENE_CURRENT");
      List<Cache<String, Person>> caches = createClusteredCaches(2, cacheCfg);
      cache1 = caches.get(0);
      cache2 = caches.get(1);
      completeAllCaches(InfinispanDirectoryProvider.DEFAULT_INDEXESDATA_CACHENAME);
      completeAllCaches(InfinispanDirectoryProvider.DEFAULT_INDEXESMETADATA_CACHENAME);
      completeAllCaches(InfinispanDirectoryProvider.DEFAULT_LOCKING_CACHENAME);
   }

   private void completeAllCaches(String cacheName) {
      EmbeddedCacheManager cm1 = cacheManagers.get(0);
      EmbeddedCacheManager cm2 = cacheManagers.get(0);
      List cacheList = new ArrayList(2);
      cacheList.add(cm1.getCache(cacheName));
      cacheList.add(cm2.getCache(cacheName));
      TestingUtil.blockUntilViewsReceived(10000, cacheList);
      TestingUtil.waitForRehashToComplete(cacheList);
   }

   protected boolean transactionsEnabled() {
      return true;
   }

   private void storeOn(Cache<String, Person> cache, String key, Person person) throws Exception {
      TransactionManager transactionManager = null;
      transactionManager = cache.getAdvancedCache().getTransactionManager();
      if (transactionsEnabled()) transactionManager.begin();
      cache.put(key, person);
      if (transactionsEnabled()) transactionManager.commit();
   }

   public void testSimple() throws Exception {
      assertIndexSize(0);
      //depending on test run, the index master selection might pick either cache
      storeOn(cache1, "k1", new Person("K. Firt", "Is not a character from the matrix", 1));
      assertIndexSize(1);
      storeOn(cache2, "k2", new Person("K. Seycond", "Is a pilot", 1));
      assertIndexSize(2);
      storeOn(cache1, "k3", new Person("K. Theerd", "Forgot the fundamental laws", 1));
      assertIndexSize(3);
   }

   private void assertIndexSize(int expectedIndexSize) {
      assertIndexSize(cache1, expectedIndexSize);
      assertIndexSize(cache2, expectedIndexSize);
   }

   private void assertIndexSize(Cache<String, Person> cache, int expectedIndexSize) {
      SearchManager searchManager = Search.getSearchManager(cache);
      CacheQuery query = searchManager.getQuery(new MatchAllDocsQuery(), Person.class);
      Assert.assertEquals(expectedIndexSize, query.list().size());
   }

}
