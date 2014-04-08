package org.infinispan.query.impl.externalizers;

/**
 * Identifiers used by the Marshaller to delegate to specialized Externalizers.
 * For details, read http://infinispan.org/docs/7.0.x/user_guide/user_guide.html#_preassigned_externalizer_id_ranges
 *
 * The range reserved for the Infinispan Query module is from 1600 to 1699.
 *
 * @author Sanne Grinovero
 * @since 7.0
 */
public interface ExternalizerIds {

   Integer FILTER_AND_CONVERTER = 1610;

   Integer LUCENE_QUERY_BOOLEAN = 1650;

   Integer LUCENE_QUERY_TERM = 1651;

   Integer LUCENE_TERM = 1640;

   Integer LUCENE_SORT = 1641;

   Integer LUCENE_SORT_FIELD = 1642;

   Integer LUCENE_TOPDOCS = 1643;

   Integer CLUSTERED_QUERY_TOPDOCS = 1644;

   Integer LUCENE_SCORE_DOC = 1645;

   Integer LUCENE_TOPFIELDDOCS = 1646;
   
   Integer LUCENE_FIELD_SCORE_DOC = 1647;

}
