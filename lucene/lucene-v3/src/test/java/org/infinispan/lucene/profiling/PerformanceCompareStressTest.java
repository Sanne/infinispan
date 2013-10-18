package org.infinispan.lucene.profiling;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.infinispan.AdvancedCache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.lucene.CacheTestSupport;
import org.infinispan.lucene.DirectoryIntegrityCheck;
import org.infinispan.lucene.directory.DirectoryBuilder;
import org.infinispan.test.TestingUtil;
import org.infinispan.transaction.TransactionMode;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * PerformanceCompareStressTest is useful to get an idea on relative performance between Infinispan
 * in local or clustered mode against a RAMDirectory or FSDirectory. To be reliable set a long
 * DURATION_MS and a number of threads similar to the use case you're interested in: results might
 * vary on the number of threads because of the lock differences. This is not meant as a benchmark
 * but used to detect regressions.
 * 
 * This requires Lucene > 2.9.1 or Lucene > 3.0.0 because of
 * https://issues.apache.org/jira/browse/LUCENE-2095
 * 
 * @author Sanne Grinovero
 * @since 4.0
 */
@Test(groups = "profiling", testName = "lucene.profiling.PerformanceCompareStressTest", sequential = true)
public class PerformanceCompareStressTest {

   /**
    * The number of terms in the dictionary used as source of terms by the IndexWriter to produce
    * new documents
    */
   private static final int DICTIONARY_SIZE = 800 * 1000;

   /** Concurrent Threads in tests */
   private static final int READER_THREADS = 5;
   private static final int WRITER_THREADS = 1;

   private static final int GRID_NODES = 5;

   private static final int CHUNK_SIZE = 256 * 1024;

   private static final String indexName = "tempIndexName";

   private static final long DURATION_MS = 12 * 60 * 1000;

   private ConfigurationBuilder configuration() {
      ConfigurationBuilder cfg = CacheTestSupport.createTestConfiguration(TransactionMode.NON_TRANSACTIONAL);
      cfg.clustering().l1().enable();
      cfg.invocationBatching().disable();
      cfg.jmxStatistics().disable();
      cfg.storeAsBinary().disable();
      return cfg;
   }

   @Test
   public void profileTestRAMDirectory() throws InterruptedException, IOException {
      RAMDirectory dir = new RAMDirectory();
      stressTestDirectory(dir, "RAMDirectory");
   }

   @Test
   public void profileTestFSDirectory() throws InterruptedException, IOException {
      File indexDir = new File(new File("."), indexName);
      boolean directoriesCreated = indexDir.mkdirs();
      assert directoriesCreated : "couldn't create directory for FSDirectory test";
      FSDirectory dir = FSDirectory.open(indexDir);
      stressTestDirectory(dir, "FSDirectory");
   }

   @Test
   public void profileTestInfinispanDirectoryWithNetworkDelayZero() throws Exception {
      InfinispanTestGrid grid = new InfinispanTestGrid(configuration(), GRID_NODES, 0 );
      try {
         grid.start();
         AdvancedCache cache = grid.getCache(0);
         Directory dir = DirectoryBuilder.newDirectoryInstance(cache, cache, cache, indexName).chunkSize(CHUNK_SIZE).create();
         stressTestDirectory(dir, "InfinispanClustered-delayedIO:0");
         verifyDirectoryState(cache);
      }
      finally {
         grid.close();
      }
   }

   @Test
   public void profileTestInfinispanDirectoryWithNetworkDelay1() throws Exception {
      InfinispanTestGrid grid = new InfinispanTestGrid(configuration(), GRID_NODES, 1 );
      try {
         grid.start();
         AdvancedCache cache = grid.getCache(0);
         Directory dir = DirectoryBuilder.newDirectoryInstance(cache, cache, cache, indexName).chunkSize(CHUNK_SIZE).create();
         stressTestDirectory(dir, "InfinispanClustered-delayedIO:4");
         verifyDirectoryState(cache);
      }
      finally {
         grid.close();
      }
   }

