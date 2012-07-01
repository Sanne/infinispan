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

import static org.infinispan.query.helper.TestQueryHelperFactory.createCacheQuery;

import java.util.List;

import javax.transaction.TransactionManager;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.test.Person;
import org.infinispan.test.MultipleCacheManagersTest;
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
      cacheCfg.indexing()
         .enable()
         .indexLocalOnly(true)
         .addProperty("hibernate.search.default.indexmanager", org.infinispan.query.indexmanager.InfinispanIndexManager.class.getName())
         .addProperty("hibernate.search.lucene_version", "LUCENE_CURRENT");
      List<Cache<String, Person>> caches = createClusteredCaches(2, cacheCfg);
      cache1 = caches.get(0);
      cache2 = caches.get(1);
   }

   protected boolean transactionsEnabled() {
      return false;
   }

   private void prepareTestData() throws Exception {
      storeOn(cache1, "k1", new Person("K. Firt", "Is not a characted from the matrix", 1));
      storeOn(cache2, "k2", new Person("K. Seycond", "Is a pilot", 1));
      storeOn(cache2, "k3", new Person("K. Theerd", "Forgot the fundamental laws", 1));
   }

   private void storeOn(Cache<String, Person> cache, String key, Person person) throws Exception {
      TransactionManager transactionManager = null;
      transactionManager = cache.getAdvancedCache().getTransactionManager();
      if (transactionsEnabled()) transactionManager.begin();
      cache.put(key, person);
      if (transactionsEnabled()) transactionManager.commit();
   }

   public void testSimple() throws Exception {
      prepareTestData();
      CacheQuery cacheQuery = createCacheQuery(cache2, "blurb", "pilot");

      List<Object> found = cacheQuery.list();

      assert found.size() == 1;
      assert ((Person)found.get(0)).getName().equals("K. Seycond");
   }

}
