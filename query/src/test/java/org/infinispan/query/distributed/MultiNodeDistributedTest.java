package org.infinispan.query.distributed;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.helper.StaticTestingErrorHandler;
import org.infinispan.query.helper.TestableCluster;
import org.infinispan.query.helper.TestableCluster.Node;
import org.infinispan.query.test.Person;
import org.infinispan.test.AbstractInfinispanTest;
import org.testng.annotations.Test;

/**
 * Configures the Hibernate Search backend to use Infinispan custom commands as a backend
 * transport, and a consistent hash for Master election for each index.
 * The test changes the view several times while indexing and verifying index state.
 *
 * @author Sanne Grinovero
 */
@Test(groups = "functional", testName = "query.distributed.MultiNodeDistributedTest")
public class MultiNodeDistributedTest extends AbstractInfinispanTest {

   protected final TestableCluster<String,Person> cluster = new TestableCluster<>(getConfigurationResourceName(), transactionsEnabled());

   protected String getConfigurationResourceName() {
      return "dynamic-indexing-distribution.xml";
   }

   public void testIndexingWorkDistribution() throws Exception {
      try {
         cluster.startNewNode(false);
         cluster.startNewNode(true);
         assertIndexSize(0);
         //depending on test run, the index master selection might pick either cache.
         //We don't know which cache it picks, but we allow writing & searching on all.
         cluster.storeOnAnyNode("k1", new Person("K. Firt", "Is not a character from the matrix", 1));
         assertIndexSize(1);
         cluster.storeOnAnyNode("k2", new Person("K. Seycond", "Is a pilot", 1));
         assertIndexSize(2);
         cluster.storeOnAnyNode("k3", new Person("K. Theerd", "Forgot the fundamental laws", 1));
         assertIndexSize(3);
         cluster.storeOnAnyNode("k3", new Person("K. Overide", "Impersonating Mr. Theerd", 1));
         assertIndexSize(3);
         cluster.startNewNode(false);
         cluster.storeOnAnyNode("k4", new Person("K. Forth", "Dynamic Topology!", 1));
         assertIndexSize(4);
         cluster.startNewNode(false);
         assertIndexSize(4);
         cluster.killRandomNode();
         cluster.killMasterNodeForIndex("person");
         //After a node kill, a stale lock might not be immediately resolved.
         //Solicit resolution by issues at least three writes:
         cluster.storeOnAnyNode("k5", new Person("K. Vife", "Gets stuck in a buffer", 1));
         cluster.storeOnAnyNode("k6", new Person("K. Seix", "Fills the buffer", 1));
         cluster.storeOnAnyNode("k7", new Person("K. Vife", "Failover!", 1));
         assertIndexSize(7);
      }
      finally {
         cluster.killAll();
      }
   }

   protected void assertIndexSize(int expectedIndexSize) {
      final Node node = cluster.takeAnyNode();
      try {
         StaticTestingErrorHandler.assertAllGood(node.getCache());
         SearchManager searchManager = Search.getSearchManager(node.getCache());
         CacheQuery query = searchManager.getQuery(new MatchAllDocsQuery(), Person.class);
         assertEquals(expectedIndexSize, query.list().size());
      }
      finally {
         cluster.returnNode(node);
      }
   }

   protected boolean transactionsEnabled() {
      return false;
   }

}
