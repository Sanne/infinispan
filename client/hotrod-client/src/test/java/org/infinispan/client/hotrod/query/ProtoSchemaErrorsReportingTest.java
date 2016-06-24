package org.infinispan.client.hotrod.query;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.client.hotrod.test.SingleHotRodServerTest;
import org.infinispan.commons.equivalence.ByteArrayEquivalence;
import org.infinispan.configuration.cache.Index;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

/**
 * Verify that deploying an invalid schema causes errors to be reported.
 * 
 * @since 9.0
 * @author Sanne Grinovero <sanne@infinispan.org> (C) 2016 Red Hat Inc.
 */
@Test(testName = "client.hotrod.query.ProtoSchemaErrorsReportingTest", groups = "functional")
public class ProtoSchemaErrorsReportingTest extends SingleHotRodServerTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      org.infinispan.configuration.cache.ConfigurationBuilder builder = new org.infinispan.configuration.cache.ConfigurationBuilder();
      builder.dataContainer()
            .keyEquivalence(ByteArrayEquivalence.INSTANCE)
            .valueEquivalence(ByteArrayEquivalence.INSTANCE)
            .indexing().index(Index.ALL)
            .addProperty("default.directory_provider", "ram")
            .addProperty("lucene_version", "LUCENE_CURRENT");

      return TestCacheManagerFactory.createCacheManager(builder);
   }

   @Override
   protected RemoteCacheManager getRemoteCacheManager() {
      org.infinispan.client.hotrod.configuration.ConfigurationBuilder clientBuilder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
      clientBuilder.addServer().host("127.0.0.1").port(hotrodServer.getPort());
      clientBuilder.marshaller(new ProtoStreamMarshaller());
      return new RemoteCacheManager(clientBuilder.build());
   }

   public void testIllegalSchemaDeploy() throws Exception {
      RemoteCache<String, String> metadataCache = remoteCacheManager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
      assertFalse(metadataCache.containsKey(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX));
      try {
         metadataCache.put("broken.proto", "This ins't a valid protobuf schema, isn't it ?!");
         fail("The previous operation should have thrown a CacheException");
      }
      catch (HotRodClientException hrce) {
         //expecting this exception
         assertTrue(hrce.toString().contains("Failed to parse proto file : broken.proto"));
      }
      assertTrue(metadataCache.containsKey(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX));
   }

}
