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
package org.infinispan.stress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.AbstractCacheTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Verifies the atomic semantic of Infinispan's implementations of
 * java.util.concurrent.ConcurrentMap<K, V>.putIfAbsent(K key, V value); which is an interesting
 * concurrent locking case.
 * 
 * @since 5.2
 * @see java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)
 * @author Sanne Grinovero <sanne@infinispan.org> (C) 2012 Red Hat Inc.
 */
@Test(groups = "stress", testName = "stress.replaceOperationtressTest", enabled = true,
      description = "Since this test is slow to run, it should be disabled by default and run by hand as necessary.")
public class ReplaceOperationStressTest {

   private static final int NODES_NUM = 5;
   private static final int MOVES = 50000;
   private static final int THREADS = 3;
   private static final String SHARED_KEY = "thisIsTheKeyForConcurrentAccess";

   private static final String[] validMoves = generateValidMoves();
   private static final String[] invalidMoves = generateInvalidMoves();

   private static final AtomicBoolean failed = new AtomicBoolean(false);
   private static volatile String failureMessage = "";

   /**
    * Testing replace(Object, Object, Object) behaviour on clustered DIST_SYNC caches
    */
   public void testonInfinispanDIST_SYNC_NonTX() throws Exception {
      testEntry(CacheMode.DIST_SYNC, false);
   }

   /**
    * Testing replace(Object, Object, Object) behaviour on clustered DIST_SYNC caches with transactions
    */
   public void testonInfinispanDIST_SYNC_TX() throws Exception {
      testEntry(CacheMode.DIST_SYNC, false);
   }

   /**
    * Testing replace(Object, Object, Object) behaviour on clustered DIST_SYNC caches
    */
   public void testonInfinispanLOCAL_NonTX() throws Exception {
      testEntry(CacheMode.LOCAL, false);
   }

   /**
    * Testing replace(Object, Object, Object) behaviour on clustered REPL_SYNC caches
    */
   public void testonInfinispanREPL_NonTX() throws Exception {
      testEntry(CacheMode.REPL_SYNC, false);
   }

   /**
    * Testing replace(Object, Object, Object) behaviour on clustered REPL_SYNC caches with transactions
    */
   public void testonInfinispanREPL_TX() throws Exception {
      testEntry(CacheMode.REPL_SYNC, true);
   }

   private void testEntry(final CacheMode mode, final boolean transactional) {
      final ConfigurationBuilder builder;
      if (mode.isClustered()) {
         builder = AbstractCacheTest.getDefaultClusteredCacheConfig(mode, transactional);
      }
      else {
         builder = TestCacheManagerFactory.getDefaultCacheConfiguration(transactional);
      }
      testOnConfiguration(builder, NODES_NUM, mode);
   }

   private void testOnConfiguration(final ConfigurationBuilder builder, final int nodesNum, final CacheMode mode) {
      final List<EmbeddedCacheManager> cacheManagers;
      final List<Cache> caches;
      if (mode.isClustered()) {
         cacheManagers = new ArrayList<EmbeddedCacheManager>(nodesNum);
         caches = new ArrayList<Cache>(nodesNum);
         for (int i = 0; i < nodesNum; i++) {
            EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(builder);
            cacheManagers.add(cacheManager);
            caches.add(cacheManager.getCache());
         }
         TestingUtil.blockUntilViewsReceived(10000, caches);
         if (mode.isDistributed()) {
            TestingUtil.waitForRehashToComplete(caches);
         }
      }
      else {
         EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createCacheManager(builder);
         cacheManagers = Collections.singletonList(cacheManager);
         Cache cache = cacheManager.getCache();
         caches = Collections.singletonList(cache);
      }
      try {
         testOnCaches(caches);
      }
      finally {
         TestingUtil.killCaches(caches);
         TestingUtil.killCacheManagers(cacheManagers);
      }
   }

   private void testOnCaches(List<Cache> caches) {
      ExecutorService exec = Executors.newFixedThreadPool(THREADS*2);
      for (int i = 0; i < THREADS; i++) {
         Runnable validMover = new ValidMover(caches, validMoves);
         exec.execute(validMover);
         Runnable invalidMover = new InvalidMover(caches, invalidMoves);
         exec.execute(invalidMover);
      }
      exec.shutdown();
      assert !failed.get() : failureMessage;
   }

   private static String[] generateInvalidMoves() {
      int totalSize = MOVES;
      String[] invalidMoves = new String[totalSize];
      for ( int i=0; i<totalSize; i++) {
         invalidMoves[i] = "i_"+i;
      }
      System.out.println("Invalid moves ready");
      return invalidMoves;
   }

   private static String[] generateValidMoves() {
      String[] validMoves = new String[MOVES];
      for ( int i=0; i<MOVES; i++) {
         validMoves[i] = "v_"+i;
      }
      System.out.println("Valid moves ready");
      return validMoves;
   }

   static final class ValidMover implements Runnable {

      private final List<Cache> caches;
      private final String[] validMoves;

      public ValidMover(List<Cache> caches, String[] validMoves) {
         this.caches = caches;
         this.validMoves = validMoves;
      }

      @Override
      public void run() {
         int cachePickIndex = 0;
         caches.get(0).put(SHARED_KEY, validMoves[0]);
         System.out.println("Valid mover starting..");
         for ( int moveToIndex = 0; moveToIndex<validMoves.length; ) {
            cachePickIndex = ++cachePickIndex % caches.size();
            Cache cache = caches.get(cachePickIndex);
            boolean replace = cache.replace(SHARED_KEY, validMoves[cachePickIndex], validMoves[++cachePickIndex]);
            if (replace == false) {
               //Test failed!
               failed.set(true);
               failureMessage = "Return value wasn't true after a valid move!";
               return;
            }
            for (Cache c : caches) {
               final Object currentStored = c.get(SHARED_KEY);
               if (!currentStored.equals(validMoves[cachePickIndex])) {
                  failed.set(true);
                  failureMessage = "different value than stored was retrieved!";
                  return;
               }
            }
            if (moveToIndex % (MOVES / 100) == 0) {
               System.out.print( "1%..");
            }
         }
      }
   }

   static final class InvalidMover implements Runnable {

      private final List<Cache> caches;
      private final String[] invalidMoves;

      public InvalidMover(List<Cache> caches, String[] invalidMoves) {
         this.caches = caches;
         this.invalidMoves = invalidMoves;
      }

      @Override
      public void run() {
         int cachePickIndex = 0;
         caches.get(0).put(SHARED_KEY, invalidMoves[0]);
         System.out.println("Valid mover starting..");
         for ( int moveToIndex = 0; moveToIndex<invalidMoves.length; ) {
            cachePickIndex = ++cachePickIndex % caches.size();
            Cache cache = caches.get(cachePickIndex);
            boolean replace = cache.replace(SHARED_KEY, invalidMoves[cachePickIndex], invalidMoves[++cachePickIndex]);
            if (replace == true) {
               //Test failed!
               failed.set(true);
               failureMessage = "Return value was false after a valid move!";
               return;
            }
         }
      }
   }

}
