/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other
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
package org.infinispan.query.performance;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.test.Person;
import org.infinispan.test.fwk.TestCacheManagerFactory;

/**
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2012 Red Hat Inc.
 */
public class CrazyWriter {

   private static final int LOOP_OUTPUT = 60000;

   private final EmbeddedCacheManager cacheManager;
   private final Cache<Object, Object> cache;

   public CrazyWriter(final EmbeddedCacheManager cacheManager) {
      this.cacheManager = cacheManager;
      this.cache = cacheManager.getCache();
   }

   private void loop() {
      System.out.println("entering loop");
      long previousTimestamp = 0;
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
         cache.put(
               "K-" + String.valueOf(i),
               new Person("V-" + String.valueOf(i), "HUMM" + String.valueOf(i * 3), i));
         if (i % LOOP_OUTPUT == 0) {
            long current = System.nanoTime();
            long interval = TimeUnit.MILLISECONDS.convert(current - previousTimestamp, TimeUnit.NANOSECONDS);
            if (previousTimestamp != 0) {
               System.out.println("inserted " + i + "! Inserted " + LOOP_OUTPUT
                     + " elements in " + interval + " milliseconds");
            }
            previousTimestamp = current;
         }
      }
   }

   public static void main(String[] args) throws Exception {
      EmbeddedCacheManager cacheManager = createCacheManager();
      try {
         CrazyWriter cw = new CrazyWriter(cacheManager);
         cw.loop();
      } finally {
         cacheManager.stop();
      }
   }

   static EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder cfg = TestCacheManagerFactory
            .getDefaultCacheConfiguration(false);
      cfg.eviction().strategy(EvictionStrategy.NONE);
      cfg.expiration().disableReaper();
      cfg.indexing().enable().indexLocalOnly(false)
            .addProperty("default.directory_provider", "filesystem")
            .addProperty("lucene_version", "LUCENE_CURRENT")
            .addProperty("default.indexwriter.merge_factor", "30")
            .addProperty("default.sharding_strategy.nbr_of_shards", "12")
            .addProperty("default.​indexwriter.ram_buffer_size", "400")
            .addProperty("default.indexmanager", "near-real-time")
            .addProperty("​default.​indexwriter.use_compound_file", "false");
      // .addProperty("​default.​indexwriter.exclusive_index_use", "true");
      return TestCacheManagerFactory.createCacheManager(cfg);
   }

}
