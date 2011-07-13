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

import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.index.Term;

/**
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 */
public class FakeDocument {

   private final Map<String, FakeField> fields = new TreeMap<String, FakeField>();

   public FakeDocument addField(String fieldName, String value) {
      FakeField fakeField = fields.get(fieldName);
      if (fakeField == null) {
         fakeField = new FakeField();
         fields.put(fieldName, fakeField);
      }
      fakeField.addValue(value);
      return this;
   }

   public boolean matches(Term t) {
      FakeField fakeField = fields.get(t.field());
      if (fakeField==null) {
         return false;
      }
      return fakeField.containsValue(t.text());
   }

}
