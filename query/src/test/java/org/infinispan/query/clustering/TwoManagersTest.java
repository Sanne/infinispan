package org.infinispan.query.clustering;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.AccountHS;
import org.infinispan.test.MultipleCacheManagersTest;
import org.testng.annotations.Test;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Test(groups = "functional", testName = "query.dsl.TwoManagersTest")
public class TwoManagersTest extends MultipleCacheManagersTest {

   protected static final String CACHE_NAME = "dist_lucene";
   protected static final String INFINISPAN_CONFIG_FILE = "query-no-tx.xml";
   ExecutorService executorService = Executors.newFixedThreadPool(2);

   @Override
   protected void createCacheManagers() throws Throwable {
      cacheManagers.add(new DefaultCacheManager(INFINISPAN_CONFIG_FILE, false));
      cacheManagers.add(new DefaultCacheManager(INFINISPAN_CONFIG_FILE, false));
   }

   public void testStart() {
      ArrayList<Future> futures = new ArrayList<Future>();
      for (final EmbeddedCacheManager cm : cacheManagers) {
         futures.add(executorService.submit(new Runnable() {
            @Override
            public void run() {
               System.out.println("Starting CM");
               cm.start();
               assertNotNull(cm.getCache(CACHE_NAME));
               System.out.println("Started CM");
            }
         }));
         try {
            Thread.sleep(5000);
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
      }
      for (Future f : futures) {
         try {
            f.get();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      assertEquals(cacheManagers.get(0).getMembers().size(), 2);
      assertEquals(cacheManagers.get(1).getMembers().size(), 2);

      AccountHS account = new AccountHS();
      account.setId(42);
      account.setCreationDate(new Date());
      account.setDescription("Test");

      cacheManagers.get(0).getCache(CACHE_NAME).put(42, account);
      assertEquals(cacheManagers.get(0).getCache(CACHE_NAME).get(42), account);
      assertEquals(cacheManagers.get(1).getCache(CACHE_NAME).get(42), account);
   }

}
