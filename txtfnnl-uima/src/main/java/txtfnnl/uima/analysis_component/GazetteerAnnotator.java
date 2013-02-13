package txtfnnl.uima.analysis_component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
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
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.UIMAUtils;
import txtfnnl.uima.Views;
import txtfnnl.uima.resource.JdbcGazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;

/**
 * A "NER" to detect the presence of names from a DB of entities in particular text spans. This AE
 * requires a DB with entity names mapped to some form of entity ID.
 * <p>
 * This AE first loads all ID, name pairs from a database using a predefined query and creates a
 * regular expression Pattern from those names. Then, names are matched on particular
 * {@link TextAnnotation spans} defined via their {@link #PARAM_SOURCE_NAMESPACE namespace} (and
 * optionally their {@link #PARAM_SOURCE_IDENTIFIER identifier}, too) of the documents via that
 * regular expression. The IDs mapping to those matched names are annotated on the SOFA as
 * {@link SemanticAnnotation semantic annotations} with a configurable
 * {@link #PARAM_ENTITY_NAMESPACE namespace} and a confidence value depending on the uniqueness of
 * the mapping.
 * <p>
 * The <b>Entity DB</b> has to be a database that can produce a list of (String) IDs and names and
 * by executing a {@link JdbcGazetteerResource#PARAM_QUERY_SQL SQL query} provided during AE setup.
 * The Entities' {@link #PARAM_ENTITY_NAMESPACE namespace} for the entities is used to create the
 * resulting {@link SemanticAnnotation semantic annotations}. The simplest possible SQL query might
 * be configures like this:
 * 
 * <pre>
 * SELECT id, name FROM entities;
 * </pre>
 * 
 * For name matching, the name must match exactly to the (Unicode) letters and numbers, while the
 * type of separator to use between different letter and number tokens (defined as consecutive
 * spans of code points with the same Unicode category or a capitalized token) may be any character
 * except letters and numbers. If such a "normalized" name matches multiple DB IDs, the confidence
 * of each annotation is reduced proportionally. If the name only matches after ignoring the
 * positions of the required separators, the confidence is reduced proportionally to the number of
 * separators the name contained. Finally, if only a case-independent version of the name matches,
 * the confidence is reduced proportionally to the number case adjustments.
 * 
 * @author Florian Leitner
 */
