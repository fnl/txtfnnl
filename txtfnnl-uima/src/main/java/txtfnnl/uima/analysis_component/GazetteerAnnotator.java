package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
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
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.GazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.Offset;
import txtfnnl.utils.stringsim.LeitnerLevenshtein;
import txtfnnl.utils.stringsim.Similarity;

/**
 * An AE to create normalized (ID'd) annotations of names of entities in a Gazetteer with its own
 * matching method. This AE requires a <i>Gazetteer</i> with entity <i>names</i> mapped to
 * <i>IDs</i> and implementing a matching strategy for the Strings it will receive.
 * <p>
 * Text {@link TextAnnotation segments} are {@link GazetteerResource#match(String) matched} via a
 * {@link GazetteerResource}. The text segments used for matching can be selected on their
 * {@link #PARAM_TEXT_NAMESPACE namespace} and, optionally, {@link #PARAM_TEXT_IDENTIFIER
 * identifier}. The {@link SemanticAnnotation semantic annotations} created on matched spans are
 * annotated with a configurable {@link #PARAM_ENTITY_NAMESPACE namespace} and the identifier
 * provided by the Gazetteer. A confidence value is set on the semantic annotations based on the
 * string similarity of the match.
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
   * against the Gazetteer (default: match the whole document text).
   */
  public static final String PARAM_TEXT_NAMESPACE = "TextNamespace";
  @ConfigurationParameter(name = PARAM_TEXT_NAMESPACE, mandatory = false)
  protected String textNamespace = null;
  /**
   * The (optional) identifier of the {@link TextAnnotation annotations} containing the relevant
   * text to match against the Gazetteer (default: match the whole document text)..
   * <p>
   * This parameter is only effective if {@link #PARAM_TEXT_NAMESPACE} is set, too.
   */
  public static final String PARAM_TEXT_IDENTIFIER = "TextIdentifier";
  @ConfigurationParameter(name = PARAM_TEXT_IDENTIFIER, mandatory = false)
  protected String textIdentifier = null;
  /**
   * The minimum string similarity value required to annotate a match.
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
  protected FilterStrategy filter;
  /** The GazetteerResource used for entity matching. */
  public static final String MODEL_KEY_GAZETTEER = "Gazetteer";
  @ExternalResource(key = MODEL_KEY_GAZETTEER)
  protected GazetteerResource gazetteer;
  private Similarity measure = LeitnerLevenshtein.INSTANCE; // TODO: make configurable?
  protected Logger logger;
  private int count;
  protected int filtered;

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
     * Define the {@link TextAnnotation annotation} namespace to get the covered text from for the
     * Gazetteer's matcher.
     */
    public Builder setTextNamespace(String namespace) {
      setOptionalParameter(PARAM_TEXT_NAMESPACE, namespace);
      return this;
    }

    /**
     * Define a {@link TextAnnotation TextAnnotation} identifier to limiting the Gazetteer's text
     * to scan.
     */
    public Builder setTextIdentifier(String identifier) {
      setOptionalParameter(PARAM_TEXT_IDENTIFIER, identifier);
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
    /** Return <code>true</code> if the match may be processed. */
    public boolean process(String match);
  }

  protected static class SelectWhitelist implements FilterStrategy {
    private Set<String> allowedMatches;

    public SelectWhitelist(String[] whitelist) {
      allowedMatches = new HashSet<String>(Arrays.asList(whitelist));
    }

    public boolean process(String match) {
      return (allowedMatches.contains(match));
    }
  }

  protected static class FilterBlacklist implements FilterStrategy {
    private Set<String> filteredMatches;

    public FilterBlacklist(String[] blacklist) {
      filteredMatches = new HashSet<String>(Arrays.asList(blacklist));
    }

    public boolean process(String match) {
      return (!filteredMatches.contains(match));
    }
  }

  protected static class SelectAndFilter implements FilterStrategy {
    private Set<String> allowedMatches;
    private Set<String> filteredMatches;

    public SelectAndFilter(String[] whitelist, String[] blacklist) {
      allowedMatches = new HashSet<String>(Arrays.asList(whitelist));
      filteredMatches = new HashSet<String>(Arrays.asList(blacklist));
    }

    public boolean process(String match) {
      return (allowedMatches.contains(match) && !filteredMatches.contains(match));
    }
  }

  protected static class NoStrategy implements FilterStrategy {
    public NoStrategy() {}

    public boolean process(String match) {
      return true;
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
          blacklist);
      else filter = new SelectWhitelist(whitelist);
    } else if (blacklist != null && blacklist.length > 0) {
      filter = new FilterBlacklist(blacklist);
    } else {
      filter = new NoStrategy();
    }
    count = 0;
    filtered = 0;
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String docText = jcas.getDocumentText();
    List<SemanticAnnotation> buffer = new LinkedList<SemanticAnnotation>();
    if (textNamespace == null) {
      Map<Offset, List<String>> matches = gazetteer.match(docText);
      for (Offset offset : matches.keySet()) {
        String match = docText.substring(offset.start(), offset.end());
        if (filter.process(match))
          buffer.addAll(makeAnnotations(jcas, match, matches.get(offset), offset));
      }
    } else {
      FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, null, textNamespace,
          textIdentifier);
      FSIterator<Annotation> it = TextAnnotation.getIterator(jcas);
      it = jcas.createFilteredIterator(it, cons);
      while (it.hasNext()) {
        // findEntities -> annotateEntities
        Annotation ann = it.next();
        String txt = ann.getCoveredText();
        Map<Offset, List<String>> matches = gazetteer.match(txt);
        for (Offset pos : matches.keySet()) {
          String match = txt.substring(pos.start(), pos.end());
          if (filter.process(match))
            buffer.addAll(makeAnnotations(jcas, match, matches.get(pos), new Offset(pos.start() +
                ann.getBegin(), pos.end() + ann.getBegin())));
        }
      }
    }
    for (SemanticAnnotation ann : buffer)
      ann.addToIndexes();
  }

  /**
   * Iterate over the entity names mapped to each of the <code>IDs</code> of a <code>match</code>
   * and calculate the highest similarity. Create an annotation at the given <code>offset</code> if
   * the similarity constraint is met.
   * 
   * @return the list of newly created {@link SemanticAnnotation annotations}.
   */
  private List<SemanticAnnotation> makeAnnotations(JCas jcas, String match, List<String> ids,
      Offset offset) {
    List<SemanticAnnotation> anns = new LinkedList<SemanticAnnotation>();
    for (String id : ids) {
      SemanticAnnotation ann = makeAnnotation(jcas, match, id, offset);
      if (ann != null) anns.add(ann);
    }
    return anns;
  }

  /**
   * Iterate over the entity names mapped to the <code>ID</code> of a <code>match</code> and
   * calculate the highest similarity. Create an annotation at the given <code>offset</code> if the
   * similarity constraint is met.
   * 
   * @return the newly created {@link SemanticAnnotation annotation} or <code>null</code>.
   */
  protected SemanticAnnotation makeAnnotation(JCas jcas, String match, String id, Offset offset) {
    String[] officialNames = gazetteer.get(id);
    SemanticAnnotation ann = null;
    if (ArrayUtils.contains(officialNames, match)) {
      ann = annotate(id, jcas, offset, 1.0, match);
    } else {
      double conf = -1.0;
      String name = null;
      for (String n : officialNames) {
        double sim = measure.similarity(n, match);
        if (sim > conf) {
          name = n;
          conf = sim;
        }
      }
      if (conf < minSimilarity) logger.log(Level.FINER,
          "dropping low-similarity match ''{0}'' for {1} on ''{2}''", new String[] { name, id, match });
      else ann = annotate(id, jcas, offset, conf, name);
    }
    return ann;
  }

  /**
   * Add a {@link SemanticAnnotation semantic annotation} for a DB ID with a given confidence
   * value.
   */
  protected SemanticAnnotation annotate(String id, JCas jcas, Offset offset, double confidence,
                                        String name) {
    SemanticAnnotation entity = new SemanticAnnotation(jcas, offset);
    entity.setAnnotator(URI);
    entity.setConfidence(confidence);
    entity.setIdentifier(id);
    entity.setNamespace(entityNamespace);
    Property match = new Property(jcas);
    match.setName("name");
    match.setValue(name);
    entity.addProperty(jcas, match);
    count++;
    return entity;
  }

  @Override
  public void destroy() {
    super.destroy();
    logger.log(Level.CONFIG, "detected {0} {1} entities and filtered {2}",
        new String[] { Integer.toString(count), entityNamespace, Integer.toString(filtered) });
  }
}
