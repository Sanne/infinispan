package org.infinispan.query.indexmanager;

import java.util.Properties;

import org.hibernate.search.backend.impl.lucene.LuceneBackendQueueProcessor;
import org.hibernate.search.backend.spi.BackendQueueProcessor;
import org.hibernate.search.indexes.impl.DirectoryBasedIndexManager;
import org.hibernate.search.infinispan.impl.InfinispanDirectoryProvider;
import org.hibernate.search.spi.WorkerBuildContext;
import org.hibernate.search.store.DirectoryProvider;
import org.infinispan.query.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * A custom IndexManager to store indexes in the grid itself.
 * 
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2012 Red Hat Inc.
 */
public class InfinispanIndexManager extends DirectoryBasedIndexManager {

   private static final Log log = LogFactory.getLog(InfinispanIndexManager.class, Log.class);

   private InfinispanCommandsBackend remoteMaster;

   protected BackendQueueProcessor createBackend(String indexName, Properties cfg, WorkerBuildContext buildContext) {
      BackendQueueProcessor localMaster = createLocalLuceneBackend(cfg, buildContext);
      remoteMaster = new InfinispanCommandsBackend();
      remoteMaster.initialize(cfg, buildContext, this);
      //localMaster is already initialized by the BackendFactory
      MasterSwitchDelegatingQueueProcessor joinedMaster = new MasterSwitchDelegatingQueueProcessor(localMaster, remoteMaster);
      return joinedMaster;
   }

   private BackendQueueProcessor createLocalLuceneBackend(Properties cfg, WorkerBuildContext buildContext) {
      //Rather than using the BackendFactory, we force specifically the creation of a LuceneBackendQueueProcessor:
      LuceneBackendQueueProcessor luceneBackendQueueProcessor = new LuceneBackendQueueProcessor();
      luceneBackendQueueProcessor.initialize(cfg, buildContext, this);
      return luceneBackendQueueProcessor;
   }

   protected DirectoryProvider createDirectoryProvider(String indexName, Properties cfg, WorkerBuildContext buildContext) {
      //warn user we're overriding the configured DirectoryProvider - if anything different than Infinispan is selected.
      String directoryOption = cfg.getProperty("directory_provider", null);
      if (directoryOption != null && ! "infinispan".equals(directoryOption)) {
         log.ignoreDirectoryProviderProperty(indexName, directoryOption);
      }
      InfinispanDirectoryProvider infinispanDP = new InfinispanDirectoryProvider();
      infinispanDP.initialize(indexName, cfg, buildContext);
      return infinispanDP;
   }

   public InfinispanCommandsBackend getRemoteMaster() {
      return remoteMaster;
   }

}
