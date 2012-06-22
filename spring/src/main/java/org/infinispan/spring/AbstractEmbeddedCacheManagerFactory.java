/**
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other
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

package org.infinispan.spring;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.infinispan.config.CacheLoaderManagerConfig;
import org.infinispan.config.ConfigurationException;
import org.infinispan.config.CustomInterceptorConfig;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionThreadPolicy;
import org.infinispan.jmx.MBeanServerLookup;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.lookup.TransactionManagerLookup;
import org.infinispan.util.concurrent.IsolationLevel;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.springframework.core.io.Resource;

/**
 * <p>
 * An abstract base class for factories creating cache managers that are backed by an
 * EmbeddedCacheManager.
 * </p>
 * 
 * @author <a href="mailto:olaf DOT bergner AT gmx DOT de">Olaf Bergner</a>
 * @author Marius Bogoevici
 */
public class AbstractEmbeddedCacheManagerFactory {

   protected static final Log logger = LogFactory.getLog(AbstractEmbeddedCacheManagerFactory.class);

   private Resource configurationFileLocation;

   protected final GlobalConfigurationOverrides globalConfigurationOverrides = new GlobalConfigurationOverrides();

   protected final ConfigurationOverrides configurationOverrides = new ConfigurationOverrides();

   // ------------------------------------------------------------------------
   // Create fully configured EmbeddedCacheManager instance
   // ------------------------------------------------------------------------

   protected EmbeddedCacheManager createBackingEmbeddedCacheManager() throws ConfigurationException, IOException {
      final GlobalConfigurationBuilder globalCfgBuilder = new GlobalConfigurationBuilder();
      final ConfigurationBuilder cacheCfgBuilder = new ConfigurationBuilder();

      this.globalConfigurationOverrides.applyOverridesTo(globalCfgBuilder);
      this.configurationOverrides.applyOverridesTo(cacheCfgBuilder);

      final EmbeddedCacheManager nativeEmbeddedCacheManager = createCacheManager(globalCfgBuilder, cacheCfgBuilder);

      return nativeEmbeddedCacheManager;
   }

   protected EmbeddedCacheManager createCacheManager(GlobalConfigurationBuilder globalBuilder, ConfigurationBuilder builder) {
      return new DefaultCacheManager(globalBuilder.build(), builder.build());
   }

   // ------------------------------------------------------------------------
   // Setter for location of configuration file
   // ------------------------------------------------------------------------

   /**
    * <p>
    * Sets the {@link org.springframework.core.io.Resource <code>location</code>} of the
    * configuration file which will be used to configure the
    * {@link org.infinispan.manager.EmbeddedCacheManager <code>EmbeddedCacheManager</code>} the
    * {@link org.infinispan.spring.provider.SpringEmbeddedCacheManager
    * <code>SpringEmbeddedCacheManager</code>} created by this <code>FactoryBean</code> delegates
    * to. If no location is supplied, <tt>Infinispan</tt>'s default configuration will be used.
    * </p>
    * <p>
    * Note that configuration settings defined via using explicit setters exposed by this
    * <code>FactoryBean</code> take precedence over those defined in the configuration file pointed
    * to by <code>configurationFileLocation</code>.
    * </p>
    * 
    * @param configurationFileLocation
    *           The {@link org.springframework.core.io.Resource <code>location</code>} of the
    *           configuration file which will be used to configure the
    *           {@link org.infinispan.manager.EmbeddedCacheManager
    *           <code>EmbeddedCacheManager</code>} the
    *           {@link org.infinispan.spring.provider.SpringEmbeddedCacheManager
    *           <code>SpringEmbeddedCacheManager</code>} created by this <code>FactoryBean</code>
    *           delegates to
    */
   public void setConfigurationFileLocation(final Resource configurationFileLocation) {
      this.configurationFileLocation = configurationFileLocation;
   }

   // ------------------------------------------------------------------------
   // Setters for GlobalConfiguration properties
   // ------------------------------------------------------------------------

   /**
    * @param exposeGlobalJmxStatistics
    * @see org.infinispan.config.GlobalConfiguration#setExposeGlobalJmxStatistics(boolean)
    */
   public void setExposeGlobalJmxStatistics(final boolean exposeGlobalJmxStatistics) {
      this.globalConfigurationOverrides.exposeGlobalJmxStatistics = exposeGlobalJmxStatistics;
   }

   /**
    * @param jmxObjectName
    * @see org.infinispan.config.GlobalConfiguration#setJmxDomain(java.lang.String)
    */
   public void setJmxDomain(final String jmxObjectName) {
      this.globalConfigurationOverrides.jmxDomain = jmxObjectName;
   }

   /**
    * @param properties
    * @see org.infinispan.config.GlobalConfiguration#setMBeanServerProperties(java.util.Properties)
    */
   public void setMBeanServerProperties(final Properties properties) {
      this.globalConfigurationOverrides.mBeanServerProperties = properties;
   }

