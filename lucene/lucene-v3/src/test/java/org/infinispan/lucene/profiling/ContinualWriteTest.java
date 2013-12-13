package org.infinispan.lucene.profiling;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.StringHelper;
import org.infinispan.Cache;
import org.infinispan.lucene.CacheTestSupport;
import org.infinispan.lucene.DirectoryIntegrityCheck;
import org.infinispan.lucene.directory.DirectoryBuilder;
import org.infinispan.lucene.testutils.LuceneSettings;
import org.infinispan.manager.CacheContainer;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * ContinualWriteTest.
 * 
 * -Xms8g -Xmx8g -XX:+UseParallelGC -XX:MaxPermSize=256m -Djava.net.preferIPv4Stack=true -Dorg.jboss.resolver.warning=true -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000 -Dcom.arjuna.ats.arjuna.coordinator.CoordinatorEnvironmentBean.asyncPrepare=true -XX:+UseLargePages -Djava.awt.headless=true -Dinfinispan.unsafe.allow_jdk8_chm=true -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:-PrintTLAB -XX:+PrintHeapAtGCExtended -XX:+PrintAdaptiveSizePolicy -XX:+PrintGCApplicationStoppedTime -XX:-PrintGCApplicationConcurrentTime -Xloggc:/tmp/lucene.gclog -XX:+ParallelRefProcEnabled -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=delay=10s,duration=24h,filename=/tmp/flight_record_lucene.jfr,settings=/opt/flightrecorder/CustomInfinispan.jfc
 * 
 * @author sanne
 * @since 5.2
 */
public class ContinualWriteTest {

   private static final int OUTPUT_PERIODICITY_DOCS = 3000000;
   private static final int DOCS_TO_WRITE = OUTPUT_PERIODICITY_DOCS *10;

   private static final String indexName = "tempIndexName";

   private static final int CHUNK_SIZE = 16 * 1024 * 1024;

   private static final int VOCAB_SIZE = 500000;


   @Test
   public void profileTestRAMDirectory() throws InterruptedException, IOException {
      RAMDirectory dir = new RAMDirectory();
      try {
         stressTestDirectory(dir, "RAMDirectory");
      }
      finally {
         dir.close();
      }
   }

   @Test
   public void profileTestFSDirectory() throws InterruptedException, IOException {
      File indexDir = new File(new File("."), indexName);
      boolean directoriesCreated = indexDir.mkdirs();
      assert directoriesCreated : "couldn't create directory for FSDirectory test";
      FSDirectory dir = FSDirectory.open(indexDir);
      try {
         stressTestDirectory(dir, "FSDirectory");
      }
      finally {
         dir.close();
      }
   }

   @Test
   public void profileTestInfinispanDirectoryWithNetworkDelayZero() throws InterruptedException, IOException {
      CacheContainer cacheContainer = CacheTestSupport.createLocalCacheManager();
      try {
         Cache cache = cacheContainer.getCache();
         Directory dir = DirectoryBuilder.newDirectoryInstance(cache, cache, cache, indexName).create();
         stressTestDirectory(dir, "Infinispan:LocalCache");
         DirectoryIntegrityCheck.verifyDirectoryStructure(cache, indexName, true);
      }
      finally {
         TestingUtil.killCacheManagers(cacheContainer);
      }
   }

   @AfterClass
   public void afterTest() {
      TestingUtil.recursiveFileRemove(indexName);
   }

   private void stressTestDirectory(Directory dir, String testName) throws IOException {
      CacheTestSupport.initializeDirectory(dir);
      IndexWriter iwriter = LuceneSettings.openWriter(dir, 5000);
      String[] terms = initializeTermsArray();
      String fieldNameOne = StringHelper.intern("main");
      String fieldNameTwo = StringHelper.intern("secondField");
      System.out.println("Starting test ["+testName+"]");
      long startNanoTime = System.nanoTime();
      for (int i=0; i<DOCS_TO_WRITE; i++) {
         Document doc = new Document();
         doc.add(new Field(fieldNameOne, false, terms[i%terms.length], Store.YES, Index.NOT_ANALYZED, TermVector.WITH_POSITIONS));
         doc.add(new Field(fieldNameTwo, false, terms[(i+10)%terms.length], Store.NO, Index.NOT_ANALYZED, TermVector.YES));
         iwriter.addDocument(doc);
         if (i % OUTPUT_PERIODICITY_DOCS == 0) {
            System.out.println("Written " + OUTPUT_PERIODICITY_DOCS + " documents");
         }
      }
      iwriter.commit();
      iwriter.close();
      long endNanoTime = System.nanoTime();
      System.out.println( testName + " completed in " + TimeUnit.SECONDS.convert(endNanoTime - startNanoTime, TimeUnit.NANOSECONDS) + "seconds");
   }

   private String[] initializeTermsArray() {
      String[] values = new String[VOCAB_SIZE];
      for (int i=0; i<VOCAB_SIZE; i++) {
         values[i] = "v" + i + "-" + (i-500) + "ksom";
      }
      return values;
   }

}
