package txtfnnl.uima.analysis_component;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.UIMAUtils;
import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;

/**
 * Remove {@link RelationshipAnnotation relationships} whose linked {@link TextAnnotation text
 * entities} (i.e., the sources and targets) do not contain some other {@link TextAnnotation text
 * annotation}.
 * <p>
 * This filter can be used to remove relationships between text entities that could not then be
 * further annotated with more information, such as normalized mappings.
 * 
 * @author Florian Leitner
 */
public class RelationshipFilter extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator. */
  public static final String URI = RelationshipFilter.class.getName();
  protected Logger logger;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} from a particular annotator.
   */
  public static final String PARAM_RELATIONSHIP_ANNOTATOR = "RelationshipAnnotator";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_ANNOTATOR)
  private String relationshipAnnotator = null;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} with a particular namespace.
   */
  public static final String PARAM_RELATIONSHIP_NAMESPACE = "RelationshipNamespace";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_NAMESPACE)
  private String relationshipNamespace = null;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} with a particular identifier.
   */
  public static final String PARAM_RELATIONSHIP_IDENTIFIER = "RelationshipIdentifier";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_IDENTIFIER)
  private String relationshipIdentifier = null;
  /**
   * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation entities}
   * of {@link RelationshipAnnotation relationships} from a particular annotator.
   */
  public static final String PARAM_ENTITY_ANNOTATOR = "EntityAnnotator";
  @ConfigurationParameter(name = PARAM_ENTITY_ANNOTATOR)
  private String entityAnnotator = null;
  /**
   * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation entities}
   * of {@link RelationshipAnnotation relationships} with a particular namespace.
   */
  public static final String PARAM_ENTITY_NAMESPACE = "EntityNamespace";
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE)
  private String entityNamespace = null;
  /**
   * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation entities}
   * of {@link RelationshipAnnotation relationships} with a particular identifier.
   */
  public static final String PARAM_ENTITY_IDENTIFIER = "EntityIdentifier";
  @ConfigurationParameter(name = PARAM_ENTITY_IDENTIFIER)
  private String entityIdentifier = null;
  /**
   * Check for inner, mapped {@link SemanticAnnotation annotations} in the {@link TextAnnotation
   * entities} from a particular annotator.
   */
  public static final String PARAM_MAPPED_ANNOTATOR = "MappedAnnotator";
  @ConfigurationParameter(name = PARAM_MAPPED_ANNOTATOR)
  private String mappedAnnotator = null;
  /**
   * Check for inner, mapped {@link SemanticAnnotation annotations} in the {@link TextAnnotation
   * entities} with a particular namespace.
   */
  public static final String PARAM_MAPPED_NAMESPACE = "MappedNamespace";
  @ConfigurationParameter(name = PARAM_MAPPED_NAMESPACE)
  private String mappedNamespace = null;
  /**
   * Check for inner, mapped {@link SemanticAnnotation annotations} in the {@link TextAnnotation
   * entities} with a particular identifier.
   */
  public static final String PARAM_MAPPED_IDENTIFIER = "MappedIdentifier";
  @ConfigurationParameter(name = PARAM_MAPPED_IDENTIFIER)
  private String mappedIdentifier = null;
  /**
   * Remove {@link RelationshipAnnotation relationships} that had a mapping (default: retain only
   * relationships with mappings).
   */
  public static final String PARAM_REMOVE_MAPPED = "RemoveMapped";
  @ConfigurationParameter(name = PARAM_REMOVE_MAPPED, defaultValue = "false")
  private boolean removeMapped;

  /**
   * Configure a new descriptor with a pattern file resource.
   * 
   * @param relAnnotator to check
   * @param relNs to check
   * @param relId to check
   * @param entityAnnotator to regard
   * @param entityNs to regard
   * @param entityId to regard
   * @param mappedAnnotator to require
   * @param mappedNs to require
   * @param mappedId to require
   * @param separator between values in the patterns file
   * @param removeMapped remove relationship annotations that have been mapped
   * @return a configured AE description
   * @throws UIMAException
   * @throws IOException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final String relAnnotator, final String relNs,
      final String relId, final String entityAnnotator, final String entityNs,
      final String entityId, final String mappedAnnotator, final String mappedNs,
      final String mappedId, final boolean removeMapped) throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(RelationshipFilter.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_RELATIONSHIP_ANNOTATOR, relAnnotator);
            put(PARAM_RELATIONSHIP_NAMESPACE, relNs);
            put(PARAM_RELATIONSHIP_IDENTIFIER, relId);
            put(PARAM_ENTITY_ANNOTATOR, entityAnnotator);
            put(PARAM_ENTITY_NAMESPACE, entityNs);
            put(PARAM_ENTITY_IDENTIFIER, entityId);
            put(PARAM_MAPPED_ANNOTATOR, mappedAnnotator);
            put(PARAM_MAPPED_NAMESPACE, mappedNs);
            put(PARAM_MAPPED_IDENTIFIER, mappedId);
            put(PARAM_REMOVE_MAPPED, removeMapped);
          }
        }));
  }

  /** Default configuration requires no parameters. */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return RelationshipFilter.configure(null, null, null, null, null, null, null, null, null,
        false);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.INFO, "initialized for {0}:{1} relationships from {2} "
        + "with {3}:{4} entities from {5} and {6}:{7} mappings from {8}", new String[] {
        relationshipNamespace, relationshipIdentifier, relationshipAnnotator, entityNamespace,
        entityIdentifier, entityAnnotator, mappedNamespace, mappedIdentifier, mappedAnnotator });
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    RelationshipAnnotation rel;
    final FSMatchConstraint relCons = RelationshipAnnotation.makeConstraint(jcas,
        relationshipAnnotator, relationshipNamespace, relationshipIdentifier);
    FSIterator<TOP> relIt = jcas.createFilteredIterator(jcas.getJFSIndexRepository()
        .getAllIndexedFS(RelationshipAnnotation.type), relCons);
    final FSMatchConstraint mappedCons = (mappedAnnotator == null && mappedNamespace == null && mappedIdentifier == null)
        ? null : SemanticAnnotation.makeConstraint(jcas, mappedAnnotator, mappedNamespace,
            mappedIdentifier);
    final AnnotationIndex<Annotation> mappedIdx = jcas.getAnnotationIndex(SemanticAnnotation.type);
    List<RelationshipAnnotation> removable = new LinkedList<RelationshipAnnotation>();
    int count = 0;
    while (relIt.hasNext()) {
      count++;
      rel = (RelationshipAnnotation) relIt.next();
      boolean mapped = isMapped(jcas, rel.getTargets(), mappedCons, mappedIdx);
      if (!mapped && !removeMapped || mapped && removeMapped) removable.add(rel);
    }
    for (RelationshipAnnotation r : removable)
      r.removeFromIndexes();
    logger.log(Level.FINE, "removed {0}/{1} relationship annotations",
        new Object[] { removable.size(), count });
  }

  private boolean isMapped(JCas jcas, FSArray entities, final FSMatchConstraint mappedCons,
      final AnnotationIndex<Annotation> mappedIdx) {
    boolean tested = false;
    for (int i = 0; i < entities.size(); i++) {
      TextAnnotation ann = (TextAnnotation) entities.get(i);
      if (entityAnnotator != null && !entityAnnotator.equals(ann.getAnnotator()) ||
          entityNamespace != null && !entityNamespace.equals(ann.getNamespace()) ||
          entityIdentifier != null && !entityIdentifier.equals(ann.getIdentifier())) continue;
      if (mappedCons == null) {
        if (!mappedIdx.subiterator(ann, true, true).hasNext()) return false;
      } else {
        if (!(jcas.createFilteredIterator(mappedIdx.subiterator(ann, true, true), mappedCons)
            .hasNext())) return false;
      }
      tested = true;
    }
    return tested;
  }
}
