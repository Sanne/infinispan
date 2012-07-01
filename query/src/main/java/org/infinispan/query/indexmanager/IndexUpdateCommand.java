/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
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
package org.infinispan.query.indexmanager;

import org.infinispan.CacheException;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.commands.remote.BaseRpcCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.query.ModuleCommandIds;
import org.infinispan.query.SearchManager;

/**
* @author Sanne Grinovero
*/
public class IndexUpdateCommand extends BaseRpcCommand implements ReplicableCommand {

   /**
    * Being the first version, we only support version 1.
    */
   private static final Byte PROTOCOL_VERSION = Byte.valueOf((byte) 1);

   public static final byte COMMAND_ID = ModuleCommandIds.UPDATE_INDEX;

   private SearchManager searchManager;

   private byte[] serializedModel;

   private String indexName;

   public IndexUpdateCommand(String cacheName) {
      super(cacheName);
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      //FIXME implement me
      return Boolean.TRUE; //Return value to be ignored
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public Object[] getParameters() {
      return new Object[]{ PROTOCOL_VERSION, indexName, serializedModel };
   }

   @Override
   public void setParameters(int commandId, Object[] parameters) {
      Byte protoVersion = (Byte) parameters[0];
      if (PROTOCOL_VERSION.equals(protoVersion)) {
         this.indexName = (String) parameters[1];
         this.serializedModel = (byte[]) parameters[2];
      }
      else {
         throw new CacheException("Incompatible versions detected");
      }
   }

   @Override
   public boolean isReturnValueExpected() {
      return false;
   }

   /**
    * This is invoked only on the receiving node, before {@link #perform(InvocationContext)}
    */
   public void injectComponents(SearchManager searchManager) {
      this.searchManager = searchManager;
   }

   public void setSerializedWorkList(byte[] serializedModel) {
      this.serializedModel = serializedModel;
   }

   public void setIndexName(String indexName) {
      this.indexName = indexName;
   }

}
