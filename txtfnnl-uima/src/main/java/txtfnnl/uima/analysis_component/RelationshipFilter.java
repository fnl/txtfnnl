package txtfnnl.uima.analysis_component;

import java.util.LinkedList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
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

import txtfnnl.uima.AnalysisComponentBuilder;
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
  public static final String PARAM_RELATIONSHIP_ANNOTATOR = "CoocurrenceAnnotator";
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

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      super(RelationshipFilter.class);
    }

    /**
     * Only iterate over {@link RelationshipAnnotation relationships} from a particular annotator.
     */
    public Builder setRelationshipAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_RELATIONSHIP_ANNOTATOR, uri);
      return this;
    }

    /**
     * Only iterate over {@link RelationshipAnnotation relationships} with a particular namespace.
     */
    public Builder setRelationshipNamespace(String ns) {
      setOptionalParameter(PARAM_RELATIONSHIP_NAMESPACE, ns);
      return this;
    }

    /**
     * Only iterate over {@link RelationshipAnnotation relationships} with a particular identifier.
     */
    public Builder setRelationshipIdentifier(String id) {
      setOptionalParameter(PARAM_RELATIONSHIP_IDENTIFIER, id);
      return this;
    }

    /**
     * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation
     * entities} of {@link RelationshipAnnotation relationships} from a particular annotator.
     */
    public Builder setEntityAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_ENTITY_ANNOTATOR, uri);
      return this;
    }

    /**
     * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation
     * entities} of {@link RelationshipAnnotation relationships} with a particular namespace.
     */
    public Builder setEntityNamespace(String ns) {
      setOptionalParameter(PARAM_ENTITY_NAMESPACE, ns);
      return this;
    }

    /**
     * Only verify {@link RelationshipAnnotation#getTargets() target} {@link TextAnnotation
     * entities} of {@link RelationshipAnnotation relationships} with a particular identifier.
     */
    public Builder setEntityIdentifier(String id) {
      setOptionalParameter(PARAM_ENTITY_IDENTIFIER, id);
      return this;
    }

    /**
     * Check for the inner, mapped {@link SemanticAnnotation annotations} in the
     * {@link TextAnnotation entities} from a particular annotator only.
     */
    public Builder setMappingAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_MAPPED_ANNOTATOR, uri);
      return this;
    }

    /**
     * Check for the inner, mapped {@link SemanticAnnotation annotations} in the
     * {@link TextAnnotation entities} with a particular namespace only.
     */
    public Builder setMappingNamespace(String ns) {
      setOptionalParameter(PARAM_MAPPED_NAMESPACE, ns);
      return this;
    }

    /**
     * Check for the inner, mapped {@link SemanticAnnotation annotations} in the
     * {@link TextAnnotation entities} with a particular identifier only.
     */
    public Builder setMappingIdentifier(String id) {
      setOptionalParameter(PARAM_MAPPED_IDENTIFIER, id);
      return this;
    }

    /**
     * Invert the behavior and remove {@link RelationshipAnnotation relationships} that had a
     * mapping.
     */
    public Builder removeMappedRelationships() {
      setOptionalParameter(PARAM_REMOVE_MAPPED, Boolean.TRUE);
      return this;
    }
  }

  /** Configure a new builder. */
  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.CONFIG, "initialized for {0}:{1} relationships from {2} "
        + "with {3}:{4} entities from {5} and {6}:{7} mappings from {8}", new String[] {
        relationshipNamespace, relationshipIdentifier, relationshipAnnotator, entityNamespace,
        entityIdentifier, entityAnnotator, mappedNamespace, mappedIdentifier, mappedAnnotator });
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
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
