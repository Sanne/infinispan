package org.infinispan.query.distributed;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.infinispan.Cache;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.helper.StaticTestingErrorHandler;
import org.infinispan.query.helper.TestableCluster;
import org.infinispan.query.helper.TestableCluster.Node;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.fwk.TestResourceTracker;
import org.junit.Assert;
import org.testng.annotations.Test;

/**
 * This test is meant to verify what happens when a write/read happens concurrently with a topology
 * change.
 *
 * Long running soak test verifying stale lock cleanup for the IndexManager. It will only write on
 * stable nodes, and verify queries on each step so it's very slow. The cluster is dynamically
 * scaling randomly between MINIMAL_NODES and MAXIMUM_NODES, but applying a small bias to favour
 * larger clusters.
 *
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2014 Red Hat Inc.
 * @since 7.0
 */
@Test(groups = "profiling", testName = "query.distributed.IndexManagerStressTest")
public class IndexManagerStressTest extends AbstractInfinispanTest {

   private static final int INITIAL_NODES = 3;
   private static final int MINIMAL_NODES = 1;
   private static final int MAXIMUM_NODES = 10;

   private final AtomicInteger uniqueIdGen = new AtomicInteger();
   private final AtomicInteger validStatesVerified = new AtomicInteger();

   /**
    * To be able to keep the worker threads busy, they must be able to use a node exclusively. This
    * is to protect the node being verified from being terminated.
    */
   private final int WORKING_THREADS = 3;

   /**
    * When enabled, each writing thread will verify that the changes it is aware of
    * are indeed immediately found by queries.
    * Disable it to focus on other areas, or to test asynchronous indexing.
    * Warning: during some lock migrations even synchronous backends will temporarily
    * commute to an asynch semantics, so this might lead to failures.
    */
   private final boolean VERIFY_SYNCH_SEARCH = false;

   private final TestableCluster<String, Person> cluster = new TestableCluster<>(getConfigurationResourceName(), false);
   private final AtomicReference exitCause = new AtomicReference();
   private final AtomicBoolean someErrorHappened = new AtomicBoolean(false);

   private volatile ExecutorService executorService;

   protected String getConfigurationResourceName() {
      return "dynamic-indexing-distribution.xml";
   }

   public void testSequentialLoad() throws Exception {
      TestResourceTracker.backgroundTestStarted(this);
      try {
         for (int i = 0; i < INITIAL_NODES; i++) {
            //Wait for cluster formation on the last one:
            cluster.startNewNode(INITIAL_NODES == i);
         }
         executorService = Executors.newFixedThreadPool(WORKING_THREADS + 2);
         for (int i = 0; i < WORKING_THREADS; i++) {
            executorService.submit(new UserThread(i));
         }
         executorService.submit(new TopologyMessupMonkey());
         executorService.submit(new ProgressMonitor());
         executorService.shutdown();
         executorService.awaitTermination(20, TimeUnit.HOURS);
         exitCondition("Main thread waited for termination and timed out", false);
         executorService.awaitTermination(20, TimeUnit.SECONDS);
         executorService.shutdownNow();
      } finally {
         exitCondition("Main thread was exiting eager", true);
         cluster.killAll();
      }
      reportErrors();
   }

   private void reportErrors() {
      if (someErrorHappened.get()) {
         Object cause = exitCause.get();
         StringWriter w = new StringWriter();
         w.append("Failed because: ");
         if (cause instanceof Throwable) {
            ((Throwable) cause).printStackTrace(new PrintWriter(w));
         }
         else {
            w.append(cause.toString());
         }
         String error = w.toString();
         System.err.println(error);
         Assert.fail(error);
      }
   }

   /**
    * Will record an exit explanation, and cause the test to terminate early. If a Throwable is
    * recorded as explanation, that will consider the test failed.
    *
    * @param explanation
    * @param failTest
    */
   private void exitCondition(Object explanation, boolean failTest) {
      Assert.assertNotNull(explanation);
      //We'll record only the first explanation as cause:
      exitCause.compareAndSet(null, explanation);
      someErrorHappened.compareAndSet(false, failTest);
      executorService.shutdownNow();
   }

   private boolean needExit() {
      return exitCause.get() != null;
   }

   private final class UserThread implements Runnable {
      private final String privateKeyWord;
      private int expectedMatches;

      public UserThread(int threadId) {
         this.privateKeyWord = "KEYWORD_" + threadId;
      }

