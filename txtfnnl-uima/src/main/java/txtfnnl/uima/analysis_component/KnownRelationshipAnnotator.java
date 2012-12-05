package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FSStringConstraint;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeaturePath;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.resource.RelationshipStringMapResource;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * An AE that annotates {@link SemanticAnnotation} {@link Entity} instances in
 * {@link SentenceAnnotation}s as (potentially) describing a relation between
 * those entities by creating {@link RelationshipAnnotation}s between the
 * sentences and entities.
 * 
 * Note that the entities should have been annotated by the
 * {@link KnownEntityAnnotator} or at least obey the following requirements:
 * <ul>
 * <li>They must be {@link SemanticAnnotation} types</li>
 * <li>The :identifier feature must represent the Entity type value</li>
 * <li>The first :property value must hold the Entity's namespace value</li>
 * <li>The second :property value must hold the Entity's identifier value</li>
 * </ul>
 * 
 * Parameter settings:
 * <ul>
 * <li>String {@link #PARAM_RELATIONSHIP_NAMESPACE} (required)</li>
 * <li>String {@link #PARAM_ENTITY_NAMESPACE} (required)</li>
 * </ul>
 * Resources:
 * <dl>
 * <dt>KnownRelationships</dt>
 * <dd>a TSV file of known relationships</dd>
 * </dl>
 * The <b>KnownRelationships</b> resource has to be a TSV file with the
 * following columns:
 * <ol>
 * <li>Document ID: SOFA URI basename (without the file suffix)</li>
 * <li>Entity Type: will be used as the IDs of the SemanticAnnotations, using
 * the <i>Namespace<i> parameter of this Annotator as the base namespace for
 * all SemanticAnnotations</li>
 * <li>Namespace: of the entity, as used in the EntityNameDb (and not to be
 * confused with the <i>Namespace<i> parameter of this Annotator)</li>
 * <li>Identifier: of the entity, as used in the EntityNameDb</li>
 * </ol>
 * The last three column should be repeated as often as required to include
 * all entities part of the relationship. (Note that if only a single entity
 * is present, essentially all sentences that contain the entity will be
 * annotated by this AE.)
 * 
 * @author Florian Leitner
 */
public class KnownRelationshipAnnotator extends KnownEvidenceAnnotator<List<Set<Entity>>> {

	/** The URI of this Annotator. */
	public static final String URI = KnownRelationshipAnnotator.class.getName();

	public static final String PARAM_RELATIONSHIP_NAMESPACE = "RelationshipNamespace";
	@ConfigurationParameter(name = PARAM_RELATIONSHIP_NAMESPACE, mandatory = true)
	private String relationshipNamespace;

	/**
	 * The namespace that was used to annotate entities (as
	 * {@link SemanticAnnotation} :namespace features).
	 */
	public static final String PARAM_ENTITY_NAMESPACE = KnownEntityAnnotator.PARAM_NAMESPACE;
	@ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE, mandatory = true)
	private String entityNamespace;

	/**
	 * Remove sentence annotations that contain no relationship.
	 * 
	 * An optional flag indicating if sentence annotations without
	 * relationships should be removed from the CAS.
	 */
	public static final String PARAM_REMOVE_SENTENCE_ANNOTATIONS = "RemoveSentenceAnnotations";
	@ConfigurationParameter(name = PARAM_REMOVE_SENTENCE_ANNOTATIONS, defaultValue = "false")
	private boolean removeSentenceAnnotations;

	/**
	 * Configure an AE description for a pipeline.
	 * 
	 * @param namespace of the {@link SemanticAnnotation}s
	 * @param queries to use for fetching entity names from the JDBC-connected DB
	 * @param entityMap containing filename to entity type, namespace, and ID mappings
	 * @param dbUrl of the DB to connect to
	 * @param driverClass to use for connecting to the DB
	 * @param dbUsername to use for connecting to the DB
	 * @param dbPassword to use for connecting to the DB
	 * @return a configured AE description
	 * @throws ResourceInitializationException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final String entityNamespace,
	                                                  final String relationshipNamespace,
	                                                  File relationshipMap,
	                                                  final boolean removeSentenceAnnotations)
	        throws UIMAException, IOException {
		final ExternalResourceDescription evidenceMapResource = RelationshipStringMapResource
		    .configure("file:" + relationshipMap.getAbsolutePath());

		return AnalysisEngineFactory.createPrimitiveDescription(KnownRelationshipAnnotator.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(MODEL_KEY_EVIDENCE_STRING_MAP, evidenceMapResource);
				    put(PARAM_ENTITY_NAMESPACE, entityNamespace);
				    put(PARAM_RELATIONSHIP_NAMESPACE, relationshipNamespace);
				    put(PARAM_REMOVE_SENTENCE_ANNOTATIONS, removeSentenceAnnotations);
			    }
		    }));
	}
	
	/**
	 * Create an iterator over
	 * {@link txtfnnl.uima.tcas.RelationshipAnnotation} annotation types of
	 * some given namespace.
	 * 
	 * @param jcas with the annotations
	 * @param namespace to filter on (<code>null</code> to use all)
	 * @return an iterator over RelationshipAnnotation elements
	 */
	public static FSIterator<TOP> getRelationshipIterator(JCas jcas, String namespace) {
		FSIterator<TOP> annIt = jcas.getJFSIndexRepository().getAllIndexedFS(
		    RelationshipAnnotation.type);

		if (namespace != null) {
			Feature nsFeat = jcas.getTypeSystem().getFeatureByFullName(
			    RelationshipAnnotation.class.getName() + ":namespace");
			ConstraintFactory cf = jcas.getConstraintFactory();
			FeaturePath nsPath = jcas.createFeaturePath();
			nsPath.addFeature(nsFeat);
			FSStringConstraint nsCons = cf.createStringConstraint();
			nsCons.equals(namespace);
			FSMatchConstraint nsEmbed = cf.embedConstraint(nsPath, nsCons);
			annIt = jcas.createFilteredIterator(annIt, nsEmbed);
		}
		return annIt;
	}

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);
		entityNamespace = (String) ctx.getConfigParameterValue(PARAM_ENTITY_NAMESPACE);
		relationshipNamespace = (String) ctx.getConfigParameterValue(PARAM_RELATIONSHIP_NAMESPACE);

		ensureNotNull(entityNamespace, ResourceInitializationException.CONFIG_SETTING_ABSENT,
		    PARAM_ENTITY_NAMESPACE);

		ensureNotNull(relationshipNamespace,
		    ResourceInitializationException.CONFIG_SETTING_ABSENT, PARAM_RELATIONSHIP_NAMESPACE);

		Boolean rsaBoolean = (Boolean) ctx
		    .getConfigParameterValue(PARAM_REMOVE_SENTENCE_ANNOTATIONS);
		removeSentenceAnnotations = (rsaBoolean != null && rsaBoolean.booleanValue())
		        ? true
		        : false;
	}

	/**
	 * Iterate over sentences and their contained entities to detect any of
	 * the relationships in the given list.
	 * 
	 * If any relationship exists, it is annotated as a
	 * {@link txtfnnl.uima.tcas.RelationshipAnnotation}.
	 * 
	 * @param documentId of the current SOFA
	 * @param textJCas of the current SOFA
	 * @param relationships a list of all entity sets (ie., a "relationship")
	 *        to annotate
	 */
	@Override
	void process(String documentId, JCas textJCas, List<Set<Entity>> relationships) {
		int numRels = relationships.size();
		int[] found = new int[numRels];
		checksum += numRels;
		boolean hadRelations = false;
		List<Annotation> remove = new LinkedList<Annotation>();

		// Fetch a sentence iterator and the entity annotation index
		FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
		AnnotationIndex<Annotation> semanticAnnIdx = textJCas
		    .getAnnotationIndex(SemanticAnnotation.type);

		// Create an FSIterator constraint for entity annotations
		FSMatchConstraint entityCons = SemanticAnnotation
		    .makeConstraint(textJCas, entityNamespace);

		// Iterate over every sentence
		while (sentenceIt.hasNext()) {
			hadRelations = false;
			Annotation sentenceAnn = sentenceIt.next();
			FSIterator<Annotation> entityIt = textJCas.createFilteredIterator(
			    semanticAnnIdx.subiterator(sentenceAnn), entityCons);

			// If the sentence has entities...
			if (entityIt.hasNext()) {
				Map<Entity, List<SemanticAnnotation>> entityMap = new HashMap<Entity, List<SemanticAnnotation>>();

				// Collect all annotations into an entity map
				while (entityIt.hasNext()) {
					SemanticAnnotation entityAnn = (SemanticAnnotation) entityIt.next();
					Entity entity = new Entity(entityAnn.getIdentifier(), entityAnn.getProperties(
					    0).getValue(), entityAnn.getProperties(1).getValue());

					if (!entityMap.containsKey(entity))
						entityMap.put(entity, new LinkedList<SemanticAnnotation>());

					entityMap.get(entity).add(entityAnn);
				}
				Set<Entity> entitySet = entityMap.keySet();
				int pos = relationships.indexOf(entitySet);

				// If any of the known relationships' entity sets coincide
				// with the entities found in the sentence, annotate it
				if (pos != -1) {
					found[pos] = 1;
					annotateRelationship(entitySet, textJCas, entityMap, sentenceAnn);
					hadRelations = true;
				} else {
					List<Set<Entity>> done = new LinkedList<Set<Entity>>();

					for (pos = numRels; pos-- > 0;) {
						Set<Entity> rel = relationships.get(pos);

						if (entitySet.containsAll(rel) && !done.contains(rel)) {
							found[pos] = 1;
							annotateRelationship(rel, textJCas, entityMap, sentenceAnn);
							done.add(rel);
							hadRelations = true;
						}
					}
				}
			}

			if (!hadRelations && removeSentenceAnnotations)
				remove.add(sentenceAnn);
		}

		if (removeSentenceAnnotations) {
			for (Annotation sentenceAnn : remove) {
				textJCas.removeFsFromIndexes(sentenceAnn);
			}
		}
		setCounters(numRels, found);
	}

	/**
	 * Annotate a (potential) relationship between entities in a sentence on
	 * the CAS.
	 * 
	 * @param relationship set of entities in the relationship
	 * @param jcas to index the annotation
	 * @param entityMap of entities all their to SemanticAnnotation objects
	 * @param sentence containing the SemanticAnnotation objects
	 */
	void
	        annotateRelationship(Set<Entity> relationship, JCas jcas,
	                             Map<Entity, List<SemanticAnnotation>> entityMap,
	                             Annotation sentence) {
		RelationshipAnnotation ann = new RelationshipAnnotation(jcas);
		FSArray relSource = new FSArray(jcas, 1);
		int targetSize = 0;
		int idx = 0;

		for (Entity e : relationship)
			targetSize += entityMap.get(e).size();

		FSArray relTarget = new FSArray(jcas, targetSize);

		relSource.set(0, sentence);

		for (Entity e : relationship) {
			for (SemanticAnnotation sa : entityMap.get(e))
				relTarget.set(idx++, sa);
		}

		ann.setAnnotator(URI);
		ann.setConfidence(1.0);
		ann.setIdentifier("known-relationship"); // TODO
		ann.setNamespace(relationshipNamespace);
		ann.setSources(relSource);
		ann.setTargets(relTarget);
		ann.addToIndexes();
	}

	private void setCounters(int total, int[] found) {
		int tp_sum = 0;

		for (int i : found)
			tp_sum += i;

		truePositives += tp_sum;
		falseNegatives += total - tp_sum;
	}

}
