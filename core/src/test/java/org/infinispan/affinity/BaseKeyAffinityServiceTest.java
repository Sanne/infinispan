/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010 Red Hat Inc. and/or its affiliates and other
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
package org.infinispan.affinity;

import org.infinispan.distribution.BaseDistFunctionalTest;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.manager.CacheContainer;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.AddressCollection;
import org.infinispan.test.TestingUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static junit.framework.Assert.assertEquals;

/**
 * @author Mircea.Markus@jboss.com
 * @since 4.1
 */
public class BaseKeyAffinityServiceTest extends BaseDistFunctionalTest {
   
   protected KeyAffinityServiceImpl keyAffinityService;

   protected void assertMapsToAddress(Object o, Address addr) {
      ConsistentHash hash = caches.get(0).getAdvancedCache().getDistributionManager().getConsistentHash();
      List<Address> addresses = hash.locate(o, numOwners);
      assertEquals("Expected key " + o + " to map to address " + addr + ". List of addresses is" + addresses, true, addresses.contains(addr));
   }

   protected AddressCollection topology() {
      return topology(caches.get(0).getCacheManager());
   }

   protected AddressCollection topology(CacheContainer cm) {
      return cm.getCache(cacheName).getAdvancedCache().getRpcManager().getTransport().getMembers();
   }

   protected void assertEventualFullCapacity() throws InterruptedException {
      AddressCollection addresses = topology();
      assertEventualFullCapacity(addresses);
   }

   protected void assertCorrectCapacity() throws InterruptedException {
      assertCorrectCapacity(topology());
   }

   protected void assertEventualFullCapacity(AddressCollection addresses) throws InterruptedException {
      Map<Address, BlockingQueue> blockingQueueMap = keyAffinityService.getAddress2KeysMapping();
      long maxWaitTime = 20 * 60 * 1000; // No more than 20 minutes per address since any more is ridiculous!
      for (Address addr : addresses) {
         BlockingQueue queue = blockingQueueMap.get(addr);
         long giveupTime = System.currentTimeMillis() + maxWaitTime;
         //the queue will eventually get filled
         while (queue.size() != 100 && System.currentTimeMillis() < giveupTime) Thread.sleep(100);
         assertEquals(100, queue.size());
      }
      assertEquals(keyAffinityService.getMaxNumberOfKeys(), keyAffinityService.getExitingNumberOfKeys());
      assertEquals(addresses.size() * 100, keyAffinityService.getExitingNumberOfKeys());
      assertEquals(false, keyAffinityService.isKeyGeneratorThreadActive());
   }

   protected void assertCorrectCapacity(AddressCollection addresses) throws InterruptedException {
      Map<Address, BlockingQueue> blockingQueueMap = keyAffinityService.getAddress2KeysMapping();
      long maxWaitTime = 5 * 60 * 1000;
      for (Address addr : addresses) {
         BlockingQueue queue = blockingQueueMap.get(addr);
         long giveupTime = System.currentTimeMillis() + maxWaitTime;
         while (queue.size() < KeyAffinityServiceImpl.THRESHOLD * 100 && System.currentTimeMillis() < giveupTime) Thread.sleep(100);
         assert queue.size() >= KeyAffinityServiceImpl.THRESHOLD * 100 : "Obtained " + queue.size();
      }
   }

   protected void assertKeyAffinityCorrectness() {
      AddressCollection addressList = topology();
      assertKeyAffinityCorrectness(addressList);
   }

   protected void assertKeyAffinityCorrectness(AddressCollection addressList) {
      Map<Address, BlockingQueue> blockingQueueMap = keyAffinityService.getAddress2KeysMapping();
      for (Address addr : addressList) {
         BlockingQueue queue = blockingQueueMap.get(addr);
         assertEquals(100, queue.size());
         for (Object o : queue) {
            assertMapsToAddress(o, addr);
         }
      }
   }

   protected void waitForClusterToResize() {
      TestingUtil.blockUntilViewsReceived(10000, false, caches);
      TestingUtil.waitForRehashToComplete(caches);
      assertEquals(caches.size(), topology().size());
   }
}
