package org.infinispan.query.indexedembedded;

import org.hibernate.search.annotations.Field;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * @author Sanne Grinovero &lt;sanne@hibernate.org&gt; (C) 2011 Red Hat Inc.
 */
public class City {

   @ProtoField(1)
   public String name;

   @Field
   public String getName() {
      return name;
   }
}
