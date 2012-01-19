/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other
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
package org.infinispan.remoting.transport;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Sanne
 */
public class AddressCollection implements Iterable<Address> {
   
   public AddressCollection(List<Address> initialValues) {
      
   }

   /**
    * 
    */
   public AddressCollection() {
      // TODO Auto-generated constructor stub
   }

   /**
    * @param addresses
    */
   public AddressCollection(Collection<Address> addresses) {
      // TODO Auto-generated constructor stub
   }

   /**
    * @param address
    * @param address2
    */
   public AddressCollection(Address address, Address address2) {
      // TODO Auto-generated constructor stub
   }

   /**
    * Retains only the elements in this list that are contained in the
    * specified collection (optional operation).  In other words, removes
    * from this list all the elements that are not contained in the specified
    * collection.
    *
    * @param c collection containing elements to be retained in this list
    * @return <tt>true</tt> if this list changed as a result of the call
    * @throws UnsupportedOperationException if the <tt>retainAll</tt> operation
    *         is not supported by this list
    * @throws ClassCastException if the class of an element of this list
    *         is incompatible with the specified collection (optional)
    * @throws NullPointerException if this list contains a null element and the
    *         specified collection does not permit null elements (optional),
    *         or if the specified collection is null
    * @see #remove(Object)
    * @see #contains(Object)
    */
   public AddressCollection retainAll(List<Address> members) {
      // TODO Auto-generated method stub
      return null;
      
   }

   public AddressCollection retainAll(AddressCollection members) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param members
    * @return
    */
   public AddressCollection copyRetainingAddresses(AddressCollection members) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @return
    */
   public boolean isEmpty() {
      // TODO Auto-generated method stub
      return false;
   }

   /**
    * @return
    */
   public int size() {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * @return
    */
   public List<org.jgroups.Address> toJGroupsAddressList() {
      // org.infinispan.remoting.transport.jgroups.JGroupsTransport.toJGroupsAddressList(Collection<Address>)
      return null;
   }

   /**
    * @param coordinator
    * @return
    */
   public static AddressCollection singleton(Address coordinator) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param node
    * @return
    */
   public boolean contains(Address node) {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public Iterator<Address> iterator() {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param leavers
    */
   public AddressCollection withoutAll(AddressCollection leavers) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param joiners
    * @param leavers
    * @return
    */
   public AddressCollection withAllWithoutAll(AddressCollection joiners, AddressCollection leavers) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * Adds an element to a new copy of this and returns the new collection
    * @param joiner
    */
   public AddressCollection with(Address joiner) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param recipients
    * @return
    */
   public boolean notContainingAll(AddressCollection recipients) {
      // TODO Auto-generated method stub
      return false;
   }

   /**
    * @return
    */
   public static AddressCollection emptyList() {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param leavers2
    * @return
    */
   public AddressCollection withAll(AddressCollection leavers2) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @return
    */
   public List<Address> toAddressList() {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param toRemove
    * @return
    */
   public AddressCollection without(Address toRemove) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param moreAddresses
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public AddressCollection withAll(Address[] moreAddresses) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param moreAddresses
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public AddressCollection withAll(Collection<Address> moreAddresses) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public Address getFirstAddress() {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param i
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public Address get(int i) {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * @param members
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public boolean containsAll(AddressCollection members) {
      // TODO Auto-generated method stub
      return false;
   }

   /**
    * @param a1
    * @return
    */
   @Deprecated //temporary to simplify migrations
   public int indexOf(Address a1) {
      // TODO Auto-generated method stub
      return 0;
   }

}
