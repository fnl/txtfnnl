package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.resource.RelationshipStringMapResource;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * An AE that annotates {@link SemanticAnnotation semantic annotations} of {@link Entity entities}
 * in {@link SentenceAnnotation sentences} as (potentially) describing a relation between those
 * entities as {@link RelationshipAnnotation relationship annotations} between the sentence (in the
 * sources feature) and entities (in the targets feature).
 * <p>
 * Note that the entities should have been annotated by the {@link KnownEntityAnnotator} or at
 * least obey the following requirements:
 * <ul>
 * <li>They must be {@link SemanticAnnotation} types</li>
 * <li>The :identifier feature must represent the Entity's type value</li>
 * <li>The first :property dictionary value must hold the Entity's namespace value</li>
 * <li>The second :property dictionary value must hold the Entity's ID value</li>
 * </ul>
 * Parameter settings:
 * <ul>
 * <li>String {@link #PARAM_RELATIONSHIP_NAMESPACE} (required)</li>
 * <li>String {@link #PARAM_ENTITY_NAMESPACE} (required)</li>
 * </ul>
 * <b>Resources</b>:
 * <dl>
 * <dt>{@link KnownEvidenceAnnotator#MODEL_KEY_EVIDENCE_STRING_MAP Evidence String Map}</dt>
 * <dd>a TSV file of known relationships</dd>
 * </dl>
 * The <b>Evidence String Map</b> resource has to be a TSV file with the following columns:
 * <ol>
 * <li>Document ID: SOFA URI basename (without the file suffix)</li>
 * <li>Entity Type: matching the :identifier features of the SemanticAnnotations of the annotated
 * Entities</li>
 * <li>Namespace: matching the first :property feature value of the SemanticAnnotations of the
 * Entities</li>
 * <li>Identifier: matching the second :property feature value of the SemanticAnnotations of the
 * Entities</li>
 * </ol>
 * The last three columns can be repeated as often as required to list all entities part of the
 * relationship. (Note that if only a single entity is present, essentially all sentences that
 * contain the entity will be annotated by this AE.)
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
   * The namespace that was used to annotate entities (as {@link SemanticAnnotation} :namespace
   * features).
   */
  public static final String PARAM_ENTITY_NAMESPACE = KnownEntityAnnotator.PARAM_NAMESPACE;
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE, mandatory = true)
  private String entityNamespace;
  /**
   * Remove sentence annotations that contain no relationship. An optional flag indicating if
   * sentence annotations without relationships should be removed from the CAS.
   */
  public static final String PARAM_REMOVE_SENTENCE_ANNOTATIONS = "RemoveSentenceAnnotations";
  @ConfigurationParameter(name = PARAM_REMOVE_SENTENCE_ANNOTATIONS, defaultValue = "false")
  private boolean removeSentenceAnnotations;

  public static class Builder extends KnownEvidenceAnnotator.Builder {
    public Builder(String relationshipNamespace, String entityNamespace,
        ExternalResourceDescription relationshipMapResource) throws IOException {
      super(KnownEntityAnnotator.class, relationshipMapResource);
      setRequiredParameter(PARAM_RELATIONSHIP_NAMESPACE, relationshipNamespace);
      setRequiredParameter(PARAM_ENTITY_NAMESPACE, entityNamespace);
    }

    public Builder removeSentenceAnnotations() {
      setOptionalParameter(PARAM_REMOVE_SENTENCE_ANNOTATIONS, Boolean.TRUE);
      return this;
    }
  }

  /**
   * Configure a description builder for this engine.
   * 
   * @param relationshipNamespace to use for the {@link RelationshipAnnotation} instances
   * @param entityNamespace to limit {@link SemanticAnnotation} instances of entities
   * @param relationshipMap containing the entity mappings to look out for
   * @return an AE description builder
   * @throws IOException if the relationship map does not exist
   * @throws ResourceInitializationException if the relationship map cannot be initialized
   */
  public static Builder configure(String relationshipNamespace, String entityNamespace,
      File relationshipMap) throws IOException, ResourceInitializationException {
    return new Builder(relationshipNamespace, entityNamespace, RelationshipStringMapResource
        .configure("file:" + relationshipMap.getCanonicalPath()).create());
  }

  /**
   * Create an iterator over {@link txtfnnl.uima.tcas.RelationshipAnnotation} annotation types of
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
      final Feature nsFeat = jcas.getTypeSystem().getFeatureByFullName(
          RelationshipAnnotation.class.getName() + ":namespace");
      final ConstraintFactory cf = jcas.getConstraintFactory();
      final FeaturePath nsPath = jcas.createFeaturePath();
      nsPath.addFeature(nsFeat);
      final FSStringConstraint nsCons = cf.createStringConstraint();
      nsCons.equals(namespace);
      final FSMatchConstraint nsEmbed = cf.embedConstraint(nsPath, nsCons);
      annIt = jcas.createFilteredIterator(annIt, nsEmbed);
    }
    return annIt;
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger.log(Level.CONFIG, "initialized with relationship namespace={0}"
        + ", entity namespace={1}, removing sentence annotations={2}", new Object[] {
        relationshipNamespace, entityNamespace, removeSentenceAnnotations });
  }

  /**
   * Iterate over sentences and their contained entities to detect any of the relationships in the
   * given list. If any relationship exists, it is annotated as a
   * {@link txtfnnl.uima.tcas.RelationshipAnnotation}.
   * 
   * @param documentId of the current SOFA
   * @param textJCas of the current SOFA
   * @param relationships a list of all entity sets (ie., a "relationship") to annotate
   */
  @Override
  void process(String documentId, JCas textJCas, List<Set<Entity>> relationships) {
    final int numRels = relationships.size();
    final int[] found = new int[numRels];
    checksum += numRels;
    boolean hadRelations = false;
    final List<Annotation> remove = new LinkedList<Annotation>();
    // Fetch a sentence iterator and the entity annotation index
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
    final AnnotationIndex<Annotation> semanticAnnIdx = textJCas
        .getAnnotationIndex(SemanticAnnotation.type);
    // Create an FSIterator constraint for entity annotations
    final FSMatchConstraint entityCons = SemanticAnnotation.makeConstraint(textJCas, null,
        entityNamespace);
    // Iterate over every sentence
    while (sentenceIt.hasNext()) {
      hadRelations = false;
      final Annotation sentenceAnn = sentenceIt.next();
      final FSIterator<Annotation> entityIt = textJCas.createFilteredIterator(
          semanticAnnIdx.subiterator(sentenceAnn, true, true), entityCons);
      // If the sentence has entities...
      if (entityIt.hasNext()) {
        final Map<Entity, List<SemanticAnnotation>> entityMap = new HashMap<Entity, List<SemanticAnnotation>>();
        // Collect all annotations into an entity map
        while (entityIt.hasNext()) {
          final SemanticAnnotation entityAnn = (SemanticAnnotation) entityIt.next();
          final Entity entity = new Entity(entityAnn.getIdentifier(), entityAnn.getProperties(0)
              .getValue(), entityAnn.getProperties(1).getValue());
          if (!entityMap.containsKey(entity)) {
            entityMap.put(entity, new LinkedList<SemanticAnnotation>());
          }
          entityMap.get(entity).add(entityAnn);
        }
        final Set<Entity> entitySet = entityMap.keySet();
        int pos = relationships.indexOf(entitySet);
        // If any of the known relationships' entity sets coincide
        // with the entities found in the sentence, annotate it
        if (pos != -1) {
          found[pos] = 1;
          annotateRelationship(entitySet, textJCas, entityMap, sentenceAnn);
          hadRelations = true;
        } else {
          final List<Set<Entity>> done = new LinkedList<Set<Entity>>();
          for (pos = numRels; pos-- > 0;) {
            final Set<Entity> rel = relationships.get(pos);
            if (entitySet.containsAll(rel) && !done.contains(rel)) {
              found[pos] = 1;
              annotateRelationship(rel, textJCas, entityMap, sentenceAnn);
              done.add(rel);
              hadRelations = true;
            }
          }
        }
      }
      if (!hadRelations && removeSentenceAnnotations) {
        remove.add(sentenceAnn);
      }
    }
    if (removeSentenceAnnotations) {
      for (final Annotation sentenceAnn : remove) {
        textJCas.removeFsFromIndexes(sentenceAnn);
      }
    }
    setCounters(numRels, found);
  }

  /**
   * Annotate a (potential) relationship between entities in a sentence on the CAS.
   * 
   * @param relationship set of entities in the relationship
   * @param jcas to index the annotation
   * @param entityMap of entities all their to SemanticAnnotation objects
   * @param sentence containing the SemanticAnnotation objects
   */
  void annotateRelationship(Set<Entity> relationship, JCas jcas,
      Map<Entity, List<SemanticAnnotation>> entityMap, Annotation sentence) {
    final RelationshipAnnotation ann = new RelationshipAnnotation(jcas);
    final FSArray relSource = new FSArray(jcas, 1);
    int targetSize = 0;
    int idx = 0;
    for (final Entity e : relationship) {
      targetSize += entityMap.get(e).size();
    }
    final FSArray relTarget = new FSArray(jcas, targetSize);
    relSource.set(0, sentence);
    for (final Entity e : relationship) {
      for (final SemanticAnnotation sa : entityMap.get(e)) {
        relTarget.set(idx++, sa);
      }
    }
    ann.setAnnotator(URI);
    ann.setConfidence(1.0);
    ann.setIdentifier("known-relationship"); // XXX: setting relationship IDs?
    ann.setNamespace(relationshipNamespace);
    ann.setSources(relSource);
    ann.setTargets(relTarget);
    ann.addToIndexes();
  }

  private void setCounters(int total, int[] found) {
    int tp_sum = 0;
    for (final int i : found) {
      tp_sum += i;
    }
    truePositives += tp_sum;
    falseNegatives += total - tp_sum;
    if (tp_sum > 0) {
      logger.log(Level.INFO, "found {0} known relationships", tp_sum);
      if (total > tp_sum) {
        logger.log(Level.INFO, "missed {0} known relationships", total - tp_sum);
      }
    } else {
      logger.log(Level.WARNING, "missed all {0} known relationships", total);
    }
  }
}
