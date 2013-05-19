package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
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
import txtfnnl.uima.resource.GazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
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
  protected String entityNamespace;
  /**
   * The (optional) namespace of {@link TextAnnotation annotations} containing the text to match
   * against the Gazetteer (default: match all text).
   */
  public static final String PARAM_TEXT_NAMESPACE = "TextNamespace";
  @ConfigurationParameter(name = PARAM_TEXT_NAMESPACE, mandatory = false)
  protected String sourceNamespace;
  /**
   * The (optional) identifier of the {@link TextAnnotation annotations} containing the relevant
   * text.
   * <p>
   * This parameter is only effective if {@link #PARAM_TEXT_NAMESPACE} is set, too.
   */
  public static final String PARAM_TEXT_IDENTIFIER = "TextIdentifier";
  @ConfigurationParameter(name = PARAM_TEXT_IDENTIFIER, mandatory = false)
  protected String sourceIdentifier = null;
  /**
   * The minimum string similarity value required to annotate the match.
   * <p>
   * Defaults to 0.0, i.e., no limitation. The value must be in the <code>[0.0, 1.0)</code> range,
   * where 0.0 means any match is OK, and 1.0 would indicate to not annotate anything at all.
   */
  public static final String PARAM_MIN_SIMILARITY = "MinSimilarity";
  @ConfigurationParameter(name = PARAM_MIN_SIMILARITY, mandatory = false, defaultValue = "0.0")
  private float minSimilarity;
  /** A list of matches to allow (if set, all others will be ignored). */
  public static final String PARAM_WHITELIST = "Whitelist";
  @ConfigurationParameter(name = PARAM_WHITELIST, mandatory = false)
  private String[] whitelist;
  /** A list of matches to remove (useless in conjunction with a whitelist). */
  public static final String PARAM_BLACKLIST = "Blacklist";
  @ConfigurationParameter(name = PARAM_BLACKLIST, mandatory = false)
  private String[] blacklist;
  /** The filter strategy chosen based on whitelist and blacklist settings. */
  private FilterStrategy filter;
  /** The GazetteerResource used for entity matching. */
  public static final String MODEL_KEY_GAZETTEER = "Gazetteer";
  @ExternalResource(key = MODEL_KEY_GAZETTEER)
  protected GazetteerResource gazetteer;
  private Similarity measure = LeitnerLevenshtein.INSTANCE; // TODO: make configurable?
  private Logger logger;
  private int count;
  private int filtered;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass, String entityNamespace,
        ExternalResourceDescription gazetteerResourceDescription) {
      super(klass);
      setRequiredParameter(PARAM_ENTITY_NAMESPACE, entityNamespace);
      setRequiredParameter(MODEL_KEY_GAZETTEER, gazetteerResourceDescription);
    }

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
     * Set a minimum string similarity value required to annotate the match. The value should be in
     * the <code>[0.0, 1.0)</code> range, where 0.0 means any match is OK, and 1.0 would indicate
     * to not annotate anything at all (so it should be less than 1.0).
     */
    public Builder setMinimumSimilarity(double d) {
      assert 0.0 <= d && d < 1.0 : "bad minimum simliarity value " + Double.toString(d);
      setOptionalParameter(PARAM_MIN_SIMILARITY, new Float(d));
      return this;
    }

    /**
     * Define a list of exact matches that should be annotated (white-listing); Any other matches
     * will not be annotated.
     */
    public Builder setWhitelist(String[] whitelist) {
      setOptionalParameter(PARAM_WHITELIST, whitelist);
      return this;
    }

    /**
     * Define a list of exact matches that should not be annotated ("stop words"); Any other
     * matches will be annotated.
     * <p>
     * Note that defining both a whitelist and a blacklist is probably a mistake: anything not on
     * the whitelist will not be annotated, while if the mention is found in both lists, it will
     * not be annotated, either.
     */
    public Builder setBlacklist(String[] blacklist) {
      setOptionalParameter(PARAM_BLACKLIST, blacklist);
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

  protected static interface FilterStrategy {
    public int process(JCas jcas, List<SemanticAnnotation> buffer, String match, Offset offset,
        Set<String> ids);
  }

  protected static class SelectWhitelist implements FilterStrategy {
    private Set<String> allowedMatches;
    private GazetteerAnnotator annotator;

    public SelectWhitelist(String[] whitelist, GazetteerAnnotator ga) {
      allowedMatches = new HashSet<String>(Arrays.asList(whitelist));
      annotator = ga;
    }

    public int process(JCas jcas, List<SemanticAnnotation> buffer, String match, Offset offset,
        Set<String> ids) {
      if (allowedMatches.contains(match)) {
        buffer.addAll(annotator.makeAnnotations(jcas, match, ids, offset));
        return ids.size();
      } else {
        return 0;
      }
    }
  }

  protected static class FilterBlacklist implements FilterStrategy {
    private Set<String> filteredMatches;
    private GazetteerAnnotator annotator;

    public FilterBlacklist(String[] blacklist, GazetteerAnnotator ga) {
      filteredMatches = new HashSet<String>(Arrays.asList(blacklist));
      annotator = ga;
    }

    public int process(JCas jcas, List<SemanticAnnotation> buffer, String match, Offset offset,
        Set<String> ids) {
      if (!filteredMatches.contains(match)) {
        buffer.addAll(annotator.makeAnnotations(jcas, match, ids, offset));
        return ids.size();
      } else {
        return 0;
      }
    }
  }

  protected static class SelectAndFilter implements FilterStrategy {
    private Set<String> allowedMatches;
    private Set<String> filteredMatches;
    private GazetteerAnnotator annotator;

    public SelectAndFilter(String[] whitelist, String[] blacklist, GazetteerAnnotator ga) {
      allowedMatches = new HashSet<String>(Arrays.asList(whitelist));
      filteredMatches = new HashSet<String>(Arrays.asList(blacklist));
      annotator = ga;
      annotator.logger.log(Level.WARNING, "defining both a while- and a blacklist");
    }

    public int process(JCas jcas, List<SemanticAnnotation> buffer, String match, Offset offset,
        Set<String> ids) {
      if (allowedMatches.contains(match) && !filteredMatches.contains(match)) {
        buffer.addAll(annotator.makeAnnotations(jcas, match, ids, offset));
        return ids.size();
      } else {
        return 0;
      }
    }
  }

  protected static class NoStrategy implements FilterStrategy {
    private GazetteerAnnotator annotator;

    public NoStrategy(GazetteerAnnotator ga) {
      annotator = ga;
    }

    public int process(JCas jcas, List<SemanticAnnotation> buffer, String match, Offset offset,
        Set<String> ids) {
      buffer.addAll(annotator.makeAnnotations(jcas, match, ids, offset));
      return ids.size();
    }
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.CONFIG, "{0} Gazetteer initialized with {1} entities", new Object[] {
        entityNamespace, gazetteer.size() });
    if (whitelist != null && whitelist.length > 0) {
      if (blacklist != null && blacklist.length > 0) filter = new SelectAndFilter(whitelist,
          blacklist, this);
      else filter = new SelectWhitelist(whitelist, this);
    } else if (blacklist != null && blacklist.length > 0) {
      filter = new FilterBlacklist(blacklist, this);
    } else {
      filter = new NoStrategy(this);
    }
    count = 0;
    filtered = 0;
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String docText = jcas.getDocumentText();
    List<SemanticAnnotation> buffer = new LinkedList<SemanticAnnotation>();
    if (sourceNamespace == null) {
      Map<Offset, Set<String>> matches = matchText(docText);
      for (Offset offset : matches.keySet()) {
        String match = docText.substring(offset.start(), offset.end());
        filtered += filter.process(jcas, buffer, match, offset, matches.get(offset));
      }
    } else {
      FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, null, sourceNamespace,
          sourceIdentifier);
      FSIterator<Annotation> it = TextAnnotation.getIterator(jcas);
      it = jcas.createFilteredIterator(it, cons);
      while (it.hasNext()) {
        // findEntities -> annotateEntities
        Annotation ann = it.next();
        Map<Offset, Set<String>> matches = matchText(ann.getCoveredText());
        for (Offset pos : matches.keySet()) {
          Offset offset = new Offset(pos.start() + ann.getBegin(), pos.end() + ann.getBegin());
          String match = docText.substring(offset.start(), offset.end());
          filtered += filter.process(jcas, buffer, match, offset, matches.get(pos));
        }
      }
    }
    for (SemanticAnnotation ann : buffer)
      ann.addToIndexes();
  }

  protected Map<Offset, Set<String>> matchText(String text) {
    return gazetteer.match(text);
  }

  /**
   * Iterate over the entity keys and offsets of all Gazetteer matches in the <code>text</code> of
   * the <code>annotation</code>.
   * <p>
   * Determine if the match <code>key</code> is exact, or requires resolving the entity
   * <code>key</code>, and calculate the (normalized) {@link Similarity#similarity(String, String)
   * string similarity} for the annotation confidence.
   */
  protected List<SemanticAnnotation> makeAnnotations(JCas jcas, String name, Set<String> ids,
      Offset offset) {
    List<SemanticAnnotation> anns = new LinkedList<SemanticAnnotation>();
    for (String dbId : ids) {
      Set<String> officialNames = gazetteer.get(dbId);
      if (officialNames.contains(name)) {
        anns.add(annotate(dbId, jcas, offset, 1.0));
      } else {
        double conf = 0.0;
        for (String n : officialNames)
          conf = Math.max(conf, measure.similarity(n, name));
        if (conf < minSimilarity) logger.log(Level.FINER,
            "dropping low-similarity match for {0} on ''{1}''", new String[] { dbId, name });
        else anns.add(annotate(dbId, jcas, offset, conf));
      }
    }
    return anns;
  }

  /**
   * Add a {@link SemanticAnnotation semantic annotation} for a DB ID with a given confidence
   * value.
   */
  protected SemanticAnnotation annotate(String id, JCas jcas, Offset offset, double confidence) {
    SemanticAnnotation entity = new SemanticAnnotation(jcas, offset);
    entity.setAnnotator(URI);
    entity.setConfidence(confidence);
    entity.setIdentifier(id);
    entity.setNamespace(entityNamespace);
    logger.log(Level.FINER, "detected {0}:{1} ({2})",
        new String[] { entityNamespace, id, Double.toString(confidence) });
    count++;
    return entity;
  }

  @Override
  public void destroy() {
    super.destroy();
    logger.log(Level.INFO, "tagged {0} {1} entities, stopword-filtered {2}",
        new String[] { Integer.toString(count), entityNamespace, Integer.toString(filtered) });
  }
}
