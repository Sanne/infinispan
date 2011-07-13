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
public interface IndexAdaptor {

   /**
    * @return
    */
   int maxDoc();

   /**
    * @return
    */
   int numDocs();

   /**
    * Returns the number of documents containing the term <code>term</code>.
    * @throws IOException if there is a low-level IO error
    */
   int docFreq(Term term);

   /** Returns an unpositioned {@link TermDocs} enumerator.
    * <p>
    * Note: the TermDocs returned is unpositioned. Before using it, ensure
    * that you first position it with {@link TermDocs#seek(Term)} or 
    * {@link TermDocs#seek(TermEnum)}.
    * 
    * @throws IOException if there is a low-level IO error
    */
   TermDocs termDocs();

}