public class GazetteerAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator (namespace and ID are defined dynamically). */
  public static final String URI = GazetteerAnnotator.class.getName();
  /** The namespace to use for the {@link SemanticAnnotation SemanticAnnotations}. */
  public static final String PARAM_ENTITY_NAMESPACE = "EntityNamespace";
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE, mandatory = true)
  private String entityNamespace;
  /**
   * The namespace of the {@link TextAnnotation TextAnnotations} containing the relevant text.
   */
  public static final String PARAM_SOURCE_NAMESPACE = "SourceNamespace";
  @ConfigurationParameter(name = PARAM_SOURCE_NAMESPACE, mandatory = true)
  private String sourceNamespace;
  /**
   * The optional identifier of the {@link TextAnnotation TextAnnotations} containing the relevant
   * text. If not provided, all passages with the matching source namespace (only) are annotated.
   */
  public static final String PARAM_SOURCE_IDENTIFIER = "SourceIdentifier";
  @ConfigurationParameter(name = PARAM_SOURCE_IDENTIFIER, mandatory = false)
  private String sourceIdentifier = null;
  /** The key used for the JdbcGazetteerResource. */
  public static final String MODEL_KEY_JDBC_GAZETTEER = "JdbcGazetteer";
  @ExternalResource(key = MODEL_KEY_JDBC_GAZETTEER)
  private JdbcGazetteerResource gazetteer;
  private Logger logger;

  /**
   * Configure a {@link GazetteerAnnotator} description.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param sourceNamespace of the {@link TextAnnotation TextAnnotations} containing the text
   *        elements to process
   * @param sourceIdentifier of the {@link TextAnnotation TextAnnotations} containing the text
   *        elements to process (optional)
   * @param sqlQuery to use for fetching the entity names from the JDBC-connected DB
   * @param idMatching whether to match the DB IDs themselves, too
   * @param exactCaseMatching whether to only detect exact case matches
   * @param dbUrl of the entity DB to connect to
   * @param driverClass to use for connecting to the DB
   * @param dbUsername to use for connecting to the DB (optional)
   * @param dbPassword to use for connecting to the DB (optional)
   * @return a configured AE description
   * @throws ResourceInitializationException
   */
  public static AnalysisEngineDescription configure(String entityNamespace,
      String sourceNamespace, String sourceIdentifier, String sqlQuery, boolean idMatching,
      boolean exactCaseMatching, String dbUrl, String driverClass, String dbUsername,
      String dbPassword) throws UIMAException, IOException {
    final ExternalResourceDescription gazetteer = JdbcGazetteerResource.configure(dbUrl, sqlQuery,
        idMatching, exactCaseMatching, driverClass, dbUsername, dbPassword, -1, null, true);
    return GazetteerAnnotator.configure(entityNamespace, sourceNamespace, sourceIdentifier,
        gazetteer);
  }

  /**
   * Configure a {@link GazetteerAnnotator} description using only the essential configuration
   * parameters for this AE.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param sourceNamespace of the {@link SemanticAnnotation SemanticAnnotations} containing the
   *        text to search/match
   * @param sqlQuery to use for fetching the entity names from the JDBC-connected DB
   * @param dbUrl of the entity DB to connect to
   * @param driverClass to use for connecting to the DB
   * @return a configured AE description
   * @throws ResourceInitializationException
   */
  public static AnalysisEngineDescription configure(String entityNamespace,
      String sourceNamespace, String sourceIdentifier, String sqlQuery, String dbUrl,
      String driverClass) throws UIMAException, IOException {
    return GazetteerAnnotator.configure(entityNamespace, sourceNamespace, null, sqlQuery, true,
        false, dbUrl, driverClass, null, null);
  }

  /**
   * Configure an {@link GazetteerAnnotator} description with an already configured DB gazetteer
   * resource description.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param sourceNamespace of the {@link SemanticAnnotation SemanticAnnotations} containing the
   *        text to search/match
   * @param sourceIdentifier of the {@link SemanticAnnotation SemanticAnnotations} containing the
   *        text to search/match (optional)
   * @param jdbcGazetteerResource a gazetteer for mapping text entities to DB IDs
   * @return a configured AE description
   * @throws ResourceInitializationException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final String entityNamespace,
      final String sourceNamespace, final String sourceIdentifier,
      final ExternalResourceDescription jdbcGazetteerResource) throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(GazetteerAnnotator.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_ENTITY_NAMESPACE, entityNamespace);
            put(PARAM_SOURCE_NAMESPACE, sourceNamespace);
            put(PARAM_SOURCE_IDENTIFIER, sourceIdentifier);
            put(MODEL_KEY_JDBC_GAZETTEER, jdbcGazetteerResource);
          }
        }));
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.INFO, "initialized with {0} entities", gazetteer.size());
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, null, sourceNamespace,
        sourceIdentifier);
    FSIterator<Annotation> it = TextAnnotation.getIterator(jcas);
    it = jcas.createFilteredIterator(it, cons);
    while (it.hasNext())
      findEntities(jcas, it.next());
  }

  /**
   * Iterate over the entity keys and offsets of all gazetteer matches in the text of the
   * <code>annotation</code>.
   */
  private void findEntities(JCas jcas, Annotation annotation) {
    int base = annotation.getBegin();
    Map<JdbcGazetteerResource.Offset, String> matches = gazetteer.match(annotation
        .getCoveredText());
    for (JdbcGazetteerResource.Offset offset : matches.keySet())
      annotateEntities(jcas, matches.get(offset), base + offset.begin, base + offset.end);
  }

  /**
   * Determine if the match is exact, only after normalizing (removing) the separators, without
   * case sensitive matching, or both.
   */
  private void annotateEntities(JCas jcas, String entityKey, int begin, int end) {
    if (gazetteer.containsKey(entityKey)) {
      annotateAll(jcas, gazetteer.get(entityKey), begin, end, 1.0);
    } else if (gazetteer.containsNormal(entityKey.replace("-", ""))) {
      String normalKey = entityKey.replace("-", "");
      // set base confidence to (2 / 2 + numSep)
      annotateAll(jcas, gazetteer.getNormal(normalKey), begin, end,
          2.0 / (2.0 + entityKey.length() - normalKey.length()));
    } else {
      String loweCaseKey = String.format("~%s", entityKey.toLowerCase());
      if (!gazetteer.usesExactCaseMatching() && gazetteer.containsKey(loweCaseKey)) {
        caseInsensitiveMatch(jcas, entityKey, gazetteer.get(loweCaseKey), begin, end);
      } else if (!gazetteer.usesExactCaseMatching() &&
          gazetteer.containsNormal(loweCaseKey.replace("-", ""))) {
        caseInsensitiveMatch(jcas, entityKey, gazetteer.getNormal(loweCaseKey.replace("-", "")),
            begin, end);
      } else {
        logger.log(Level.WARNING, "unmatched key ''" + entityKey + "'' from entity ''" +
            jcas.getDocumentText().substring(begin, end));
      }
    }
  }

  /** Annotate all the given database IDs at the given offset, equally spreading the confidence. */
  private void annotateAll(JCas jcas, Set<String> dbIds, int begin, int end, double confidence) {
    confidence /= dbIds.size(); // spread probability across all IDs
    for (String id : dbIds)
      annotate(id, jcas, begin, end, confidence);
  }

  /**
   * Annotate all database IDs that can be found for the set of keys at the given offset, equally
   * spreading the confidence between all database IDs, and reducing each ID's confidence
   * proportionally to the {@link #distanceMod(CharSequence, CharSequence) distance} of its key to
   * the originally matched entity key.
   */
  private void caseInsensitiveMatch(JCas jcas, String entityKey, Set<String> keys, int begin,
      int end) {
    Map<String, String> idToKey = new HashMap<String, String>();
    for (String key : keys) {
      for (String id : gazetteer.get(key))
        if (!idToKey.containsKey(id) ||
            distanceMod(entityKey, idToKey.get(id)) < distanceMod(entityKey, key))
          idToKey.put(id, key);
    }
    double confidence = 1.0 / idToKey.size(); // spread probability across all IDs
    for (String id : idToKey.keySet())
      // reduce per-id confidence proportionally to edit distance from the original entity key
      annotate(id, jcas, begin, end, confidence * distanceMod(entityKey, idToKey.get(id)));
  }

  /** Return a String distance-based confidence modifier. */
  private double distanceMod(String a, String b) {
    // exact matches: distance modifier = 1
    if (a.equals(b)) return 1.0;
    // Abc1 and ABC1 cases: distance modifier = 1/2
    if (a.toUpperCase().equals(b) || b.toUpperCase().equals(a)) return 0.5;
    // otherwise, calculate the Levenshtein distance L
    int[][] distance = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++)
      distance[i][0] = i;
    for (int j = 1; j <= b.length(); j++)
      distance[0][j] = j;
    for (int i = 1; i <= a.length(); i++)
      for (int j = 1; j <= b.length(); j++)
        distance[i][j] = minimum(distance[i - 1][j] + 1, distance[i][j - 1] + 1,
            distance[i - 1][j - 1] + ((a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1));
    // and return a distance modifier of (2 / [2 + L])
    return 2.0 / ((double) 2 + distance[a.length()][b.length()]);
  }

  /** Return the smallest of three integers. */
  private static int minimum(int a, int b, int c) {
    return Math.min(Math.min(a, b), c);
  }

  /**
   * Add a {@link SemanticAnnotation semantic annotation} for a DB ID at the given offset and with
   * the given confidence value.
   */
  private void annotate(String id, JCas jcas, int begin, int end, double confidence) {
    SemanticAnnotation entity = new SemanticAnnotation(jcas, begin, end);
    entity.setAnnotator(URI);
    entity.setConfidence(confidence);
    entity.setIdentifier(id);
    entity.setNamespace(entityNamespace);
    entity.addToIndexes();
  }
}
