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
package org.infinispan.query.performance;

import java.text.NumberFormat;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.search.util.impl.Executors;
import org.infinispan.Cache;
import org.infinispan.commons.util.Util;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

/**
 * Tests indexing in a NeverEndingLoop, to monitor performance and profile.
 * 
 * @author Sanne Grinovero
 * @since 5.2
 */
public class NeverEndingLoop implements Runnable {

   private static final int CACHE_THREADS = 8;
   private static final int OBJECTS_PER_THREAD = 1000;
   private static final AtomicLong progress = new AtomicLong();
   private static final AtomicLong startTime = new AtomicLong();

   private final int threadId;
   private final SimpleIndexedObject[] objectsPool = new SimpleIndexedObject[OBJECTS_PER_THREAD];
   private final String[] keyPool = new String[OBJECTS_PER_THREAD];

   private final Cache<String,SimpleIndexedObject> cache;

   public NeverEndingLoop(Cache<String,SimpleIndexedObject> cache, int threadId) {
      this.cache = cache;
      this.threadId = threadId;
   }

   public static void main(String[] args) throws InterruptedException {
      Configuration cfg = new ConfigurationBuilder()
         .indexing()
               .enable()
                  .setProperty( "hibernate.search.default.indexmanager", "near-real-time")
                  .setProperty( "hibernate.search.default.directory_provider", "filesystem")
                  .setProperty( "hibernate.search.default.indexwriter.ram_buffer_size", "256")
                  .setProperty( "hibernate.search.lucene_version", "LUCENE_35")
                  .setProperty( "hibernate.search.​default.​indexwriter.merge_factor", "80")
                  .setProperty( "hibernate.search.​default.​indexwriter.merge_calibrate_by_deletes", "true")
                  .setProperty( "hibernate.search.​default.​indexwriter.use_compound_file", "true")
                  .setProperty( "hibernate.search.default.​worker.execution", "async")
                  .setProperty( "hibernate.search.default.sharding_strategy.nbr_of_shards", "8")
               .build();
      DefaultCacheManager defaultCacheManager = new DefaultCacheManager(cfg);
      System.out.println("Cache initialized");
      try {
         ThreadPoolExecutor executor = Executors.newFixedThreadPool(CACHE_THREADS, "Cache Users");
         Cache<String,SimpleIndexedObject> cache = defaultCacheManager.getCache();
         for (int i=0; i<CACHE_THREADS; i++) {
            executor.execute( new NeverEndingLoop(cache, i) );
         }
         executor.shutdown();
         executor.awaitTermination(2, TimeUnit.DAYS);
      }
      finally {
         defaultCacheManager.stop();
         System.out.println("Cache stopped");
      }
   }

   @Override
   public void run() {
      prefillObjectsPool();
      startTime.compareAndSet(0, System.nanoTime());
      while (true) {
         //Insert
         for (int i = 0; i < OBJECTS_PER_THREAD; i++) {
            doInsert(i);
         }
         //Move -todo
         //Delete - todo
         cycleDone();
      }
   }

   private static void cycleDone() {
      long operations = progress.addAndGet(OBJECTS_PER_THREAD);
      if (operations % 1 == 0) {
         long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime.get(), TimeUnit.NANOSECONDS);
         long durationSeconds = TimeUnit.SECONDS.convert(durationMillis, TimeUnit.MILLISECONDS);
         if ( durationSeconds > 0 ) {
            NumberFormat NF = NumberFormat.getInstance();
            System.out.printf(" done %s " + "operations in %s\n\taverage: %s operations/second\n", NF.format(operations), Util.prettyPrintTime(durationMillis, TimeUnit.MILLISECONDS),
               NF.format(operations / durationSeconds));
         }
      }
   }

   private void doInsert(int i) {
      cache.put(keyPool[i], objectsPool[i]);
   }

   private void prefillObjectsPool() {
      for (int i=0; i<OBJECTS_PER_THREAD; i++) {
         String shortString = "some " + threadId + " other term " + i;
         String longerString = shortString + " " + shortString;
         objectsPool[i] = new SimpleIndexedObject(longerString, shortString);
         keyPool[i] = "K" + threadId + "E" + i;
      }
   }

}
