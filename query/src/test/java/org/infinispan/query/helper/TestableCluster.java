package org.infinispan.query.helper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.transaction.TransactionManager;

import net.jcip.annotations.GuardedBy;

import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.indexmanager.InfinispanIndexManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
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

   private static final boolean FORCE_WAIT_FOR_CLUSTER = true;

   private static final int MAX_CLUSTER_SIZE = 100;

   private final Random rand = new Random();

   @GuardedBy("itself")
   private final Set<Node> allNodes = new HashSet<>();

   private final BlockingQueue<Node> availableNodes = new ArrayBlockingQueue<>(MAX_CLUSTER_SIZE, true);

   private final String configurationResourceName;
   private final boolean transactionsEnabled;

   public TestableCluster(String configurationResourceName, boolean transactionsEnabled) {
      this.configurationResourceName = configurationResourceName;
      this.transactionsEnabled = transactionsEnabled;
   }

   public void startNewNode(boolean waitForClusterFormation) {
      synchronized (allNodes) {
         if (allNodes.size()==MAX_CLUSTER_SIZE) {
            throw new IllegalStateException("Too many nodes running already!");
         }

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
         out("Scaled UP, " + allNodes.size() + " nodes in the cluster");
      }
   }

   public void killAll() {
      synchronized (allNodes) {
         List<CacheContainer> cacheManagers = new ArrayList<>(allNodes.size());
         for (Node n : allNodes) {
            cacheManagers.add(n.cacheManager);
         }
         TestingUtil.killCacheManagers(cacheManagers);
         allNodes.clear();
      }
      //And try to unfreeze any thread which might be waiting on the queue:
      for (int i=0; i<MAX_CLUSTER_SIZE; i++) {
         availableNodes.offer(new Node(null,null));
      }
   }

   public void killRandomNode() {
      final Node node = takeAnyNode();
      killNode(node);
   }

   public void killMasterNodeForIndex(String indexName) {
      //Needs to lock around the selection, or the master role might change
      synchronized (allNodes) {
         final Node masterNode = takeMasterNode(indexName);
         killNode(masterNode);
      }
   }

   private void killNode(final Node node) {
      synchronized (allNodes) {
         node.kill();
         Assert.assertTrue(allNodes.remove(node));
         waitForRehashToComplete(allNodes);
         out("Scaled DOWN, " + allNodes.size() + " nodes in the cluster");
      }
   }

   /**
    * Returns a node to the pool
    */
   public void returnNode(Node reference) {
      availableNodes.add(reference);
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
         //System.out.println("Scarce resources! Thread blocked in wait for available node");
         try {
            return availableNodes.take();
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for an available node", e);
         }
      }
   }

   @GuardedBy("allNodes")//Role of the node might change during invocation if you don't hold this lock
   private Node takeMasterNode(String indexName) {
      for (int y=0; y<10000; y++) {
         for (int i=0; i<100; i++) {
            final Node n = takeAnyNode();
            if (n.isMasterNodeForIndex(indexName)) {
               return n;
            }
            else {
               returnNode(n);
               //This recursion might spin, as there isn't necessarily a master
               //available at all points in time
               try {
                  Thread.sleep(5);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new RuntimeException("Interrupted while waiting for the Master node to be available", e);
               }
            }
         }
         System.out.println("Could not get the MASTER node quickly; spinning!");
      }
      throw new RuntimeException("Couldn't get the Master node in reasonable time");
   }

   private static void out(final String string) {
      System.out.println("*** " + string);
   }

   public final static class Node {

      private final EmbeddedCacheManager cacheManager;
      private final AdvancedCache advancedCache;
      private volatile boolean alive = true;

      private Node(EmbeddedCacheManager cacheManager, AdvancedCache advancedCache) {
         this.cacheManager = cacheManager;
         this.advancedCache = advancedCache;
      }

      private void kill() {
         this.alive = false;
         //TODO disconnect me first from network
         advancedCache.stop();
         TestingUtil.killCacheManagers(cacheManager);
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

   }

}
