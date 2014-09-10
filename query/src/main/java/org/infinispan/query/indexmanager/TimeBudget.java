package org.infinispan.query.indexmanager;

import java.util.concurrent.TimeUnit;

import org.infinispan.query.logging.Log;
import org.jboss.logging.Logger;

public final class TimeBudget implements Instant {

   public static final String LOGGER_NAME = "IndexingTimeBudget";
   private static final Log timeBudget = Logger.getMessageLogger(Log.class, LOGGER_NAME);

   private final long startTime;

   private TimeBudget() {
      startTime = System.nanoTime();
   }

   public static Instant startStopwatch() {
      if (! timeBudget.isTraceEnabled()) {
         return null;
      }
      else {
         return new TimeBudget();
      }
   }

   public static void stopStopWatch(Instant start, String prefix) {
      if (start == null) {
         return;
      }
      else {
         long stopTime = System.nanoTime();
         long milliseconds = TimeUnit.MILLISECONDS.convert(stopTime - start.getStartTime(), TimeUnit.NANOSECONDS);
         timeBudget.trace(prefix + milliseconds + " milliseconds");
      }
   }

   @Override
   public long getStartTime() {
      return startTime;
   }

}
