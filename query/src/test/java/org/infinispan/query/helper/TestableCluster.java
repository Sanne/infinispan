package org.infinispan.query.helper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.transaction.TransactionManager;

import net.jcip.annotations.GuardedBy;

import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.indexmanager.InfinispanIndexManager;
import org.infinispan.query.logging.Log;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.jboss.logging.Logger;
import org.junit.Assert;

/**
 * Helper to test scenarios in a dynamic cluster.
 *
 * Allows for indexing operations to happen concurrently with cluster
 * topology updates, but only one node can be added/removed at a time.
 *
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2014 Red Hat Inc.
 * @since 7.0
 */
public final class TestableCluster<K, V> {

   private static final Log log = Logger.getMessageLogger(Log.class, "CLUSTER");

   private static final boolean FORCE_WAIT_FOR_CLUSTER = true;

   private final Random rand = new Random();

   @GuardedBy("itself")
   private final Set<Node> allNodes = new HashSet<>();

   private final BlockingQueue<Node> availableNodes = new LinkedTransferQueue<>();

   private final AtomicBoolean requestMasterNode = new AtomicBoolean(false);
   private final BlockingQueue<Node> exchangerForMaster = new LinkedTransferQueue<>();

   private final String configurationResourceName;
   private final boolean transactionsEnabled;

   private final String indexName;

   public TestableCluster(String configurationResourceName, boolean transactionsEnabled, String indexName) {
      this.configurationResourceName = configurationResourceName;
      this.transactionsEnabled = transactionsEnabled;
      this.indexName = indexName;
   }