   /**
    * @param mBeanServerLookupClass
    * @see org.infinispan.config.GlobalConfiguration#setMBeanServerLookup(java.lang.String)
    */
   public void setMBeanServerLookupClass(final String mBeanServerLookupClass) {
      this.globalConfigurationOverrides.mBeanServerLookupClass = mBeanServerLookupClass;
   }

   /**
    * @param mBeanServerLookup
    * @see org.infinispan.config.GlobalConfiguration#setMBeanServerLookup(org.infinispan.jmx.MBeanServerLookup)
    */
   public void setMBeanServerLookup(final MBeanServerLookup mBeanServerLookup) {
      this.globalConfigurationOverrides.mBeanServerLookup = mBeanServerLookup;
   }

   /**
    * @param allowDuplicateDomains
    * @see org.infinispan.config.GlobalConfiguration#setAllowDuplicateDomains(boolean)
    */
   public void setAllowDuplicateDomains(final boolean allowDuplicateDomains) {
      this.globalConfigurationOverrides.allowDuplicateDomains = allowDuplicateDomains;
   }

   /**
    * @param cacheManagerName
    * @see org.infinispan.config.GlobalConfiguration#setCacheManagerName(java.lang.String)
    */
   public void setCacheManagerName(final String cacheManagerName) {
      this.globalConfigurationOverrides.cacheManagerName = cacheManagerName;
   }

   /**
    * @param strictPeerToPeer
    * @see org.infinispan.config.GlobalConfiguration#setStrictPeerToPeer(boolean)
    */
   public void setStrictPeerToPeer(final boolean strictPeerToPeer) {
      this.globalConfigurationOverrides.strictPeerToPeer = strictPeerToPeer;
   }

   /**
    * @param asyncListenerExecutorFactoryClass
    * @see org.infinispan.config.GlobalConfiguration#setAsyncListenerExecutorFactoryClass(java.lang.String)
    */
   public void setAsyncListenerExecutorFactoryClass(final String asyncListenerExecutorFactoryClass) {
      this.globalConfigurationOverrides.asyncListenerExecutorFactoryClass = asyncListenerExecutorFactoryClass;
   }

   /**
    * @param asyncTransportExecutorFactoryClass
    * @see org.infinispan.config.GlobalConfiguration#setAsyncTransportExecutorFactoryClass(java.lang.String)
    */
   public void setAsyncTransportExecutorFactoryClass(final String asyncTransportExecutorFactoryClass) {
      this.globalConfigurationOverrides.asyncTransportExecutorFactoryClass = asyncTransportExecutorFactoryClass;
   }

   /**
    * @param evictionScheduledExecutorFactoryClass
    * @see org.infinispan.config.GlobalConfiguration#setEvictionScheduledExecutorFactoryClass(java.lang.String)
    */
   public void setEvictionScheduledExecutorFactoryClass(
            final String evictionScheduledExecutorFactoryClass) {
      this.globalConfigurationOverrides.evictionScheduledExecutorFactoryClass = evictionScheduledExecutorFactoryClass;
   }

   /**
    * @param replicationQueueScheduledExecutorFactoryClass
    * @see org.infinispan.config.GlobalConfiguration#setReplicationQueueScheduledExecutorFactoryClass(java.lang.String)
    */
   public void setReplicationQueueScheduledExecutorFactoryClass(
            final String replicationQueueScheduledExecutorFactoryClass) {
      this.globalConfigurationOverrides.replicationQueueScheduledExecutorFactoryClass = replicationQueueScheduledExecutorFactoryClass;
   }

   /**
    * @param marshallerClass
    * @see org.infinispan.config.GlobalConfiguration#setMarshallerClass(java.lang.String)
    */
   public void setMarshallerClass(final String marshallerClass) {
      this.globalConfigurationOverrides.marshallerClass = marshallerClass;
   }

   /**
    * @param nodeName
    * @see org.infinispan.config.GlobalConfiguration#setTransportNodeName(java.lang.String)
    */
   public void setTransportNodeName(final String nodeName) {
      this.globalConfigurationOverrides.transportNodeName = nodeName;
   }

   /**
    * @param transportClass
    * @see org.infinispan.config.GlobalConfiguration#setTransportClass(java.lang.String)
    */
   public void setTransportClass(final String transportClass) {
      this.globalConfigurationOverrides.transportClass = transportClass;
   }

   /**
    * @param transportProperties
    * @see org.infinispan.config.GlobalConfiguration#setTransportProperties(java.util.Properties)
    */
   public void setTransportProperties(final Properties transportProperties) {
      this.globalConfigurationOverrides.transportProperties = transportProperties;
   }