      @Override
      public void run() {
         TestResourceTracker.backgroundTestStarted(IndexManagerStressTest.this);
         System.out.println("User Thread starting..");
         try {
            while (needExit() == false) {
               expectedMatches += writeToIndex(privateKeyWord);
               assertIndexStateIsEventuallyConsistent(expectedMatches, privateKeyWord);
            }
         } catch (Throwable e) {
            exitCondition(e, true);
         } finally {
            exitCondition("User Thread is quitting", false);
         }
      }
   }

   private final class TopologyMessupMonkey implements Runnable {

      @Override
      public void run() {
         TestResourceTracker.backgroundTestStarted(IndexManagerStressTest.this);
         try {
            System.out.println("TopologyMessupMonkey Thread starting..");
            while (needExit() == false) {
               cluster.startNewNode(false);
               cluster.killRandomNode();
               cluster.resizeRandomInRange(MINIMAL_NODES, MAXIMUM_NODES);
               cluster.startNewNode(false);
               cluster.killMasterNodeForIndex("person");
            }
         } catch (Throwable e) {
            exitCondition(e, true);
         } finally {
            exitCondition("TopologyMessupMonkey Thread quitting", false);
         }
      }

   }

   private final class ProgressMonitor implements Runnable {

      @Override
      public void run() {
         TestResourceTracker.backgroundTestStarted(IndexManagerStressTest.this);
         try {
            System.out.println("ProgressMonitor Thread starting..");
            while (needExit() == false) {
               System.out.println(" written " + uniqueIdGen.get() + " entries to the index; Valid index states verified: " + validStatesVerified.get());
               Thread.sleep(2000);
            }
         } catch (Throwable e) {
            exitCondition(e, true);
         } finally {
            exitCondition("ProgressMonitor Thread quitting", false);
         }
      }

   }

   private int writeToIndex(String privateKeyWord) throws Exception {
      final int LOOPS = 7;
      for (int i = 0; i < LOOPS; i++) {
         String key = "key" + uniqueIdGen.incrementAndGet();
         storeInvalid(key, privateKeyWord);
         storeValid(key, privateKeyWord);
      }
      return LOOPS;
   }

   private void storeValid(String key, String privateKeyWord) throws Exception {
      cluster.storeOnAnyNode(key, new Person(privateKeyWord, "VALID"));
   }

   private void storeInvalid(String key, String privateKeyWord) throws Exception {
      cluster.storeOnAnyNode(key, new Person(privateKeyWord, "INVALID"));
   }

   protected void assertIndexStateIsEventuallyConsistent(int expectedMatches, String privateKeyWord) {
      final Node node = cluster.takeAnyNode();
      try {
         Cache cache = node.getCache();
         StaticTestingErrorHandler.assertAllGood(cache);
         if (VERIFY_SYNCH_SEARCH) {
            boolean queryStateOk = false;
            int failuresCount = 0;
            while (!queryStateOk) {
               queryStateOk = verifyQueryState(cache, expectedMatches, privateKeyWord);
               if (queryStateOk==false) {
                  failuresCount++;
                  if (failuresCount==200) {
                     exitCondition("Index condition was not satisfied yet!", true);
                     return;
                  }
                  try {
                     Thread.currentThread().sleep(100);
                  } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     exitCondition("Interrupted!", true);
                  }
               }
            }
         }
      } finally {
         cluster.returnNode(node);
         validStatesVerified.incrementAndGet();
      }
   }

   /**
    * @return True if all conditions are met
    */
   private boolean verifyQueryState(Cache cache, int expectedMatches, String privateKeyWord) {
      SearchManager searchManager = Search.getSearchManager(cache);
      TermQuery byKeyword = new TermQuery(new Term("name", privateKeyWord));
      TermQuery valid = new TermQuery(new Term("surname", "VALID"));
      TermQuery notvalid = new TermQuery(new Term("surname", "INVALID"));
      BooleanQuery validComposite = new BooleanQuery();
      validComposite.add(new BooleanClause(byKeyword, Occur.MUST));
      validComposite.add(new BooleanClause(valid, Occur.MUST));
      BooleanQuery invalidComposite = new BooleanQuery();
      invalidComposite.add(new BooleanClause(byKeyword, Occur.MUST));
      invalidComposite.add(new BooleanClause(notvalid, Occur.MUST));

      CacheQuery validCacheQuery = searchManager.getQuery(validComposite, Person.class);
      if (expectedMatches != validCacheQuery.getResultSize()) {
         return false;
      }
      CacheQuery invalidCacheQuery = searchManager.getQuery(invalidComposite, Person.class);
      return 0 == invalidCacheQuery.getResultSize();
   }

}