   public void startNewNode(boolean waitForClusterFormation) {
      synchronized (allNodes) {
         final EmbeddedCacheManager cacheManager;
         try {
            cacheManager = TestCacheManagerFactory.fromXml(configurationResourceName);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
         Cache<K, V> cache = cacheManager.getCache();
         Node node = new Node(cacheManager, cache.getAdvancedCache());
         allNodes.add(node);
         if (waitForClusterFormation || FORCE_WAIT_FOR_CLUSTER) {
            waitForRehashToComplete(allNodes);
         }
         availableNodes.add(node);
         log.info("Scaled UP, " + allNodes.size() + " nodes in the cluster. Last joiner is: " + node.cacheManager.getAddress());
      }
   }

   public void killAll() {
      log.info("SHUTDOWN - killing all nodes aggressively");
      synchronized (allNodes) {
         for (Node n : allNodes) {
            n.kill();
         }
         allNodes.clear();
      }
   }

   public void killRandomNode() {
      final Node node = takeAnyNode();
      killNode(node);
   }

   public void killMasterNodeForIndex() {
      //Needs to lock around the selection, or the master role might change
      synchronized (allNodes) {
         final Node masterNode = takeMasterNode();
         killNode(masterNode);
      }
   }

   private void killNode(final Node node) {
      synchronized (allNodes) {
         String description = node.kill();
         Assert.assertTrue(allNodes.remove(node));
         waitForRehashToComplete(allNodes);
         log.info("Scaled DOWN: " + allNodes.size() + " nodes in the cluster. Last node killed is: " + description);
      }
   }

   /**
    * Returns a node to the pool
    */
   public void returnNode(Node node) {
      //If this is the master node and another thread
      //is looking for it, hand it off directly:
      if (node.isMasterNodeForIndex(indexName)) {
         if (this.requestMasterNode.compareAndSet(true, false)) {
            this.exchangerForMaster.add(node);
            return;
         }
      }
      availableNodes.add(node);
   }

   public void storeOnAnyNode(K key, V value) throws Exception {
      Node node = takeAnyNode();
      try {
         storeOn(node, key, value);
      }
      finally {
         returnNode(node);
      }
   }

   public synchronized void resizeRandomInRange(int min, int max) {
      Assert.assertTrue(min < max);
      synchronized (allNodes) {
         final int currentSize = allNodes.size();
         if (currentSize <= min) {
            startNewNode(false);
         }
         if (currentSize >= max) {
            killRandomNode();
         }
         final int oneToTen = rand.nextInt(10) + 1;
         //make it more likely to scale up:
         if (oneToTen > 8) {
            killRandomNode();
         }
         else {
            startNewNode(false);
         }
      }
   }

   private void storeOn(final Node node, K key, V value) throws Exception {
      log.trace("Storing something on node: " + node);
      Assert.assertNotNull(node);
      Assert.assertNotNull(node.advancedCache);
      Assert.assertNotNull(key);
      Assert.assertNotNull(value);
      TransactionManager transactionManager = null;
      if (transactionsEnabled) {
         transactionManager = node.advancedCache.getTransactionManager();
         transactionManager.begin();
      }
      node.advancedCache.put(key, value);
      if (transactionsEnabled) {
         transactionManager.commit();
      }
      StaticTestingErrorHandler.assertAllGood(node.advancedCache);
   }

   private static <K, V> void waitForRehashToComplete(Set<Node> nodes) {
      if (nodes.size()<=1) {
         return;
      }
      List<Cache> caches = new ArrayList<>(nodes.size());
      for (Node n : nodes) {
         caches.add(n.advancedCache);
      }
      TestingUtil.waitForRehashToComplete(caches);
   }

   public Node takeAnyNode() {
      Node polledNode = availableNodes.poll();
      if (polledNode != null) {
         return polledNode;
      }
      else {
         //Might be useful to log this when figuring out why there is no quick progress:
         System.out.println("Scarce resources! Thread blocked in wait for available node");
         try {
            return availableNodes.take();
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for an available node", e);
         }
      }
   }

   @GuardedBy("allNodes")//Role of the node might change during invocation if you don't hold this lock
   private Node takeMasterNode() {
      //Set this flag so that other threads will start helping us
      requestMasterNode.set(true);
      try {
         Node masterNode = null;
         int loop = 0;
         while (masterNode == null) {
            masterNode = exchangerForMaster.poll(200, TimeUnit.MILLISECONDS);
            if (masterNode == null) {
               //Makes sure the exchanger is refilled from the pool
               //when this is driven by a single threaded test:
               returnNode(takeAnyNode());
            }
            loop++;
            if (loop > 1000) {
               throw new RuntimeException("Timed out while waiting for the Master node to be available");
            }
         }
         return masterNode;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new RuntimeException("Interrupted while waiting for the Master node to be available", e);
      }
   }

   public final static class Node {

      private final EmbeddedCacheManager cacheManager;
      private final AdvancedCache advancedCache;
      private volatile boolean alive = true;

      private Node(EmbeddedCacheManager cacheManager, AdvancedCache advancedCache) {
         this.cacheManager = cacheManager;
         this.advancedCache = advancedCache;
      }

      private String kill() {
         this.alive = false;
         log.warn("Killing node: " + this);
         String name = cacheManager.getAddress().toString();
         //TODO disconnect me first from network
         advancedCache.stop();
         TestingUtil.killCacheManagers(cacheManager);
         return name;
      }

      public boolean isMasterNodeForIndex(final String indexName) {
         //Isolated nodes are always master on their own, but that's not the
         //master we're looking for:
         if (!alive) return false;

         //Implicitly verifies the components are setup as expected by casting:
         SearchManager searchManager = Search.getSearchManager(advancedCache);
         SearchFactoryImplementor searchFactory = (SearchFactoryImplementor) searchManager.getSearchFactory();
         final InfinispanIndexManager indexManager = (InfinispanIndexManager) searchFactory.getIndexManagerHolder().getIndexManager(indexName);
         return indexManager != null && indexManager.isMasterLocal();
      }

      public Cache getCache() {
         return this.advancedCache;
      }

      public String toString() {
         return cacheManager.getAddress().toString();
      }

   }

}