   /**
    * @param clusterName
    * @see org.infinispan.config.GlobalConfiguration#setClusterName(java.lang.String)
    */
   public void setClusterName(final String clusterName) {
      this.globalConfigurationOverrides.clusterName = clusterName;
   }

   /**
    * @param machineId
    * @see org.infinispan.config.GlobalConfiguration#setMachineId(java.lang.String)
    */
   public void setMachineId(final String machineId) {
      this.globalConfigurationOverrides.machineId = machineId;
   }

   /**
    * @param rackId
    * @see org.infinispan.config.GlobalConfiguration#setRackId(java.lang.String)
    */
   public void setRackId(final String rackId) {
      this.globalConfigurationOverrides.rackId = rackId;
   }

   /**
    * @param siteId
    * @see org.infinispan.config.GlobalConfiguration#setSiteId(java.lang.String)
    */
   public void setSiteId(final String siteId) {
      this.globalConfigurationOverrides.siteId = siteId;
   }

   /**
    * @param shutdownHookBehavior
    * @see org.infinispan.config.GlobalConfiguration#setShutdownHookBehavior(java.lang.String)
    */
   public void setShutdownHookBehavior(final String shutdownHookBehavior) {
      this.globalConfigurationOverrides.shutdownHookBehavior = shutdownHookBehavior;
   }

   /**
    * @param asyncListenerExecutorProperties
    * @see org.infinispan.config.GlobalConfiguration#setAsyncListenerExecutorProperties(java.util.Properties)
    */
   public void setAsyncListenerExecutorProperties(final Properties asyncListenerExecutorProperties) {
      this.globalConfigurationOverrides.asyncListenerExecutorProperties = asyncListenerExecutorProperties;
   }

   /**
    * @param asyncTransportExecutorProperties
    * @see org.infinispan.config.GlobalConfiguration#setAsyncTransportExecutorProperties(java.util.Properties)
    */
   public void setAsyncTransportExecutorProperties(final Properties asyncTransportExecutorProperties) {
      this.globalConfigurationOverrides.asyncTransportExecutorProperties = asyncTransportExecutorProperties;
   }

   /**
    * @param evictionScheduledExecutorProperties
    * @see org.infinispan.config.GlobalConfiguration#setEvictionScheduledExecutorProperties(java.util.Properties)
    */
   public void setEvictionScheduledExecutorProperties(
            final Properties evictionScheduledExecutorProperties) {
      this.globalConfigurationOverrides.evictionScheduledExecutorProperties = evictionScheduledExecutorProperties;
   }

   /**
    * @param replicationQueueScheduledExecutorProperties
    * @see org.infinispan.config.GlobalConfiguration#setReplicationQueueScheduledExecutorProperties(java.util.Properties)
    */
   public void setReplicationQueueScheduledExecutorProperties(
            final Properties replicationQueueScheduledExecutorProperties) {
      this.globalConfigurationOverrides.replicationQueueScheduledExecutorProperties = replicationQueueScheduledExecutorProperties;
   }

   /**
    * @param marshallVersion
    * @see org.infinispan.config.GlobalConfiguration#setMarshallVersion(short)
    */
   public void setMarshallVersion(final short marshallVersion) {
      this.globalConfigurationOverrides.marshallVersion = marshallVersion;
   }

   /**
    * @param distributedSyncTimeout
    * @see org.infinispan.config.GlobalConfiguration#setDistributedSyncTimeout(long)
    */
   public void setDistributedSyncTimeout(final long distributedSyncTimeout) {
      this.globalConfigurationOverrides.distributedSyncTimeout = distributedSyncTimeout;
   }

   // ------------------------------------------------------------------------
   // Setters for Configuration
   // ------------------------------------------------------------------------

   /**
    * @param eagerDeadlockSpinDuration
    * @see org.infinispan.spring.ConfigurationOverrides#setDeadlockDetectionSpinDuration(java.lang.Long)
    */
   public void setDeadlockDetectionSpinDuration(final Long eagerDeadlockSpinDuration) {
      this.configurationOverrides.setDeadlockDetectionSpinDuration(eagerDeadlockSpinDuration);
   }

   /**
    * @param useEagerDeadlockDetection
    * @see org.infinispan.spring.ConfigurationOverrides#setEnableDeadlockDetection(java.lang.Boolean)
    */
   public void setEnableDeadlockDetection(final Boolean useEagerDeadlockDetection) {
      this.configurationOverrides.setEnableDeadlockDetection(useEagerDeadlockDetection);
   }

   /**
    * @param useLockStriping
    * @see org.infinispan.spring.ConfigurationOverrides#setUseLockStriping(java.lang.Boolean)
    */
   public void setUseLockStriping(final Boolean useLockStriping) {
      this.configurationOverrides.setUseLockStriping(useLockStriping);
   }

