package org.infinispan.lucene.profiling;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.infinispan.AdvancedCache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.test.TestingUtil;

public final class InfinispanTestGrid implements Closeable {

   private final List<TestNode> nodes;
   private final ConfigurationBuilder configuration;
   private final int networkDelayNanos;
   private final int initialNumberOfNodes;

   public InfinispanTestGrid(ConfigurationBuilder configurationBuilder, int initialNumberOfNodes, int networkDelayNanos) {
      this.configuration = configurationBuilder;
      this.initialNumberOfNodes = initialNumberOfNodes;
      this.networkDelayNanos = networkDelayNanos;
      this.nodes = new ArrayList<TestNode>(initialNumberOfNodes);
   }

   public synchronized void start() throws Exception {
      assert nodes.isEmpty();
      for (int i=0; i<initialNumberOfNodes; i++) {
         addNode();
      }
      if (initialNumberOfNodes>1) {
         TestingUtil.blockUntilViewReceived(nodes.get(0).getCache(), initialNumberOfNodes, 4000*initialNumberOfNodes, true);
      }
   }

   public synchronized void addNode() throws Exception {
      nodes.add(new TestNode(configuration, networkDelayNanos));
   }

   public synchronized void killNode(int index) {
      nodes.remove(index).kill();
   }

   public synchronized int nodesCount() {
      return nodes.size();
   }

   public synchronized AdvancedCache getCache(int index) {
      return nodes.get(index).getCache().getAdvancedCache();
   }

   @Override
   public synchronized void close() throws IOException {
      while (! nodes.isEmpty()) {
         killNode(0);
      }
      assert nodes.isEmpty();
   }

}
