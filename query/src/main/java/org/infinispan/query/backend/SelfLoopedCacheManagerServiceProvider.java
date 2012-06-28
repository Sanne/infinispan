/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.query.backend;

import java.util.Properties;

import org.hibernate.search.spi.BuildContext;
import org.hibernate.search.spi.ServiceProvider;
import org.infinispan.manager.EmbeddedCacheManager;

/**
 * To be registered as a provided ServiceProvider to the SearchFactory to allow it to extract
 * the CacheManager where needed by Search services, as the custom DirectoryProvider using
 * Infinispan itself.
 * This allows for a given indexed Cache of an EmbeddableCacheManager to use othe caches defined
 * on the same CacheManager to store it's indexes.
 * 
 * @author Sanne Grinovero
 * @since 5.2
 */
final class SelfLoopedCacheManagerServiceProvider implements ServiceProvider<EmbeddedCacheManager> {

   private final EmbeddedCacheManager uninitializedCacheManager;

   public SelfLoopedCacheManagerServiceProvider(EmbeddedCacheManager uninitializedCacheManager) {
      if (uninitializedCacheManager == null) {
         throw new IllegalArgumentException( "null parameter unacceptable" );
      }
      this.uninitializedCacheManager = uninitializedCacheManager;
   }

   @Override
   public void start(Properties properties, BuildContext context) {
      // no-op
   }

   @Override
   public EmbeddedCacheManager getService() {
      return uninitializedCacheManager;
   }

   @Override
   public void stop() {
      // no-op
   }

}
