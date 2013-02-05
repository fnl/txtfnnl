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
import org.apache.uima.cas.SofaFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.utils.UIMAUtils;

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
public class RelationshipFilterAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator. */
  public static final String URI = RelationshipFilterAnnotator.class.getName();
  protected Logger logger;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} from a particular
   * annotator (default: work with any annotator/all sentences).
   */
  public static final String PARAM_RELATIONSHIP_ANNOTATOR = "RelationshipAnnotator";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_ANNOTATOR)
  private String relationshipAnnotator = null;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} with a particular
   * namespace.
   */
  public static final String PARAM_RELATIONSHIP_NAMESPACE = "RelationshipNamespace";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_NAMESPACE)
  private String relationshipNamespace = null;
  /**
   * Only iterate over {@link RelationshipAnnotation relationships} with a particular
   * identifier.
   */
  public static final String PARAM_RELATIONSHIP_IDENTIFIER = "RelationshipIdentifier";
  @ConfigurationParameter(name = PARAM_RELATIONSHIP_IDENTIFIER)
  private String relationshipIdentifier = null;
  /**
   * Only verify {@link TextAnnotation entities} of {@link RelationshipAnnotation relationships}
   * from a particular annotator.
   */
  public static final String PARAM_ENTITY_ANNOTATOR = "EntityAnnotator";
  @ConfigurationParameter(name = PARAM_ENTITY_ANNOTATOR)
  private String entityAnnotator = null;
  /**
   * Only verify {@link TextAnnotation entities} of {@link RelationshipAnnotation relationships}
   * with a particular namespace.
   */
  public static final String PARAM_ENTITY_NAMESPACE = "EntityNamespace";
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE)
  private String entityNamespace = null;
  /**
   * Only verify {@link TextAnnotation entities} of {@link RelationshipAnnotation relationships}
   * with a particular identifier.
   */
  public static final String PARAM_ENTITY_IDENTIFIER = "EntityIdentifier";
  @ConfigurationParameter(name = PARAM_ENTITY_IDENTIFIER)
  private String entityIdentifier = null;
  /**
   * Check for {@link TextAnnotation annotations} in the {@link TextAnnotation
   * entities} from a particular annotator.
   */
  public static final String PARAM_TARGET_ANNOTATOR = "TargetAnnotator";
  @ConfigurationParameter(name = PARAM_TARGET_ANNOTATOR)
  private String targetAnnotator = null;
  /**
   * Check for {@link TextAnnotation annotations} in the {@link TextAnnotation
   * entities} with a particular namespace.
   */
  public static final String PARAM_TARGET_NAMESPACE = "TargetNamespace";
  @ConfigurationParameter(name = PARAM_TARGET_NAMESPACE)
  private String targetNamespace = null;
  /**
   * Check for {@link TextAnnotation annotations} in the {@link TextAnnotation
   * entities} with a particular identifier.
   */
  public static final String PARAM_TARGET_IDENTIFIER = "TargetIdentifier";
  @ConfigurationParameter(name = PARAM_TARGET_IDENTIFIER)
  private String targetIdentifier = null;
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
   * @param targetAnnotator to require
   * @param targetNs to require
   * @param targetId to require
   * @param separator between values in the patterns file
   * @param removeMapped remove relationship annotations that have been mapped
   * @return a configured AE description
   * @throws UIMAException
   * @throws IOException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription
      configure(final String relAnnotator, final String relNs, final String relId,
          final String entityAnnotator, final String entityNs, final String entityId,
          final String targetAnnotator, final String targetNs, final String targetId,
          final boolean removeMapped) throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(RelationshipFilterAnnotator.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_RELATIONSHIP_ANNOTATOR, relAnnotator);
            put(PARAM_RELATIONSHIP_NAMESPACE, relNs);
            put(PARAM_RELATIONSHIP_IDENTIFIER, relId);
            put(PARAM_ENTITY_ANNOTATOR, entityAnnotator);
            put(PARAM_ENTITY_NAMESPACE, entityNs);
            put(PARAM_ENTITY_IDENTIFIER, entityId);
            put(PARAM_TARGET_ANNOTATOR, targetAnnotator);
            put(PARAM_TARGET_NAMESPACE, targetNs);
            put(PARAM_TARGET_IDENTIFIER, targetId);
            put(PARAM_REMOVE_MAPPED, removeMapped);
          }
        }));
  }

  /** Default configuration requires no parameters. */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return RelationshipFilterAnnotator.configure(null, null, null, null, null, null, null, null,
        null, false);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
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
    final FSMatchConstraint targetCons = TextAnnotation.makeConstraint(jcas, targetAnnotator,
        targetNamespace, targetIdentifier);
    final AnnotationIndex<Annotation> textIdx = jcas.getAnnotationIndex(TextAnnotation.type);
    final FSMatchConstraint relCons = RelationshipAnnotation.makeConstraint(jcas,
        relationshipAnnotator, relationshipNamespace, relationshipIdentifier);
    final FSIterator<SofaFS> relIt = jcas.createFilteredIterator(jcas.getSofaIterator(), relCons);
    int count = 0;
    List<RelationshipAnnotation> removable = new LinkedList<RelationshipAnnotation>();
    while (relIt.hasNext()) {
      count++;
      rel = (RelationshipAnnotation) relIt.next();
      boolean mapped = isMapped(jcas, rel.getSources(), targetCons, textIdx);
      if (mapped) mapped = isMapped(jcas, rel.getTargets(), targetCons, textIdx);
      if (!mapped && !removeMapped || mapped && removeMapped) removable.add(rel);
    }
    for (RelationshipAnnotation r : removable)
      r.removeFromIndexes();
    logger.log(Level.FINE, "removed {0}/{0} relationship annotations",
        new Object[] { removable.size(), count });
  }

  private boolean isMapped(JCas jcas, FSArray entities, final FSMatchConstraint targetCons,
      final AnnotationIndex<Annotation> textIdx) {
    for (int i = 0; i < entities.size(); i++) {
      TextAnnotation ann = (TextAnnotation) entities.get(i);
      if (entityAnnotator == null || entityAnnotator.equals(ann.getAnnotator()) &&
          entityNamespace == null || entityNamespace.equals(ann.getNamespace()) &&
          entityIdentifier == null || entityIdentifier.equals(ann.getIdentifier()) &&
          !jcas.createFilteredIterator(textIdx.subiterator(ann, true, true), targetCons).hasNext())
        return false;
    }
    return true;
  }
}
