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

import txtfnnl.uima.Views;
import txtfnnl.uima.resource.JdbcGazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.utils.UIMAUtils;

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
 * <b>Resources:</b>
 * <dl>
 * <dt>{@link KnownEvidenceAnnotator#MODEL_KEY_EVIDENCE_STRING_MAP Evidence String Map}</dt>
 * <dd>a TSV file of known entity IDs per input file</dd>
 * <dt>{@link GazetteerAnnotator#MODEL_KEY_JDBC_CONNECTION Entity Name DB}</dt>
 * <dd>a JDBC-enabled DB of names for the entities grouped by entity IDs</dd>
 * </dl>
 * The <b>Evidence String Map</b> resource has to be a TSV file with the following columns:
 * <ol>
 * <li>Document ID: SOFA URI basename (without the file suffix)</li>
 * <li>Entity Type: will be used as the identifier feature value of the {@link SemanticAnnotation
 * SemanticAnnotations}, and these SemanticAnnotations will use the {@link #PARAM_NAMESPACE
 * PARAM_NAMESPACE} parameter set for the AE as the namespace feature value</li>
 * <li>Entity Namespace: as used in the <i>Entity Name DB</i> (and not to be confused with the <i>
 * <code>PARAM_NAMESPACE</code></i> parameter of this Annotator)</li>
 * <li>Entity ID: as used in the <i>Entity Name DB</i></li>
 * </ol>
 * The <b>Entity Name DB</b> resource has to be a database that can produce a list of (String)
 * names for a given Entity Namespace and ID from the <i>Evidence String Map</i> by executing all
 * <i> {@link #PARAM_QUERIES}</i> provided during AE setup. The Entity Namespace/ID pairs from the
 * <i>Evidence String Map</i> will be used as positional parameters in the DB queries (Entity
 * Namespace first, then ID). For example, the simplest possible SQL query might be configures like
 * this:
 * 
 * <pre>
 * SELECT name FROM entities WHERE namespace=? AND identifier=?
 * </pre>
 * 
 * I.e., in this example query, the Entity Name DB would have a table "entities" with three
 * columns, "name", "namespace", and "identifier". As Entity names might be found in multiple
 * tables, it is possible to configure multiple SQL queries for this AE.
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
   * @param caseInsensitiveMatching whether optionally use case insensitive matching, too
   * @param dbUrl of the entity DB to connect to
   * @param driverClass to use for connecting to the DB
   * @param dbUsername to use for connecting to the DB (optional)
   * @param dbPassword to use for connecting to the DB (optional)
   * @return a configured AE description
   * @throws ResourceInitializationException
   */
  public static AnalysisEngineDescription configure(String entityNamespace,
      String sourceNamespace, String sourceIdentifier, String sqlQuery,
      boolean caseInsensitiveMatching, String dbUrl, String driverClass, String dbUsername,
      String dbPassword) throws UIMAException, IOException {
    final ExternalResourceDescription gazetteer = JdbcGazetteerResource.configure(dbUrl, sqlQuery,
        !caseInsensitiveMatching, driverClass, dbUsername, dbPassword, -1, null, true);
    return GazetteerAnnotator.configure(entityNamespace, sourceNamespace, sourceIdentifier,
        gazetteer);
  }

  /**
   * Configure a {@link GazetteerAnnotator} description using only the essential
   * configuration parameters for this AE.
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
    return GazetteerAnnotator.configure(entityNamespace, sourceNamespace, null, sqlQuery,
        false, dbUrl, driverClass, null, null);
  }

  /**
   * Configure an {@link GazetteerAnnotator} description with an already configured DB
   * gazetteer resource description.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param sourceNamespace of the {@link SemanticAnnotation SemanticAnnotations} containing the
   *        text to search/match
   * @param sourceIdentifier of the {@link SemanticAnnotation SemanticAnnotations} containing the
   *        text to search/match (optional)
   * @param jdbcGazetteerResource a gazetteer that can match the entities in the text
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

  private void findEntities(JCas jcas, Annotation ann) {
    int base = ann.getBegin();
    Map<JdbcGazetteerResource.Offset, String> matches = gazetteer.match(ann.getCoveredText());
    for (JdbcGazetteerResource.Offset offset : matches.keySet())
      annotateEntities(jcas, matches.get(offset), base + offset.begin, base + offset.end);
  }

  private void annotateEntities(JCas jcas, String entityKey, int begin, int end) {
    Set<String> dbIds = gazetteer.get(entityKey);
    double confidence = 1.0;
    if (dbIds == null) {
      // the actual separation of the matched name is not matching the positions of the separators
      // in the original name
      confidence /= divisor(entityKey); // reduce conf. proportionally to number of separators
      dbIds = gazetteer.get(entityKey.replace("-", ""));
      if (dbIds == null)
        logger.log(Level.SEVERE, "unknown entity key ''" + entityKey + "'' from ''" +
            jcas.getDocumentText().substring(begin, end) + "''");
    }
    confidence /= dbIds.size();
    if (entityKey.charAt(0) == '~') confidence *= 0.5;
    for (String id : dbIds) {
      SemanticAnnotation entity = new SemanticAnnotation(jcas, begin, end);
      entity.setAnnotator(URI);
      entity.setConfidence(confidence);
      entity.setIdentifier(id);
      entity.setNamespace(entityNamespace);
      entity.addToIndexes();
    }
  }

  private double divisor(String entityKey) {
    int div = 1;
    int idx = -1;
    while ((idx = entityKey.indexOf('-', idx + 1)) != -1)
      ++div;
    return (double) div;
  }
}