   /**
    * @param unsafeUnreliableReturnValues
    * @see org.infinispan.spring.ConfigurationOverrides#setUnsafeUnreliableReturnValues(java.lang.Boolean)
    */
   public void setUnsafeUnreliableReturnValues(final Boolean unsafeUnreliableReturnValues) {
      this.configurationOverrides.setUnsafeUnreliableReturnValues(unsafeUnreliableReturnValues);
   }

   /**
    * @param rehashRpcTimeout
    * @see org.infinispan.spring.ConfigurationOverrides#setRehashRpcTimeout(java.lang.Long)
    */
   public void setRehashRpcTimeout(final Long rehashRpcTimeout) {
      this.configurationOverrides.setRehashRpcTimeout(rehashRpcTimeout);
   }

   /**
    * @param writeSkewCheck
    * @see org.infinispan.spring.ConfigurationOverrides#setWriteSkewCheck(java.lang.Boolean)
    */
   public void setWriteSkewCheck(final Boolean writeSkewCheck) {
      this.configurationOverrides.setWriteSkewCheck(writeSkewCheck);
   }

   /**
    * @param concurrencyLevel
    * @see org.infinispan.spring.ConfigurationOverrides#setConcurrencyLevel(java.lang.Integer)
    */
   public void setConcurrencyLevel(final Integer concurrencyLevel) {
      this.configurationOverrides.setConcurrencyLevel(concurrencyLevel);
   }

   /**
    * @param replQueueMaxElements
    * @see org.infinispan.spring.ConfigurationOverrides#setReplQueueMaxElements(java.lang.Integer)
    */
   public void setReplQueueMaxElements(final Integer replQueueMaxElements) {
      this.configurationOverrides.setReplQueueMaxElements(replQueueMaxElements);
   }

   /**
    * @param replQueueInterval
    * @see org.infinispan.spring.ConfigurationOverrides#setReplQueueInterval(java.lang.Long)
    */
   public void setReplQueueInterval(final Long replQueueInterval) {
      this.configurationOverrides.setReplQueueInterval(replQueueInterval);
   }

   /**
    * @param replQueueClass
    * @see org.infinispan.spring.ConfigurationOverrides#setReplQueueClass(java.lang.String)
    */
   public void setReplQueueClass(final String replQueueClass) {
      this.configurationOverrides.setReplQueueClass(replQueueClass);
   }

   /**
    * @param exposeJmxStatistics
    * @see org.infinispan.spring.ConfigurationOverrides#setExposeJmxStatistics(java.lang.Boolean)
    */
   public void setExposeJmxStatistics(final Boolean exposeJmxStatistics) {
      this.configurationOverrides.setExposeJmxStatistics(exposeJmxStatistics);
   }

   /**
    * @param invocationBatchingEnabled
    * @see org.infinispan.spring.ConfigurationOverrides#setInvocationBatchingEnabled(java.lang.Boolean)
    */
   public void setInvocationBatchingEnabled(final Boolean invocationBatchingEnabled) {
      this.configurationOverrides.setInvocationBatchingEnabled(invocationBatchingEnabled);
   }

   /**
    * @param fetchInMemoryState
    * @see org.infinispan.spring.ConfigurationOverrides#setFetchInMemoryState(java.lang.Boolean)
    */
   public void setFetchInMemoryState(final Boolean fetchInMemoryState) {
      this.configurationOverrides.setFetchInMemoryState(fetchInMemoryState);
   }

   /**
    * @param alwaysProvideInMemoryState
    * @see org.infinispan.spring.ConfigurationOverrides#setAlwaysProvideInMemoryState(java.lang.Boolean)
    */
   public void setAlwaysProvideInMemoryState(final Boolean alwaysProvideInMemoryState) {
      this.configurationOverrides.setAlwaysProvideInMemoryState(alwaysProvideInMemoryState);
   }

   /**
    * @param lockAcquisitionTimeout
    * @see org.infinispan.spring.ConfigurationOverrides#setLockAcquisitionTimeout(java.lang.Long)
    */
   public void setLockAcquisitionTimeout(final Long lockAcquisitionTimeout) {
      this.configurationOverrides.setLockAcquisitionTimeout(lockAcquisitionTimeout);
   }

   /**
    * @param syncReplTimeout
    * @see org.infinispan.spring.ConfigurationOverrides#setSyncReplTimeout(java.lang.Long)
    */
   public void setSyncReplTimeout(final Long syncReplTimeout) {
      this.configurationOverrides.setSyncReplTimeout(syncReplTimeout);
   }

   /**
    * @param cacheModeString
    * @see org.infinispan.spring.ConfigurationOverrides#setCacheModeString(java.lang.String)
    */
   public void setCacheModeString(final String cacheModeString) {
      this.configurationOverrides.setCacheModeString(cacheModeString);
   }

