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

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;

/**
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 */
public class FakeTermDocs implements TermDocs {

   private final IndexAdaptor sampleIndexAdaptor;

   public FakeTermDocs(IndexAdaptor sampleIndexAdaptor) {
      this.sampleIndexAdaptor = sampleIndexAdaptor;
   }

   @Override
   public void seek(Term term) throws IOException { //Term: defFieldName:Yeahhh!
   }

   @Override
   public void seek(TermEnum termEnum) throws IOException {
   }

   @Override
   public int doc() {
      return 0;
   }

   @Override
   public int freq() {
      return 0;
   }

   @Override
   public boolean next() throws IOException {
      return false;
   }

   @Override
   public int read(int[] docs, int[] freqs) throws IOException {
      return 0; //FIXME return values
   }

   @Override
   public boolean skipTo(int target) throws IOException {
      return false;
   }

   @Override
   public void close() throws IOException {
   }

}
