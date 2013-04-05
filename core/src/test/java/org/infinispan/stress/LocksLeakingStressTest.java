package org.infinispan.stress;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.testng.annotations.Test;

/**
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2013 Red Hat Inc.
 * @since 6.0
 */
@Test(testName = "stress.LocksLeakingStressTest", groups = "stress", enabled = true, description = "Disabled by default, designed to be run manually.")
public class LocksLeakingStressTest {

	/**
	 * This is a stress test and provides a level
	 * of confidence proportional to the amount of
	 * iterations performed.
	 */
	private static final int TEST_RUNS = 666;

	/**
	 * We scale from one node to this constant, then we start
	 * scaling back by shutting down nodes. Value should be
	 * large enough to test a reasonably sized cluster: there
	 * is no point in testing large clusters but at least
	 * more than DIST num owners should be used to catch
	 * all possible cluster situations.
	 */
	private static final int MAX_NODES = 7;

	/**
	 * At each iteration between the cluster reconfiguration
	 * and the read/write operations we can wait for the cluster
	 * to be strictly verified from the point of view of each
	 * node. Waiting isn't needed with Infinispan but makes
	 * debugging of failures easier.
	 */
	private static final boolean WAIT_CLUSTER_FORMATION = false;

	/**
	 * List of running nodes. Nodes are added/removed during the test run!
	 */
	private final List<EmbeddedCacheManager> nodes = new LinkedList<EmbeddedCacheManager>();

	/**
	 * We'll have the cluster grow initially, to then start shrinking and growing again.
	 */
	private boolean growCluster = true;

	@Test
	public void liveRun() {
		try {
			for ( int i = 0; i < TEST_RUNS; i++ ) {
				adjustNodesNumber( i );
				locksChecking();
			}
		}
		finally {
			for ( EmbeddedCacheManager node : nodes ) {
				node.stop();
			}
		}
	}

	private static int clusterSize(EmbeddedCacheManager node) {
		return node.getMembers().size();
	}

	private void adjustNodesNumber(int i) {
		if ( growCluster ) {
			if ( nodes.size() >= MAX_NODES ) {
				growCluster = false;
				killFirstNode( nodes );
			}
			else {
				addNewNode( nodes );
			}
		}
		else {
			if ( nodes.size() == 1 ) {
				growCluster = true;
				addNewNode( nodes );
			}
			else {
				killFirstNode( nodes );
			}
		}
		if ( WAIT_CLUSTER_FORMATION ) {
			waitForAllJoinsCompleted();
		}
	}

	private static void addNewNode(List<EmbeddedCacheManager> nodeList) {
		nodeList.add( createNewNode() );
	}

	private void killFirstNode(List<EmbeddedCacheManager> nodesList) {
		// we remove the oldest one: usually the most interesting to kill
		EmbeddedCacheManager cacheManager = nodesList.remove( 0 );
		cacheManager.stop();
		System.out.println( "One node removed from grid" );
	}

	private void locksChecking() {
		for ( EmbeddedCacheManager node : nodes ) {
		}
	}

	private void waitForAllJoinsCompleted() {
		final int expectedSize = nodes.size();
		for ( EmbeddedCacheManager slave : nodes ) {
			waitMembersCount( slave, expectedSize );
		}
	}

	protected static EmbeddedCacheManager createNewNode() {
		try {
			DefaultCacheManager cacheManager = new DefaultCacheManager( "configs/lock-leaking-test-config.xml" );
			cacheManager.getCache(); //trigger transport initialization
			return cacheManager;
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
		finally {
			System.out.println( "New node created" );
		}
	}

	private static void waitMembersCount(EmbeddedCacheManager node, int expectedSize) {
		int currentSize = 0;
		int loopCounter = 0;
		while ( currentSize < expectedSize ) {
			try {
				Thread.sleep( 10 );
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				throw new RuntimeException( e.getMessage() );
			}
			currentSize = clusterSize( node );
			loopCounter++;
			if ( loopCounter > 400 ) {
				throw new RuntimeException( "timeout while waiting for all nodes to join in cluster. Expected: " + expectedSize );
			}
		}
	}

}
