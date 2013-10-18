package org.infinispan.lucene.profiling;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;

public final class TestNode {

   private final EmbeddedCacheManager cacheManager;
   private final Cache<Object, Object> cache;

   public TestNode(ConfigurationBuilder configurationBuilder, int networkDelayNanos) throws Exception {
      cacheManager = TestCacheManagerFactory.createClusteredCacheManager(configurationBuilder);
      cacheManager.start();
      cache = cacheManager.getCache();
      //Requires latest JGroups
//      TestingUtil.setDelayForCache(cache, 0, 0, networkDelayNanos, networkDelayNanos);//Ugly: exception during constructor.
   }

   public Cache getCache() {
      return cache;
   }

   public void kill() {
      TestingUtil.killCaches(cache);
      TestingUtil.killCacheManagers(cacheManager);
   }

}
