package org.infinispan.query.backend;

import java.util.Properties;

import org.hibernate.search.infinispan.CacheManagerService;
import org.hibernate.search.spi.BuildContext;
import org.infinispan.manager.EmbeddedCacheManager;

class CacheManagerServiceProvider implements CacheManagerService {

	private final EmbeddedCacheManager cacheManager;

	public CacheManagerServiceProvider(EmbeddedCacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@Override
	public void start(Properties properties, BuildContext context) {
	}

	@Override
	public void stop() {
	}

	@Override
	public EmbeddedCacheManager getEmbeddedCacheManager() {
		return cacheManager;
	}

}