   /**
    * @param expirationWakeUpInterval
    * @see org.infinispan.spring.ConfigurationOverrides#setExpirationWakeUpInterval(Long) (java.lang.Long)
    */
   public void setExpirationWakeUpInterval(final Long expirationWakeUpInterval) {
      this.configurationOverrides.setExpirationWakeUpInterval(expirationWakeUpInterval);
   }

   /**
    * @param evictionStrategy
    * @see org.infinispan.spring.ConfigurationOverrides#setEvictionStrategy(org.infinispan.eviction.EvictionStrategy)
    */
   public void setEvictionStrategy(final EvictionStrategy evictionStrategy) {
      this.configurationOverrides.setEvictionStrategy(evictionStrategy);
   }

   /**
    * @param evictionStrategyClass
    * @see org.infinispan.spring.ConfigurationOverrides#setEvictionStrategyClass(java.lang.String)
    */
   public void setEvictionStrategyClass(final String evictionStrategyClass) {
      this.configurationOverrides.setEvictionStrategyClass(evictionStrategyClass);
   }

   /**
    * @param evictionThreadPolicy
    * @see org.infinispan.spring.ConfigurationOverrides#setEvictionThreadPolicy(org.infinispan.eviction.EvictionThreadPolicy)
    */
   public void setEvictionThreadPolicy(final EvictionThreadPolicy evictionThreadPolicy) {
      this.configurationOverrides.setEvictionThreadPolicy(evictionThreadPolicy);
   }

   /**
    * @param evictionThreadPolicyClass
    * @see org.infinispan.spring.ConfigurationOverrides#setEvictionThreadPolicyClass(java.lang.String)
    */
   public void setEvictionThreadPolicyClass(final String evictionThreadPolicyClass) {
      this.configurationOverrides.setEvictionThreadPolicyClass(evictionThreadPolicyClass);
   }

   /**
    * @param evictionMaxEntries
    * @see org.infinispan.spring.ConfigurationOverrides#setEvictionMaxEntries(java.lang.Integer)
    */
   public void setEvictionMaxEntries(final Integer evictionMaxEntries) {
      this.configurationOverrides.setEvictionMaxEntries(evictionMaxEntries);
   }

   /**
    * @param expirationLifespan
    * @see org.infinispan.spring.ConfigurationOverrides#setExpirationLifespan(java.lang.Long)
    */
   public void setExpirationLifespan(final Long expirationLifespan) {
      this.configurationOverrides.setExpirationLifespan(expirationLifespan);
   }

   /**
    * @param expirationMaxIdle
    * @see org.infinispan.spring.ConfigurationOverrides#setExpirationMaxIdle(java.lang.Long)
    */
   public void setExpirationMaxIdle(final Long expirationMaxIdle) {
      this.configurationOverrides.setExpirationMaxIdle(expirationMaxIdle);
   }

   /**
    * @param transactionManagerLookupClass
    * @see org.infinispan.spring.ConfigurationOverrides#setTransactionManagerLookupClass(java.lang.String)
    */
   public void setTransactionManagerLookupClass(final String transactionManagerLookupClass) {
      this.configurationOverrides.setTransactionManagerLookupClass(transactionManagerLookupClass);
   }

   /**
    * @param transactionManagerLookup
    * @see org.infinispan.spring.ConfigurationOverrides#setTransactionManagerLookup(org.infinispan.transaction.lookup.TransactionManagerLookup)
    */
   public void setTransactionManagerLookup(final TransactionManagerLookup transactionManagerLookup) {
      this.configurationOverrides.setTransactionManagerLookup(transactionManagerLookup);
   }

   /**
    * @param cacheLoaderManagerConfig
    * @see org.infinispan.spring.ConfigurationOverrides#setCacheLoaderManagerConfig(org.infinispan.config.CacheLoaderManagerConfig)
    */
   public void setCacheLoaderManagerConfig(final CacheLoaderManagerConfig cacheLoaderManagerConfig) {
      this.configurationOverrides.setCacheLoaderManagerConfig(cacheLoaderManagerConfig);
   }

   /**
    * @param syncCommitPhase
    * @see org.infinispan.spring.ConfigurationOverrides#setSyncCommitPhase(java.lang.Boolean)
    */
   public void setSyncCommitPhase(final Boolean syncCommitPhase) {
      this.configurationOverrides.setSyncCommitPhase(syncCommitPhase);
   }

   /**
    * @param syncRollbackPhase
    * @see org.infinispan.spring.ConfigurationOverrides#setSyncRollbackPhase(java.lang.Boolean)
    */
   public void setSyncRollbackPhase(final Boolean syncRollbackPhase) {
      this.configurationOverrides.setSyncRollbackPhase(syncRollbackPhase);
   }

   /**
    * @param useEagerLocking
    * @see org.infinispan.spring.ConfigurationOverrides#setUseEagerLocking(java.lang.Boolean)
    */
   public void setUseEagerLocking(final Boolean useEagerLocking) {
      this.configurationOverrides.setUseEagerLocking(useEagerLocking);
   }

