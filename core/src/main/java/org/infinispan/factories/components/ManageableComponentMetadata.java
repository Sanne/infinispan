package org.infinispan.factories.components;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;

/**
 * A specialization of {@link ComponentMetadata}, this version also includes JMX related metadata, as expressed
 * by {@link MBean}, {@link ManagedAttribute} and {@link ManagedOperation} annotations.
 *
 * @author Manik Surtani
 * @deprecated Since 10.0, does not work as the annotations are not available at runtime
 */
@Deprecated
public class ManageableComponentMetadata extends ComponentMetadata {
   private static final long serialVersionUID = 0x11A17A6EAB1EL;
   private String jmxObjectName;
   private String description;
   private Set<JmxAttributeMetadata> attributeMetadata;
   private Set<JmxOperationMetadata> operationMetadata;

   public ManageableComponentMetadata(Class<?> component, List<Field> injectFields, List<Method> injectMethods, List<Method> startMethods, List<Method> postStartMethods, List<Method> stopMethods, boolean global, boolean survivesRestarts, List<Field> managedAttributeFields, List<Method> managedAttributeMethods, List<Method> managedOperationMethods, MBean mbean) {
      super(component, injectFields, injectMethods, startMethods, postStartMethods, stopMethods, global, survivesRestarts);
      if ((managedAttributeFields != null && !managedAttributeFields.isEmpty()) || (managedAttributeMethods != null && !managedAttributeMethods.isEmpty())) {
         attributeMetadata =  new HashSet<JmxAttributeMetadata>((managedAttributeFields == null ? 0 : managedAttributeFields.size()) + (managedAttributeMethods == null ? 0 : managedAttributeMethods.size()));

         if (managedAttributeFields != null) {
            for (Field f: managedAttributeFields) attributeMetadata.add(new JmxAttributeMetadata(f));
         }

         if (managedAttributeMethods != null) {
            for (Method m: managedAttributeMethods) attributeMetadata.add(new JmxAttributeMetadata(m));
         }
      }

      if (managedOperationMethods != null && !managedOperationMethods.isEmpty()) {
         operationMetadata = new HashSet<JmxOperationMetadata>(managedOperationMethods.size());
         for (Method m: managedOperationMethods) operationMetadata.add(new JmxOperationMetadata(m));
      }

      jmxObjectName = mbean.objectName();
      description = mbean.description();
   }

   public String getJmxObjectName() {
      return jmxObjectName;
   }

   public String getDescription() {
      return description;
   }

   public Set<JmxAttributeMetadata> getAttributeMetadata() {
      if (attributeMetadata == null) return Collections.emptySet();
      return attributeMetadata;
   }

   public Set<JmxOperationMetadata> getOperationMetadata() {
      if (operationMetadata == null) return Collections.emptySet();
      return operationMetadata;
   }

   @Override
   public boolean isManageable() {
      return true;
   }

   @Override
   public ManageableComponentMetadata toManageableComponentMetadata() {
      return this;
   }

   @Override
   public String toString() {
      return "ManageableComponentMetadata{" +
            "jmxObjectName='" + jmxObjectName + '\'' +
            ", description='" + description + '\'' +
            ", attributeMetadata=" + attributeMetadata +
            ", operationMetadata=" + operationMetadata +
            '}';
   }
}
