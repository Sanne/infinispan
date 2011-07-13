/* 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
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
package org.infinispan.lucene.distindex;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.index.TermVectorMapper;

/**
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 */
public class DistributedReader extends IndexReader {

   private final IndexAdaptor dataSource;

   public DistributedReader(IndexAdaptor dataSource) {
      this.dataSource = dataSource;
   }

   @Override
   public TermFreqVector[] getTermFreqVectors(int docNumber) throws IOException {
      return null;
   }

   @Override
   public TermFreqVector getTermFreqVector(int docNumber, String field) throws IOException {
      return null;
   }

   @Override
   public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException {
   }

   @Override
   public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException {
   }

   @Override
   public int numDocs() {
      return dataSource.numDocs();
   }

   @Override
   public int maxDoc() {
      return dataSource.maxDoc();
   }

   @Override
   public Document document(int n, FieldSelector fieldSelector) throws CorruptIndexException, IOException {
      return null;
   }

   @Override
   public boolean isDeleted(int n) {
      return false;
   }

   @Override
   public boolean hasDeletions() {
      return false;
   }

   @Override
   public byte[] norms(String field) throws IOException { //defFieldName
      return null; //TODO support Norms
   }

   @Override
   public void norms(String field, byte[] bytes, int offset) throws IOException {
   }

   @Override
   protected void doSetNorm(int doc, String field, byte value) throws CorruptIndexException, IOException {
   }

   @Override
   public TermEnum terms() throws IOException {
      return null;
   }

   @Override
   public TermEnum terms(Term t) throws IOException {
      return null;
   }

   @Override
   public int docFreq(Term t) throws IOException {
      return dataSource.docFreq(t);
   }

   @Override
   public TermDocs termDocs() throws IOException {
      return dataSource.termDocs();
   }

   @Override
   public TermPositions termPositions() throws IOException {
      return null;
   }

   @Override
   protected void doDelete(int docNum) throws CorruptIndexException, IOException {
   }

   @Override
   protected void doUndeleteAll() throws CorruptIndexException, IOException {
   }

   @Override
   protected void doCommit(Map<String, String> commitUserData) throws IOException {
   }

   @Override
   protected void doClose() throws IOException {
   }

   @Override
   public Collection<String> getFieldNames(FieldOption fldOption) {
      return null;
   }

}