   /**
    * @param eagerLockSingleNode
    * @see org.infinispan.spring.ConfigurationOverrides#setEagerLockSingleNode(java.lang.Boolean)
    */
   @Deprecated
   public void setEagerLockSingleNode(final Boolean eagerLockSingleNode) {
   }

   /**
    * @param useReplQueue
    * @see org.infinispan.spring.ConfigurationOverrides#setUseReplQueue(java.lang.Boolean)
    */
   public void setUseReplQueue(final Boolean useReplQueue) {
      this.configurationOverrides.setUseReplQueue(useReplQueue);
   }

   /**
    * @param isolationLevel
    * @see org.infinispan.spring.ConfigurationOverrides#setIsolationLevel(org.infinispan.util.concurrent.IsolationLevel)
    */
   public void setIsolationLevel(final IsolationLevel isolationLevel) {
      this.configurationOverrides.setIsolationLevel(isolationLevel);
   }

   /**
    * @param stateRetrievalTimeout
    * @see org.infinispan.spring.ConfigurationOverrides#setStateRetrievalTimeout(java.lang.Long)
    */
   public void setStateRetrievalTimeout(final Long stateRetrievalTimeout) {
      this.configurationOverrides.setStateRetrievalTimeout(stateRetrievalTimeout);
   }

   /**
    * @param stateRetrievalChunkSize
    * @see org.infinispan.spring.ConfigurationOverrides#setStateRetrievalRetryWaitTimeIncreaseFactor(java.lang.Integer)
    */
   public void setStateRetrievalChunkSize(
         final Integer stateRetrievalChunkSize) {
      this.configurationOverrides
            .setStateRetrievalChunkSize(stateRetrievalChunkSize);
   }

   /**
    * @param isolationLevelClass
    * @see org.infinispan.spring.ConfigurationOverrides#setIsolationLevelClass(java.lang.String)
    */
   public void setIsolationLevelClass(final String isolationLevelClass) {
      this.configurationOverrides.setIsolationLevelClass(isolationLevelClass);
   }

   /**
    * @param useLazyDeserialization
    * @see org.infinispan.spring.ConfigurationOverrides#setUseLazyDeserialization(java.lang.Boolean)
    */
   public void setUseLazyDeserialization(final Boolean useLazyDeserialization) {
      this.configurationOverrides.setUseLazyDeserialization(useLazyDeserialization);
   }

   /**
    * @param l1CacheEnabled
    * @see org.infinispan.spring.ConfigurationOverrides#setL1CacheEnabled(java.lang.Boolean)
    */
   public void setL1CacheEnabled(final Boolean l1CacheEnabled) {
      this.configurationOverrides.setL1CacheEnabled(l1CacheEnabled);
   }

   /**
    * @param l1Lifespan
    * @see org.infinispan.spring.ConfigurationOverrides#setL1Lifespan(java.lang.Long)
    */
   public void setL1Lifespan(final Long l1Lifespan) {
      this.configurationOverrides.setL1Lifespan(l1Lifespan);
   }

   /**
    * @param l1OnRehash
    * @see org.infinispan.spring.ConfigurationOverrides#setL1OnRehash(java.lang.Boolean)
    */
   public void setL1OnRehash(final Boolean l1OnRehash) {
      this.configurationOverrides.setL1OnRehash(l1OnRehash);
   }

   /**
    * @param consistentHashClass
    * @see org.infinispan.spring.ConfigurationOverrides#setConsistentHashClass(java.lang.String)
    */
   public void setConsistentHashClass(final String consistentHashClass) {
      this.configurationOverrides.setConsistentHashClass(consistentHashClass);
   }

   /**
    * @param numOwners
    * @see org.infinispan.spring.ConfigurationOverrides#setNumOwners(java.lang.Integer)
    */
   public void setNumOwners(final Integer numOwners) {
      this.configurationOverrides.setNumOwners(numOwners);
   }

   /**
    * @param rehashEnabled
    * @see org.infinispan.spring.ConfigurationOverrides#setRehashEnabled(java.lang.Boolean)
    */
   public void setRehashEnabled(final Boolean rehashEnabled) {
      this.configurationOverrides.setRehashEnabled(rehashEnabled);
   }

   /**
    * @param rehashWaitTime
    * @see org.infinispan.spring.ConfigurationOverrides#setRehashWaitTime(java.lang.Long)
    */
   public void setRehashWaitTime(final Long rehashWaitTime) {
      this.configurationOverrides.setRehashWaitTime(rehashWaitTime);
   }

   /**
    * @param useAsyncMarshalling
    * @see org.infinispan.spring.ConfigurationOverrides#setUseAsyncMarshalling(java.lang.Boolean)
    */
   public void setUseAsyncMarshalling(final Boolean useAsyncMarshalling) {
      this.configurationOverrides.setUseAsyncMarshalling(useAsyncMarshalling);
   }