   @Test
   public void profileTestInfinispanDirectoryWithHighNetworkDelay4() throws Exception {
      InfinispanTestGrid grid = new InfinispanTestGrid(configuration(), GRID_NODES, 400 );
      try {
         grid.start();
         AdvancedCache cache = grid.getCache(0);
         Directory dir = DirectoryBuilder.newDirectoryInstance(cache, cache, cache, indexName).chunkSize(CHUNK_SIZE).create();
         stressTestDirectory(dir, "InfinispanClustered-delayedIO:40");
         verifyDirectoryState(cache);
      }
      finally {
         grid.close();
      }
   }

   @Test
   public void profileInfinispanLocalDirectory() throws Exception {
      InfinispanTestGrid grid = new InfinispanTestGrid(configuration(), 1, 0 );
      try {
         grid.start();
         AdvancedCache cache = grid.getCache(0);
         Directory dir = DirectoryBuilder.newDirectoryInstance(cache, cache, cache, indexName).chunkSize(CHUNK_SIZE).create();
         stressTestDirectory(dir, "InfinispanLocal");
         verifyDirectoryState(cache);
      }
      finally {
         grid.close();
      }
   }

   @Test(enabled=false)//to prevent invocations from some versions of TestNG
   public static void stressTestDirectory(Directory dir, String testLabel) throws InterruptedException, IOException {
      SharedState state = new SharedState(DICTIONARY_SIZE);
      CacheTestSupport.initializeDirectory(dir);
      ExecutorService e = Executors.newFixedThreadPool(READER_THREADS + WRITER_THREADS);
      for (int i = 0; i < READER_THREADS; i++) {
         e.execute(new LuceneReaderThread(dir, state));
      }
      for (int i = 0; i < WRITER_THREADS; i++) {
         e.execute(new LuceneWriterThread(dir, state));
      }
      e.shutdown();
      state.startWaitingThreads();
      Thread.sleep(DURATION_MS);
      long searchesCount = state.incrementIndexSearchesCount(0);
      long writerTaskCount = state.incrementIndexWriterTaskCount(0);
      state.quit();
      boolean terminatedCorrectly = e.awaitTermination(20, TimeUnit.SECONDS);
      AssertJUnit.assertTrue(terminatedCorrectly);
      System.out.println("Test " + testLabel + " run in " + DURATION_MS + "ms:\n\tSearches: " + searchesCount + "\n\t" + "Writes: "
               + writerTaskCount);
   }

   @AfterMethod
   public void afterTest() {
      TestingUtil.recursiveFileRemove(indexName);
   }

   private void verifyDirectoryState(AdvancedCache cache) {
      DirectoryIntegrityCheck.verifyDirectoryStructure(cache, indexName, true);
   }

   /**
    * It's much better to compare performance out of the scope of TestNG by
    * running this directly as TestNG enables assertions.
    * 
    * Suggested test switches:
    * -Xmx2G -Xms2G -XX:MaxPermSize=128M -XX:+HeapDumpOnOutOfMemoryError -Xss512k -XX:HeapDumpPath=/tmp/java_heap -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1 -Xbatch -server -XX:+UseCompressedOops -XX:+UseLargePages -XX:LargePageSizeInBytes=2m -XX:+AlwaysPreTouch
    */
   public static void main(String[] args) throws Exception {
      PerformanceCompareStressTest test = new PerformanceCompareStressTest();
//      test.profileTestRAMDirectory();
//      test.profileTestFSDirectory();
//      test.profileInfinispanLocalDirectory();
      test.profileTestInfinispanDirectoryWithNetworkDelayZero();
//      test.profileTestInfinispanDirectoryWithHighNetworkDelay4();
//      test.profileTestInfinispanDirectoryWithHighNetworkDelay40();
      test.afterTest();
   }

}
