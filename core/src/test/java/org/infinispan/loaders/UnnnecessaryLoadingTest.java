/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.loaders;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.config.ConfigurationBeanVisitor;
import org.infinispan.configuration.Builder;
import org.infinispan.configuration.BuiltBy;
import org.infinispan.configuration.cache.AbstractLoaderConfiguration;
import org.infinispan.configuration.cache.AbstractLoaderConfigurationBuilder;
import org.infinispan.configuration.cache.CacheLoaderConfiguration;
import org.infinispan.configuration.cache.CacheLoaderConfigurationBuilder;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.LoadersConfigurationBuilder;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.loaders.decorators.ChainingCacheStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.test.fwk.TestInternalCacheEntryFactory;
import org.infinispan.util.TypedProperties;
import org.testng.annotations.Test;

/**
 * A test to ensure stuff from a cache store is not loaded unnecessarily if it already exists in memory, or if the
 * Flag.SKIP_CACHE_STORE is applied.
 *
 * @author Manik Surtani
 * @author Sanne Grinovero
 * @version 4.1
 */
@Test(testName = "loaders.UnnnecessaryLoadingTest", groups = "functional", sequential = true)
@CleanupAfterMethod
public class UnnnecessaryLoadingTest extends SingleCacheManagerTest {
   CacheStore store;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder builder = TestCacheManagerFactory.getDefaultCacheConfiguration(true);
      builder
         .invocationBatching()
            .enable()
         .loaders()
            .addLoader(CountingCacheLoaderConfigurationBuilder.class)
         ;
      return TestCacheManagerFactory.createCacheManager(builder);
   }

   @Override
   protected void setup() throws Exception {
      super.setup();
      store = TestingUtil.extractComponent(cache, CacheLoaderManager.class).getCacheStore();
   }

   public void testRepeatedLoads() throws CacheLoaderException {
      CountingCacheStore countingCS = getCountingCacheStore();
      store.store(TestInternalCacheEntryFactory.create("k1", "v1"));

      assert countingCS.numLoads == 0;
      assert countingCS.numContains == 0;

      assert "v1".equals(cache.get("k1"));

      assert countingCS.numLoads == 1 : "Expected 1, was " + countingCS.numLoads;
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;

      assert "v1".equals(cache.get("k1"));

      assert countingCS.numLoads == 1 : "Expected 1, was " + countingCS.numLoads;
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;
   }

   public void testSkipCacheFlagUsage() throws CacheLoaderException {
      CountingCacheStore countingCS = getCountingCacheStore();

      store.store(TestInternalCacheEntryFactory.create("k1", "v1"));

      assert countingCS.numLoads == 0;
      assert countingCS.numContains == 0;
      //load using SKIP_CACHE_STORE should not find the object in the store
      assert cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE).get("k1") == null;
      assert countingCS.numLoads == 0;
      assert countingCS.numContains == 0;

      // counter-verify that the object was actually in the store:
      assert "v1".equals(cache.get("k1"));
      assert countingCS.numLoads == 1 : "Expected 1, was " + countingCS.numLoads;
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;

      // now check that put won't return the stored value
      store.store(TestInternalCacheEntryFactory.create("k2", "v2"));
      Object putReturn = cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE).put("k2", "v2-second");
      assert putReturn == null;
      assert countingCS.numLoads == 1 : "Expected 1, was " + countingCS.numLoads;
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;
      // but it inserted it in the cache:
      assert "v2-second".equals(cache.get("k2"));
      // perform the put in the cache & store, using same value:
      putReturn = cache.put("k2", "v2-second");
      //returned value from the cache:
      assert "v2-second".equals(putReturn);
      //and verify that the put operation updated the store too:
      assert "v2-second".equals(store.load("k2").getValue());
      assert countingCS.numLoads == 2 : "Expected 2, was " + countingCS.numLoads;

      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;
      cache.containsKey("k1");
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;
      assert false == cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE).containsKey("k3");
      assert countingCS.numContains == 0 : "Expected 0, was " + countingCS.numContains;
      assert countingCS.numLoads == 2 : "Expected 2, was " + countingCS.numLoads;

      //now with batching:
      boolean batchStarted = cache.getAdvancedCache().startBatch();
      assert batchStarted;
      assert null == cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_STORE).get("k1batch");
      assert countingCS.numLoads == 2 : "Expected 2, was " + countingCS.numLoads;
      assert null == cache.getAdvancedCache().get("k2batch");
      assert countingCS.numLoads == 3 : "Expected 3, was " + countingCS.numLoads;
      cache.endBatch(true);
   }

   private CountingCacheStore getCountingCacheStore() {
      CacheLoaderManager clm = TestingUtil.extractComponent(cache, CacheLoaderManager.class);
      ChainingCacheStore ccs = (ChainingCacheStore) clm.getCacheLoader();
      CountingCacheStore countingCS = (CountingCacheStore) ccs.getStores().keySet().iterator().next();
      reset(cache, countingCS);
      return countingCS;
   }

   public void testSkipCacheLoadFlagUsage() throws CacheLoaderException {
      CountingCacheStore countingCS = getCountingCacheStore();

      store.store(TestInternalCacheEntryFactory.create("home", "Vermezzo"));
      store.store(TestInternalCacheEntryFactory.create("home-second", "Newcastle Upon Tyne"));

      assert countingCS.numLoads == 0;
      //load using SKIP_CACHE_LOAD should not find the object in the store
      assert cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD).get("home") == null;
      assert countingCS.numLoads == 0;

      assert cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD).put("home", "Newcastle") == null;
      assert countingCS.numLoads == 0;

      final Object put = cache.getAdvancedCache().put("home-second", "Newcastle Upon Tyne, second");
      assertEquals(put, "Newcastle Upon Tyne");
      assert countingCS.numLoads == 1;
   }

   private void reset(Cache cache, CountingCacheStore countingCS) {
      cache.clear();
      countingCS.numLoads = 0;
      countingCS.numContains = 0;
   }

   public static class CountingCacheStore extends AbstractCacheLoader {
      public int numLoads, numContains;

      @Override
      public InternalCacheEntry load(Object key) throws CacheLoaderException {
         incrementLoads();
         return null;
      }

      @Override
      public Set<InternalCacheEntry> loadAll() throws CacheLoaderException {
         return Collections.emptySet();
      }

      @Override
      public Set<InternalCacheEntry> load(int numEntries) throws CacheLoaderException {
         return Collections.emptySet();
      }

      @Override
      public Set<Object> loadAllKeys(Set<Object> keysToExclude) throws CacheLoaderException {
         return Collections.emptySet();
      }

      @Override
      public boolean containsKey(Object key) throws CacheLoaderException {
         numContains++;
         return false;
      }

      private void incrementLoads() {
         numLoads++;
      }

      @Override
      public void start() throws CacheLoaderException {
      }

      @Override
      public void stop() throws CacheLoaderException {
      }

      @Override
      public Class<? extends CacheLoaderConfig> getConfigurationClass() {
         return CountingCacheStoreConfig.class;
      }
   }

   @BuiltBy(CountingCacheLoaderConfigurationBuilder.class)
   public static class CountingCacheStoreConfig extends AbstractLoaderConfiguration implements CacheLoaderConfiguration, CacheLoaderConfig {
      public CountingCacheStoreConfig() {
         super(new TypedProperties());
      }

      @Override
      public void accept(ConfigurationBeanVisitor visitor) {
      }

      @Override
      public String getCacheLoaderClassName() {
         return "";
      }

      @Override
      public void setCacheLoaderClassName(String s) {
      }

      @Override
      public ClassLoader getClassLoader() {
         return CountingCacheStoreConfig.class.getClassLoader();
      }

      @Override
      public CacheLoaderConfig clone() {
         return this;
      }
   }

   public static class CountingCacheLoaderConfigurationBuilder
      extends AbstractLoaderConfigurationBuilder<CountingCacheStoreConfig,CountingCacheLoaderConfigurationBuilder>
      implements CacheLoaderConfigurationBuilder<CountingCacheStoreConfig,CountingCacheLoaderConfigurationBuilder> {

      public CountingCacheLoaderConfigurationBuilder(LoadersConfigurationBuilder builder) {
         super(builder);
      }

      @Override
      public void validate() {
      }

      @Override
      public CountingCacheStoreConfig create() {
         return new CountingCacheStoreConfig();
      }

      @Override
      public Builder<?> read(CountingCacheStoreConfig template) {
         return null;
      }

      @Override
      public CountingCacheLoaderConfigurationBuilder self() {
         return this;
      }
   }
}