   /**
    * @param indexingEnabled
    * @see org.infinispan.spring.ConfigurationOverrides#setIndexingEnabled(java.lang.Boolean)
    */
   public void setIndexingEnabled(final Boolean indexingEnabled) {
      this.configurationOverrides.setIndexingEnabled(indexingEnabled);
   }

   /**
    * @param indexLocalOnly
    * @see org.infinispan.spring.ConfigurationOverrides#setIndexLocalOnly(java.lang.Boolean)
    */
   public void setIndexLocalOnly(final Boolean indexLocalOnly) {
      this.configurationOverrides.setIndexLocalOnly(indexLocalOnly);
   }

   /**
    * @param customInterceptors
    * @see org.infinispan.spring.ConfigurationOverrides#setCustomInterceptors(java.util.List)
    */
   public void setCustomInterceptors(final List<CustomInterceptorConfig> customInterceptors) {
      this.configurationOverrides.setCustomInterceptors(customInterceptors);
   }

   // ------------------------------------------------------------------------
   // Helper classes
   // ------------------------------------------------------------------------

   protected static final class GlobalConfigurationOverrides {

      private final Log logger = LogFactory.getLog(getClass());

      private Boolean exposeGlobalJmxStatistics;

      private Properties mBeanServerProperties;

      private String jmxDomain;

      private String mBeanServerLookupClass;

      private MBeanServerLookup mBeanServerLookup;

      private Boolean allowDuplicateDomains;

      private String cacheManagerName;

      private String clusterName;

      private String machineId;

      private String rackId;

      private String siteId;

      private Boolean strictPeerToPeer;

      private Long distributedSyncTimeout;

      private String transportClass;

      private String transportNodeName;

      private String asyncListenerExecutorFactoryClass;

      private String asyncTransportExecutorFactoryClass;

      private String evictionScheduledExecutorFactoryClass;

      private String replicationQueueScheduledExecutorFactoryClass;

      private String marshallerClass;

      private Properties transportProperties;

      private String shutdownHookBehavior;

      private Properties asyncListenerExecutorProperties;

      private Properties asyncTransportExecutorProperties;

      private Properties evictionScheduledExecutorProperties;

      private Properties replicationQueueScheduledExecutorProperties;

      private Short marshallVersion;

