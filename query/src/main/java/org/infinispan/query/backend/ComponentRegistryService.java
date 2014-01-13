package org.infinispan.query.backend;

import org.hibernate.search.engine.service.spi.Service;
import org.infinispan.factories.ComponentRegistry;

/**
 * Simple wrapper to make the Cache ComponentRegistry available to the services managed by
 * Hibernate Search
 * 
 * @author Sanne Grinovero
 * @since 5.2
 */
public final class ComponentRegistryService implements Service {

   private final ComponentRegistry cr;

   public ComponentRegistryService(ComponentRegistry cr) {
      this.cr = cr;
   }

   public ComponentRegistry getComponentRegistry() {
      return cr;
   }

}
