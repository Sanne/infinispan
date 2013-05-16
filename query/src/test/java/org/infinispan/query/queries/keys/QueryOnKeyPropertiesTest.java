/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.query.queries.keys;

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.search.Query;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.ProvidedId;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.bridge.LuceneOptions;
import org.hibernate.search.bridge.TwoWayFieldBridge;
import org.hibernate.search.engine.impl.LuceneOptionsImpl;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.Transformer;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Example query to match a selected subset of fields from the key of the entry
 * 
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2013 Red Hat Inc.
 * @since 5.3
 */
@Test(groups = "functional", testName = "query.queries.keys.QueryOnKeyPropertiesTest")
public class QueryOnKeyPropertiesTest extends SingleCacheManagerTest {

   public QueryOnKeyPropertiesTest() {
      cleanup = CleanupPhase.AFTER_METHOD;
   }

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder cfg = getDefaultStandaloneCacheConfig(true);
      cfg.indexing().enable().indexLocalOnly(false).addProperty("default.directory_provider", "ram")
            .addProperty("lucene_version", "LUCENE_CURRENT");
      return TestCacheManagerFactory.createCacheManager(cfg);
   }

   @Test
   public void testQueryingRangeBelowExcludingLimit() {
      Book dragonsBook = storeBook("Karl Stig-Erland Larsson", "The Girl with the Dragon Tattoo", "The whole books should be typed here; contributions welcome only with copyright donation.");

      QueryBuilder queryBuilder = Search.getSearchManager(cache).buildQueryBuilderForClass(Book.class).get();

      Query ftQuery = queryBuilder.keyword().onField("title").ignoreFieldBridge().matching("dragon").createQuery();

      CacheQuery cacheQuery = Search.getSearchManager(cache).getQuery(ftQuery);
      List<Object> found = cacheQuery.list();

      AssertJUnit.assertEquals(1, found.size());
      assert found.contains(dragonsBook);
   }

   private Book storeBook(String authorName, String title, String content) {
      BookIdentifier key = new BookIdentifier(authorName, title);
      Book value = new Book(content);
      CustomTransformer transformer = new CustomTransformer();
      assert transformer.fromString(transformer.toString(key)).equals(key); //verify the Transformer works fine with this key
      cache.put(key, value);
      return value;
   }

//   @Transformable(transformer = CustomTransformer.class)
   public static class BookIdentifier {

      private final String authorName;
      private final String title;

      public BookIdentifier(String authorName, String title) {
         this.authorName = authorName;
         this.title = title;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + ((authorName == null) ? 0 : authorName.hashCode());
         result = prime * result + ((title == null) ? 0 : title.hashCode());
         return result;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj)
            return true;
         if (obj == null)
            return false;
         if (getClass() != obj.getClass())
            return false;
         BookIdentifier other = (BookIdentifier) obj;
         if (authorName == null) {
            if (other.authorName != null)
               return false;
         } else if (!authorName.equals(other.authorName))
            return false;
         if (title == null) {
            if (other.title != null)
               return false;
         } else if (!title.equals(other.title))
            return false;
         return true;
      }
   }

   @Indexed
   @ProvidedId(bridge = @org.hibernate.search.annotations.FieldBridge(impl = CustomBridge.class))
   public static class Book {

      @Field
      String content;

      public Book(String content) {
         this.content = content;
      }
   }

   public static class CustomBridge implements TwoWayFieldBridge {

      private static final CustomTransformer transformer = new CustomTransformer();
      private static final LuceneOptions additionalFieldsOption = new LuceneOptionsImpl(Store.NO, Index.ANALYZED, TermVector.NO, 1.0f);

      @Override
      public void set(String fieldName, Object value, Document document, LuceneOptions luceneOptions) {
         String stringEncoded = transformer.toString((BookIdentifier) value);
         BookIdentifier id = (BookIdentifier)value;
         luceneOptions.addFieldToDocument(fieldName, stringEncoded, document);
         additionalFieldsOption.addFieldToDocument("author", id.authorName, document);
         additionalFieldsOption.addFieldToDocument("title", id.title, document);
      }

      @Override
      public Object get(String fieldName, Document document) {
         String stringEncodedKey = document.get(fieldName);
         return transformer.fromString(stringEncodedKey);
      }

      @Override
      public String objectToString(Object customType) {
         return transformer.toString((BookIdentifier)customType);
      }
   }

   public static class CustomTransformer implements Transformer<BookIdentifier> {

      @Override
      public BookIdentifier fromString(String string) {
         int separatorIndex = string.indexOf('-');
         String lengthOfAuthor = string.substring(0, separatorIndex);
         final int endOfAuthor = separatorIndex + 1 + Integer.parseInt(lengthOfAuthor, 16);
         final String author = string.substring(separatorIndex + 1, endOfAuthor);
         final String title = string.substring(endOfAuthor, string.length());
         return new BookIdentifier(author, title);
      }

      @Override
      public String toString(BookIdentifier id) {
         final int lengthAuthor = id.authorName.length();
         final int lengthTitle = id.title.length();
         return new StringBuilder( lengthAuthor + lengthTitle + 3)
            .append(Integer.toHexString(lengthAuthor))
            .append('-')
            .append(id.authorName)
            .append(id.title)
            .toString();
      }
   }

}
