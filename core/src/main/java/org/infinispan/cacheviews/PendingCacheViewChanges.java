/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
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

package org.infinispan.cacheviews;

import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.AddressCollection;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * This class is used on the coordinator to keep track of changes since the last merge.
 * 
 * When the coordinator changes or in case of a merge, the new coordinator recovers the last committed view
 * from all the members and rolls back any uncommitted views, then it prepares a new view if necessary.
 */
public class PendingCacheViewChanges {
   private static final Log log = LogFactory.getLog(PendingCacheViewChanges.class);

   private final Object lock = new Object();

   private final String cacheName;

   // The last view id generated (or received during a recover operation)
   private int lastViewId;
   // The join requests since the last COMMIT_VIEW
   // These are only used if we are the coordinator.
   private volatile AddressCollection joiners;
   // The leave requests are also used on normal nodes to compute the valid members set
   private volatile AddressCollection leavers;
   // True if there was a merge since the last committed view
   private AddressCollection recoveredMembers;

   private boolean viewInstallationInProgress;

   public PendingCacheViewChanges(String cacheName) {
      this.cacheName = cacheName;
      this.joiners = new AddressCollection();
      this.leavers = new AddressCollection();
      this.recoveredMembers = new AddressCollection();
   }

   /**
    * Called on the coordinator to create the view that will be prepared next.
    * It also sets the pendingView field, so the next call to {@code prepareView} will have no effect.
    */
   public CacheView createPendingView(CacheView committedView) {
      synchronized (lock) {
         // TODO Enforce view installation policy here?
         if (viewInstallationInProgress) {
            log.tracef("Cannot create a new view, there is another view installation in progress");
            return null;
         }
         if (leavers.isEmpty() && joiners.isEmpty() && recoveredMembers == null) {
            log.tracef("Cannot create a new view, we have no joiners or leavers");
            return null;
         }

         AddressCollection baseMembers = recoveredMembers != null ? recoveredMembers : committedView.getMembers();
         log.tracef("Previous members are %s, joiners are %s, leavers are %s, recovered after merge = %s",
               baseMembers, joiners, leavers, recoveredMembers != null);
         // If a node is both in leavers and in joiners we should install a view without it first
         // so that other nodes don't consider it an old owner, so we first add it as a joiner
         // and then we remove it as a leaver.
         AddressCollection members = baseMembers.withAllWithoutAll(joiners, leavers);

         viewInstallationInProgress = true;

         lastViewId++;
         CacheView pendingView = new CacheView(lastViewId, members);
         log.tracef("%s: created new view %s", cacheName, pendingView);
         return pendingView;
      }
   }

   /**
    * Called on the coordinator before a rollback to assign a unique view id to the rollback.
    */
   public int getRollbackViewId() {
      synchronized (lock) {
         lastViewId++;
         return lastViewId;
      }
   }

   public boolean hasChanges() {
      return recoveredMembers != null || !joiners.isEmpty() || !leavers.isEmpty();
   }

   public void resetChanges(CacheView committedView) {
      synchronized (lock) {
         // the list of valid members remains the same
         if (log.isDebugEnabled()) {
            // if a node was both a joiner and a leaver, the committed view should not contain it
            AddressCollection bothJoinerAndLeavers = joiners.retainAll(leavers);
            for (Address node : bothJoinerAndLeavers) {
               if (committedView.contains(node)) {
                  log.debugf("Node %s should not be a member in view %s, left and then joined before the view was installed");
               }
            }
         }
         leavers = leavers.retainAll(committedView.getMembers());
         joiners = joiners.withoutAll(committedView.getMembers());
         recoveredMembers = null;

         viewInstallationInProgress = false;
         if (committedView.getViewId() > lastViewId) {
            lastViewId = committedView.getViewId();
         }
      }
   }

   /**
    * Signal a join
    */
   public void requestJoin(Address joiner) {
      synchronized (lock) {
         log.tracef("%s: Node %s is joining", cacheName, joiner);
         // if the node wanted to leave earlier, we don't remove it from the list of leavers
         // since it has already left, it won't have the latest data and so it's not a valid member
         joiners = joiners.with(joiner);
      }
   }

   /**
    * Signal a leave.
    */
   public AddressCollection requestLeave(AddressCollection leavers) {
      synchronized (lock) {
         log.tracef("%s: Nodes %s are leaving", cacheName, leavers);

         // if the node wanted to join earlier, just remove it from the list of joiners
         joiners = joiners.withoutAll(leavers);
         AddressCollection leavers2 = leavers.withoutAll(joiners);
         log.tracef("%s: After pruning nodes that have joined but have never installed a view, leavers are %s", cacheName, leavers2);

         this.leavers = leavers.withAll(leavers2);
         return leavers2;
      }
   }

   /**
    * Signal a merge
    */
   public void recoveredViews(AddressCollection newMembers, AddressCollection recoveredJoiners) {
      synchronized (lock) {
         log.tracef("%s: Coordinator changed, this node is the current coordinator", cacheName);
         // Apply any changes that we may have received before we realized we're the coordinator
         recoveredMembers = newMembers.withoutAll(leavers);
         joiners = joiners.withAllWithoutAll(recoveredJoiners, recoveredMembers);
         log.tracef("%s: Members after coordinator change: %s, joiners: %s, leavers: %s",
               cacheName, recoveredMembers, joiners, leavers);
      }
   }

   /**
    * If we recovered a view after a merge or coordinator change we need to make sure the next view id is greater
    * than any view id that was already committed.
    */
   public void updateLatestViewId(int viewId) {
      synchronized (lock) {
         if (viewId > lastViewId) {
            lastViewId = viewId;
         }
      }
   }

   /**
    * @return the nodes that left since the last {@code resetChanges} call
    */
   public AddressCollection getLeavers() {
      synchronized (lock) {
         return leavers;
      }
   }

   /**
    * @return true if {@code createPendingView} has been called without a pair {@code resetChanges}
    */
   public boolean isViewInstallationInProgress() {
      return viewInstallationInProgress;
   }

   /**
    * @return the id of the view we created last (or received via {@code updateLatestViewId}.
    */
   public int getLastViewId() {
      synchronized (lock) {
         return lastViewId;
      }
   }
}
