package txtfnnl.uima.analysis_component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.UniqueTextAnnotation;
import txtfnnl.uima.Views;
import txtfnnl.uima.resource.GazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import txtfnnl.utils.Offset;
import txtfnnl.utils.stringsim.LeitnerLevenshtein;
import txtfnnl.utils.stringsim.Similarity;

/**
 * A system to create normalized (ID'd) annotations of names of entities in particular text spans.
 * This AE requires a <i>Gazetteer</i> with entity <i>names</i> mapped to <i>IDs</i>.
 * <p>
 * Text {@link TextAnnotation segments} are {@link GazetteerResource#match(String) matched} via a
 * {@link GazetteerResource}. The text segments used for matching can be filtered for their
 * {@link #PARAM_TEXT_NAMESPACE namespace} and, optionally, {@link #PARAM_TEXT_IDENTIFIER
 * identifier}. The {@link SemanticAnnotation semantic annotations} created on matched names are
 * annotated with a configurable {@link #PARAM_ENTITY_NAMESPACE namespace} and the name's
 * identifier. A confidence value is set on the semantic annotations based on the string similarity
 * of the match and the ambiguity of the entity in the Gazetteer: If a "normalized" name matches
 * multiple DB IDs, the confidence (i.e., string similarity value) of each annotation is reduced
 * proportionally to the number of known IDs for that name.
 * 
 * @author Florian Leitner
 */
