/*
 * Copyright 2011 Red Hat, Inc. and/or its affiliates.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 */

package org.infinispan.marshall.jboss;

import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleDataOutput;
import org.jboss.marshalling.reflect.SerializableClassRegistry;
import org.jboss.marshalling.river.RiverMarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * {@link RiverMarshaller} extension that allows Infinispan code to directly
 * create instances of it.
 *
 * @author Galder Zamarreño
 * @since 5.1
 */
public class ExtendedRiverMarshaller extends RiverMarshaller {

   private static final Log log = LogFactory.getLog(ExtendedRiverMarshaller.class);
   private static final boolean trace = log.isTraceEnabled();

   private RiverCloseListener listener;

   public ExtendedRiverMarshaller(RiverMarshallerFactory factory,
         SerializableClassRegistry registry, MarshallingConfiguration cfg) throws IOException {
      super(factory, registry, cfg);
   }

   @Override
   public void finish() throws IOException {
      // Before finishing, dump contents if debugging
      if (trace) {
         // Reflectiont to get position (!!!)
         try {
            Field positionField = SimpleDataOutput.class.getDeclaredField("position");
            positionField.setAccessible(true);
            int position = ((Integer) positionField.get(this)).intValue();
            log.tracef("Marshalled payload contents: %s", Util.hexDump(buffer, position));
         } catch (NoSuchFieldException e) {
            log.warn("Unable to log marshalled content", e);
         } catch (IllegalAccessException e) {
            log.warn("Unable to log marshalled content", e);
         }
      }

      super.finish();
      if (listener != null) {
         listener.closeMarshaller();
      }
   }

   void setCloseListener(RiverCloseListener listener) {
      this.listener = listener;
   }

}
