package org.infinispan.query.blackbox;

import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Testing the ISPN Directory configuration with Async. FileCacheStore. The tests are performed on Local cache.
 *
 * @author Anna Manukyan
 */
@Test(groups = "functional", testName = "query.blackbox.LocalCacheAsyncCacheStoreTest")
public class LocalCacheAsyncCacheStoreTest extends LocalCacheTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      cacheManager = TestCacheManagerFactory.fromXml("async-store-config.xml");
      cache = cacheManager.getCache("queryCache_lucenestore_async_filestore");

      return cacheManager;
   }

   @Override
   @AfterMethod
   protected void destroyAfterMethod() {
      try {
         //first stop cache managers, then clear the index
         super.destroyAfterMethod();
      } finally {
         //delete the index otherwise it will mess up the index for next tests
         TestingUtil.recursiveFileRemove(System.getProperty("java.io.tmpdir"));
      }
   }
}