public class GazetteerAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator (namespace and ID are defined dynamically). */
  public static final String URI = GazetteerAnnotator.class.getName();
  /** The <b>required</b> namespace for the {@link SemanticAnnotation entity annotations}. */
  public static final String PARAM_ENTITY_NAMESPACE = "EntityNamespace";
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE, mandatory = true)
  private String entityNamespace;
  /**
   * The (optional) namespace of {@link TextAnnotation annotations} containing the text to match
   * against the Gazetteer (default: match all text).
   */
  public static final String PARAM_TEXT_NAMESPACE = "TextNamespace";
  @ConfigurationParameter(name = PARAM_TEXT_NAMESPACE, mandatory = false)
  private String sourceNamespace;
  /**
   * The (optional) identifier of the {@link TextAnnotation annotations} containing the relevant
   * text.
   * <p>
   * This parameter is only effective if {@link #PARAM_TEXT_NAMESPACE} is set, too.
   */
  public static final String PARAM_TEXT_IDENTIFIER = "TextIdentifier";
  @ConfigurationParameter(name = PARAM_TEXT_IDENTIFIER, mandatory = false)
  private String sourceIdentifier = null;
  /** The GazetteerResource used for entity matching. */
  public static final String MODEL_KEY_GAZETTEER = "Gazetteer";
  @ExternalResource(key = MODEL_KEY_GAZETTEER)
  private GazetteerResource<Set<String>> gazetteer;
  private Similarity measure = LeitnerLevenshtein.INSTANCE; // TODO: make configurable?
  private Logger logger;
  private int count;

  public static class Builder extends AnalysisComponentBuilder {
    Builder(String entityNamespace, ExternalResourceDescription gazetteerResourceDescription) {
      super(GazetteerAnnotator.class);
      setRequiredParameter(PARAM_ENTITY_NAMESPACE, entityNamespace);
      setRequiredParameter(MODEL_KEY_GAZETTEER, gazetteerResourceDescription);
    }

    /**
     * Define a {@link TextAnnotation TextAnnotation} identifier pattern when limiting the
     * Gazetteer's text to scan (in addition to and only used if a
     * {@link Builder#setTextNamespace(String) namespace} is set).
     */
    public Builder setTextIdentifier(String sourceIdentifier) {
      setOptionalParameter(PARAM_TEXT_IDENTIFIER, sourceIdentifier);
      return this;
    }

    /**
     * Define the {@link TextAnnotation annotation} namespace to get the covered text from for the
     * Gazetteer's matcher.
     */
    public Builder setTextNamespace(String sourceNamespace) {
      setOptionalParameter(PARAM_TEXT_NAMESPACE, sourceNamespace);
      return this;
    }
  }

  /**
   * Create a new gazetteer configuration builder with a pre-configured gazetteer resource.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param gazetteerResourceDescription a pre-configured {@link GazetteerResource} description.
   */
  public static Builder configure(String entityNamespace,
      ExternalResourceDescription gazetteerResourceDescription) {
    return new Builder(entityNamespace, gazetteerResourceDescription);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.INFO, "{0} Gazetteer initialized with {1} entities", new Object[] {
        entityNamespace, gazetteer.size() });
    count = 0;
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    Set<UniqueTextAnnotation> annotated = new HashSet<UniqueTextAnnotation>();
    if (sourceNamespace == null) {
      Map<Offset, String> matches = gazetteer.match(jcas.getDocumentText());
      for (Offset offset : matches.keySet())
        for (SemanticAnnotation ann : annotateEntities(jcas, annotated, matches.get(offset),
            offset))
          ann.addToIndexes();
    } else {
      FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, null, sourceNamespace,
          sourceIdentifier);
      FSIterator<Annotation> it = TextAnnotation.getIterator(jcas);
      it = jcas.createFilteredIterator(it, cons);
      List<SemanticAnnotation> coll = new LinkedList<SemanticAnnotation>();
      while (it.hasNext())
        findEntities(jcas, annotated, it.next(), coll);
      for (SemanticAnnotation ann : coll)
        ann.addToIndexes();
    }
  }

  @Override
  public void destroy() {
    logger.log(Level.INFO, "tagged {0} {1} entities", new String[] { Integer.toString(count),
        entityNamespace });
  }

  /**
   * Iterate over the entity keys and offsets of all Gazetteer matches in the text of the
   * <code>annotation</code>.
   */
  private void findEntities(JCas jcas, Set<UniqueTextAnnotation> annotated, Annotation annotation,
      List<SemanticAnnotation> coll) {
    int base = annotation.getBegin();
    logger.log(Level.FINE, "scanning for {0} in ''{1}''", new String[] { entityNamespace,
        annotation.getCoveredText() });
    FSIterator<Annotation> tokenIt = jcas.getAnnotationIndex(TokenAnnotation.type).subiterator(
        annotation);
    List<Annotation> tokens = new LinkedList<Annotation>();
    while (tokenIt.hasNext())
      tokens.add(tokenIt.next());
    Map<Offset, String> matches = gazetteer.match(annotation.getCoveredText());
    for (Offset offset : matches.keySet())
      coll.addAll(annotateEntities(jcas, annotated, matches.get(offset),
          makeOffset(base, offset, tokens)));
  }

  private Offset makeOffset(int base, Offset offset, List<Annotation> tokens) {
    int begin = base + offset.start();
    int end = base + offset.end();
    for (Annotation t : tokens) {
      if (t.getBegin() <= begin && t.getEnd() >= begin) begin = t.getBegin();
      if (t.getBegin() <= end && t.getEnd() >= end) end = t.getEnd();
    }
    return new Offset(begin, end);
  }

  /**
   * Determine if the match is exact, or requires resolving the entity key, and calculate the
   * (normalized) {@link Similarity#similarity(String, String) string similarity} for the
   * annotation confidence.
   */
  private List<SemanticAnnotation> annotateEntities(JCas jcas,
      Set<UniqueTextAnnotation> annotated, String matchName, Offset offset) {
    List<SemanticAnnotation> coll = new LinkedList<SemanticAnnotation>();
    String txtName = (sourceNamespace == null) ? matchName : jcas.getDocumentText().substring(
        offset.start(), offset.end());
    if (gazetteer.containsKey(matchName)) {
      double confidence = measure.similarity(txtName, matchName);
      for (String dbId : gazetteer.get(matchName)) {
        UniqueTextAnnotation cta = new UniqueTextAnnotation(offset.start(), offset.end(),
            entityNamespace, dbId, URI);
        if (annotated.contains(cta)) continue;
        coll.add(annotate(dbId, jcas, offset, confidence));
        annotated.add(cta);
      }
    } else {
      Map<String, Double> dbIdToSim = new HashMap<String, Double>();
      Set<String> targets = gazetteer.resolve(matchName);
      for (String targetName : targets) {
        double sim = measure.similarity(txtName, targetName);
        for (String dbId : gazetteer.get(targetName)) {
          if (!dbIdToSim.containsKey(dbId) || dbIdToSim.get(dbId) < sim) dbIdToSim.put(dbId, sim);
        }
      }
      for (String dbId : dbIdToSim.keySet()) {
        UniqueTextAnnotation cta = new UniqueTextAnnotation(offset.start(), offset.end(),
            entityNamespace, dbId, URI);
        if (annotated.contains(cta)) continue;
        coll.add(annotate(dbId, jcas, offset, dbIdToSim.get(dbId)));
        annotated.add(cta);
      }
    }
    return coll;
  }

  /**
   * Add a {@link SemanticAnnotation semantic annotation} for a DB ID with a given confidence
   * value.
   */
  private SemanticAnnotation annotate(String id, JCas jcas, Offset offset, double confidence) {
    SemanticAnnotation entity = new SemanticAnnotation(jcas, offset);
    entity.setAnnotator(URI);
    entity.setConfidence(confidence);
    entity.setIdentifier(id);
    entity.setNamespace(entityNamespace);
    logger.log(Level.FINE, "detected {0}:{1} ({2})",
        new String[] { entityNamespace, id, Double.toString(confidence) });
    count++;
    return entity;
  }
}
