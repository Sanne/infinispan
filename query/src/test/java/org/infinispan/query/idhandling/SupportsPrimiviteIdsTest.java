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
package org.infinispan.query.idhandling;

import static junit.framework.Assert.assertEquals;

import org.apache.lucene.search.Query;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.ProvidedId;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "query.idhandling.SupportsPrimiviteIdsTest")
public class SupportsPrimiviteIdsTest  extends SingleCacheManagerTest {

   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder c = getDefaultStandaloneCacheConfig(true);
      c.indexing()
         .enable()
         .addProperty("hibernate.search.default.directory_provider", "ram")
         .addProperty("hibernate.search.lucene_version", "LUCENE_CURRENT");
      return TestCacheManagerFactory.createCacheManager(c);
   }

   @Test
   public void testReplaceSimpleSearchable() {
      CustomIndexedType se1 = new CustomIndexedType("Unqualified ProvidedId test");
      cache.put(12, se1);

      SearchManager qf = Search.getSearchManager(cache);
      QueryBuilder queryBuilder = qf.buildQueryBuilderForClass(CustomIndexedType.class).get();

      Query ispnIssueQuery = queryBuilder
            .keyword()
               .onField("value")
               .matching("ProvidedId test")
            .createQuery();

      assertEquals(1, qf.getQuery(ispnIssueQuery).list().size());

      Query idQuery = queryBuilder
         .keyword()
            .onField("CustomIdName")
            .ignoreAnalyzer()
            .matching("I:12")
         .createQuery();

      assertEquals(1, qf.getQuery(idQuery).list().size());
   }

   @Indexed
   @ProvidedId(name="CustomIdName")
   private static class CustomIndexedType {

      private final String value;

      public CustomIndexedType(String string) {
         this.value = string;
      }

      @Field
      public String getValue() {
         return value;
      }

   }

}
