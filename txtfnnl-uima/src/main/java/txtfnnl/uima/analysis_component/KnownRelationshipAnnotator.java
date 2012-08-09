package txtfnnl.uima.analysis_component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
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
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * An AE that annotates sentences as (potentially) containing a relationship
 * (description) between pre-defined sets of entities.
 * 
 * Note that the entities should have been annotated by the
 * {@link KnownEntityAnnotator} or at least obey the following requirements:
 * <ul>
 * <li>The must be {@link txtfnnl.uima.tcas.SemanticAnnotation} types</li>
 * <li>The :identifier annotation must represent the Entity type value</li>
 * <li>The first Property value must hold the Entity namespace value</li>
 * <li>The second Property value must hold the Entity identifier value</li>
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
public class KnownRelationshipAnnotator extends
        KnownEvidenceAnnotator<List<Set<Entity>>> {

	/** The URI of this Annotator. */
	public static final String URI = "http://txtfnnl/KnownRelationshipAnnotator";

	/** The namespace for the Relationship annotations. */
	public static final String PARAM_RELATIONSHIP_NAMESPACE = "RelationshipNamespace";

	/** The namespace used by the Entity annotations. */
	public static final String PARAM_ENTITY_NAMESPACE = KnownEntityAnnotator.PARAM_NAMESPACE;

	/**
	 * The name of the sentence annotation type.
	 * 
	 * Defaults to {@link SentenceAnnotator#SENTENCE_TYPE_NAME}. Can be set as
	 * an AE descriptor parameter with the name
	 * {@link UimaUtil#SENTENCE_TYPE_PARAMETER}.
	 */
	private String sentenceTypeName;

	/**
	 * The namespace used to annotate Entities.
	 * 
	 * Should be set as an AE descriptor parameter with the name
	 * {@link KnownRelationshipAnnotator#PARAM_ENTITY_NAMESPACE}.
	 * */
	private String entityNamespace;

	/**
	 * The namespace used to annotate Relationships.
	 * 
	 * Should be set as an AE descriptor parameter with the name
	 * {@link KnownRelationshipAnnotator#PARAM_RELATIONSHIP_NAMESPACE}.
	 * */
	private String relationshipNamespace;

	/**
	 * Create an iterator over
	 * {@link txtfnnl.uima.tcas.RelationshipAnnotation} annotation types of
	 * some given namespace.
	 * 
	 * @param jcas with the annotations
	 * @param namespace to filter on (<code>null</code> to use all)
	 * @return an iterator over RelationshipAnnotation elements
	 */
	public static FSIterator<TOP>
	        getRelationshipIterator(JCas jcas, String namespace) {
		FSIterator<TOP> annIt = jcas.getJFSIndexRepository().getAllIndexedFS(RelationshipAnnotation.type);

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
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(UimaUtil.SENTENCE_TYPE_PARAMETER);
		entityNamespace = (String) ctx
		    .getConfigParameterValue(PARAM_ENTITY_NAMESPACE);
		relationshipNamespace = (String) ctx
		    .getConfigParameterValue(PARAM_RELATIONSHIP_NAMESPACE);

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		ensureNotNull(entityNamespace,
		    ResourceInitializationException.CONFIG_SETTING_ABSENT,
		    PARAM_ENTITY_NAMESPACE);

		ensureNotNull(relationshipNamespace,
		    ResourceInitializationException.CONFIG_SETTING_ABSENT,
		    PARAM_RELATIONSHIP_NAMESPACE);
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
	void process(String documentId, JCas textJCas,
	             List<Set<Entity>> relationships) {
		int numRels = relationships.size();
		int[] found = new int[numRels];
		checksum += numRels;

		// Fetch the sentence iterator and the entity annotation index
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(textJCas, sentenceTypeName);
		AnnotationIndex<Annotation> semanticAnnIdx = textJCas
		    .getAnnotationIndex(SemanticAnnotation.type);

		// Create an FSIterator constraint for entity annotations
		Feature namespaceFeat = textJCas.getTypeSystem().getFeatureByFullName(
		    SemanticAnnotation.class.getName() + ":namespace");
		ConstraintFactory cf = textJCas.getConstraintFactory();
		FeaturePath namespacePath = textJCas.createFeaturePath();
		namespacePath.addFeature(namespaceFeat);
		FSStringConstraint namespaceCons = cf.createStringConstraint();
		namespaceCons.equals(entityNamespace);
		FSMatchConstraint entityCons = cf.embedConstraint(namespacePath,
		    namespaceCons);

		// Iterate over every sentence
		while (sentenceIt.hasNext()) {
			SyntaxAnnotation sentenceAnn = (SyntaxAnnotation) sentenceIt
			    .next();
			FSIterator<Annotation> semanticAnnIt = semanticAnnIdx
			    .subiterator(sentenceAnn);
			FSIterator<Annotation> entityIt = textJCas.createFilteredIterator(
			    semanticAnnIt, entityCons);

			// If the sentence has entities...
			if (entityIt.hasNext()) {
				Map<Entity, List<SemanticAnnotation>> entityMap = new HashMap<Entity, List<SemanticAnnotation>>();

				// Collect all annotations into an entity map
				while (entityIt.hasNext()) {
					SemanticAnnotation entityAnn = (SemanticAnnotation) entityIt
					    .next();
					Entity entity = new Entity(entityAnn.getIdentifier(),
					    entityAnn.getProperties(0).getValue(), entityAnn
					        .getProperties(1).getValue());

					if (!entityMap.containsKey(entity))
						entityMap.put(entity,
						    new LinkedList<SemanticAnnotation>());

					entityMap.get(entity).add(entityAnn);
				}
				Set<Entity> entitySet = entityMap.keySet();
				int pos = relationships.indexOf(entitySet);

				// If any of the known relationships' entity sets coincide
				// with the entities found in the sentence, annotate it
				if (pos != -1) {
					found[pos] = 1;
					annotateRelationship(entitySet, textJCas, entityMap,
					    sentenceAnn);
				} else {
					List<Set<Entity>> done = new LinkedList<Set<Entity>>();
					
					for (pos = numRels; pos-- > 0;) {
						Set<Entity> rel = relationships.get(pos);

						if (entitySet.containsAll(rel) && !done.contains(rel)) {
							found[pos] = 1;
							annotateRelationship(rel, textJCas, entityMap,
							    sentenceAnn);
							done.add(rel);
						}
					}
				}
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
	void annotateRelationship(Set<Entity> relationship, JCas jcas,
	                          Map<Entity, List<SemanticAnnotation>> entityMap,
	                          SyntaxAnnotation sentence) {
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
		ann.setIdentifier("TODO-relationship-id"); // TODO
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