      public void applyOverridesTo(final GlobalConfigurationBuilder cfg) {
         logger.debug("Applying configuration overrides to GlobalConfiguration [" + cfg + "] ...");

         if (this.exposeGlobalJmxStatistics != null) {
            logger.debug("Overriding property [exposeGlobalJmxStatistics] with new value [" + this.exposeGlobalJmxStatistics + "]");
            cfg.globalJmxStatistics().enabled(this.exposeGlobalJmxStatistics);
         }
         if (this.mBeanServerProperties != null) {
            logger.debug("Overriding property [mBeanServerProperties] with new value [" + this.mBeanServerProperties + "]");
            cfg.globalJmxStatistics().withProperties(this.mBeanServerProperties);
         }
         if (this.jmxDomain != null) {
            logger.debug("Overriding property [jmxDomain] with new value [" + this.jmxDomain + "]");
            cfg.globalJmxStatistics().jmxDomain(this.jmxDomain);
         }
         if (this.mBeanServerLookupClass != null) {
            logger.debug("Overriding property [mBeanServerLookupClass] with new value [" + this.mBeanServerLookupClass + "]");
            //cfg.globalJmxStatistics()...(this.mBeanServerLookupClass);//FIXME
         }
         if (this.mBeanServerLookup != null) {
            logger.debug("Overriding property [mBeanServerLookup] with new value [" + this.mBeanServerLookup + "]");
            cfg.globalJmxStatistics().mBeanServerLookup(this.mBeanServerLookup);
         }
         if (this.allowDuplicateDomains != null) {
            logger.debug("Overriding property [allowDuplicateDomains] with new value [" + this.allowDuplicateDomains + "]");
            cfg.globalJmxStatistics().allowDuplicateDomains(this.allowDuplicateDomains);
         }
         if (this.cacheManagerName != null) {
            logger.debug("Overriding property [cacheManagerName] with new value [" + this.cacheManagerName + "]");
            cfg.globalJmxStatistics().cacheManagerName(this.cacheManagerName);
         }
         if (this.clusterName != null) {
            logger.debug("Overriding property [clusterName] with new value [" + this.clusterName + "]");
            cfg.transport().clusterName(this.clusterName);
         }
         if (this.machineId != null) {
            logger.debug("Overriding property [machineId] with new value [" + this.machineId + "]");
            cfg.transport().machineId(this.machineId);
         }
         if (this.rackId != null) {
            logger.debug("Overriding property [rackId] with new value [" + this.rackId + "]");
            cfg.transport().rackId(this.rackId);
         }
         if (this.siteId != null) {
            logger.debug("Overriding property [siteId] with new value [" + this.siteId + "]");
            cfg.transport().siteId(this.siteId);
         }
         if (this.strictPeerToPeer != null) {
            logger.debug("Overriding property [strictPeerToPeer] with new value [" + this.strictPeerToPeer + "]");
            cfg.transport().strictPeerToPeer(this.strictPeerToPeer);
         }
         if (this.distributedSyncTimeout != null) {
            logger.debug("Overriding property [distributedSyncTimeout] with new value [" + this.distributedSyncTimeout + "]");
            cfg.transport().distributedSyncTimeout(this.distributedSyncTimeout);
         }
         if (this.transportClass != null) {
            logger.debug("Overriding property [transportClass] with new value [" + this.transportClass + "]");
            //cfg.transport()..setTransportClass(this.transportClass); //FIXME
         }
         if (this.transportNodeName != null) {
            logger.debug("Overriding property [transportNodeName] with new value [" + this.transportNodeName + "]");
            cfg.transport().nodeName(this.transportNodeName);
         }
         if (this.asyncListenerExecutorFactoryClass != null) {
            logger.debug("Overriding property [asyncListenerExecutorFactoryClass] with new value [" + this.asyncListenerExecutorFactoryClass + "]");
            //cfg.asyncListenerExecutor(). .setAsyncListenerExecutorFactoryClass(this.asyncListenerExecutorFactoryClass);//FIXME
         }
         if (this.asyncTransportExecutorFactoryClass != null) {
            logger.debug("Overriding property [asyncTransportExecutorFactoryClass] with new value [" + this.asyncTransportExecutorFactoryClass + "]");
            //cfg.setAsyncTransportExecutorFactoryClass(this.asyncTransportExecutorFactoryClass);//FIXME
         }
         if (this.evictionScheduledExecutorFactoryClass != null) {
            logger.debug("Overriding property [evictionScheduledExecutorFactoryClass] with new value [" + this.evictionScheduledExecutorFactoryClass + "]");
            //cfg.setEvictionScheduledExecutorFactoryClass(this.evictionScheduledExecutorFactoryClass);//FIXME
         }
         if (this.replicationQueueScheduledExecutorFactoryClass != null) {
            logger.debug("Overriding property [replicationQueueScheduledExecutorFactoryClass] with new value [" + this.replicationQueueScheduledExecutorFactoryClass + "]");
            //cfg.setReplicationQueueScheduledExecutorFactoryClass(this.replicationQueueScheduledExecutorFactoryClass);//FIXME
         }
         if (this.marshallerClass != null) {
            logger.debug("Overriding property [marshallerClass] with new value [" + this.marshallerClass + "]");
            //cfg.serialization().marshaller(marshallerClass);//FIXME
         }
         if (this.transportProperties != null) {
            logger.debug("Overriding property [transportProperties] with new value [" + this.transportProperties + "]");
            cfg.transport().withProperties(this.transportProperties);
         }
         if (this.shutdownHookBehavior != null) {
            logger.debug("Overriding property [shutdownHookBehavior] with new value [" + this.shutdownHookBehavior + "]");
            //cfg.shutdown().hookBehavior(shutdownHookBehavior);//FIXME needs parser
         }
         if (this.asyncListenerExecutorProperties != null) {
            logger.debug("Overriding property [asyncListenerExecutorProperties] with new value [" + this.asyncListenerExecutorProperties + "]");
            cfg.asyncListenerExecutor().withProperties(this.asyncListenerExecutorProperties);
         }
         if (this.asyncTransportExecutorProperties != null) {
            logger.debug("Overriding property [asyncTransportExecutorProperties] with new value [" + this.asyncTransportExecutorProperties + "]");
            cfg.asyncTransportExecutor().withProperties(this.asyncTransportExecutorProperties);
         }
         if (this.evictionScheduledExecutorProperties != null) {
            logger.debug("Overriding property [evictionScheduledExecutorProperties] with new value [" + this.evictionScheduledExecutorProperties + "]");
            cfg.evictionScheduledExecutor().withProperties(this.evictionScheduledExecutorProperties);
         }
         if (this.replicationQueueScheduledExecutorProperties != null) {
            logger.debug("Overriding property [replicationQueueScheduledExecutorProperties] with new value ["
                  + this.replicationQueueScheduledExecutorProperties + "]");
            cfg.replicationQueueScheduledExecutor().withProperties(this.replicationQueueScheduledExecutorProperties);
         }
         if (this.marshallVersion != null) {
            logger.debug("Overriding property [marshallVersion] with new value [" + this.marshallVersion + "]");
            cfg.serialization().version(this.marshallVersion);
         }

         logger.debug("Finished applying configuration overrides to GlobalConfiguration [" + cfg + "]");
      }
   }
}
