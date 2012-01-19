package org.infinispan.tx;

import java.util.Arrays;

import org.infinispan.transaction.tm.DummyNoXaXid;

public class HashDistributionsTest {

   static final int MAX_SEGMENTS = 1 << 16; // taken from ConcurrentHashMap constants

   public static void main(String[] args) {
      int concurrencyLevel = 32;
      CHMProxy chmMock = constructCHMMock(concurrencyLevel, 0.75f, concurrencyLevel);
      int segsCount = chmMock.segmentsAmount;
      System.out.println("Created a CHM with " + segsCount + " segments");
      int[] segmentUsage = new int[segsCount];
      for (int i=0; i<100; i++) {
         DummyNoXaXid xid = new DummyNoXaXid();
         segmentUsage[chmMock.segmentForObject(xid)]++;
      }
      System.out.println(Arrays.toString(segmentUsage));
   }

   private static CHMProxy constructCHMMock(int initialCapacity, float loadFactor, int concurrencyLevel){
      if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
         throw new IllegalArgumentException();
     if (concurrencyLevel > MAX_SEGMENTS)
         concurrencyLevel = MAX_SEGMENTS;
     int sshift = 0;
     int ssize = 1;
     while (ssize < concurrencyLevel) {
         ++sshift;
         ssize <<= 1;
     }
     return new CHMProxy(32 - sshift, ssize);
   }

   private static class CHMProxy {
      final int segmentShift;
      final int segmentsAmount;
      final int segmentMask;

      CHMProxy(int segmentShift, int segmentsAmount) {
         this.segmentShift = segmentShift;
         this.segmentsAmount = segmentsAmount;
         this.segmentMask = segmentsAmount -1;
      }

      final int segmentForObject(Object e) {
         int hashCode = e.hashCode();
         int hash = hash(hashCode);
         int segmentId = hashToSegmentId(hash, this);
         return segmentId;
      }

      private static int hash(int h) {
         // Spread bits to regularize both segment and index locations,
         // using variant of single-word Wang/Jenkins hash.
         h += (h <<  15) ^ 0xffffcd7d;
         h ^= (h >>> 10);
         h += (h <<   3);
         h ^= (h >>>  6);
         h += (h <<   2) + (h << 14);
         return h ^ (h >>> 16);
     }

      private static int hashToSegmentId(int hash, CHMProxy chm) {
         return (hash >>> chm.segmentShift) & chm.segmentMask;
      }

   }

}
