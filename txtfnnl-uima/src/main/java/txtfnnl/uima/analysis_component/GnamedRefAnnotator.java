package txtfnnl.uima.analysis_component;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
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
import txtfnnl.uima.resource.JdbcConnectionResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public
class GnamedRefAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator. */
  public static final String URI = GnamedRefAnnotator.class.getName();
  /** The key used for the JdbcConnectionResource. */
  public static final String MODEL_KEY_GNAMED_JDBC_CONNECTOR = "GnamedJdbcConnector";
  @ExternalResource(key = MODEL_KEY_GNAMED_JDBC_CONNECTOR)
  private JdbcConnectionResource connector;
  public static final String PARAM_REF_QUERY_BASE = "RefQueryBase";
  @ConfigurationParameter(name = PARAM_REF_QUERY_BASE,
                          description = "SQL query to fetch a list of (entity ID, accession) pairs from entity_refs using target namespace and an entity ID array as parameters",
                          defaultValue = "SELECT id, accession FROM gene_refs WHERE namespace = ? AND id = any( ? )")
  private String refQueryBase;
  public static final String PARAM_REF_NAMESPACE = "RefNamespace";
  @ConfigurationParameter(name = PARAM_REF_NAMESPACE, description = "target namespace to map to",
                          defaultValue = "gi")
  private String refNamespace;
  public static final String PARAM_ANNOTATOR_URI = "AnnotatorUri";
  @ConfigurationParameter(name = PARAM_ANNOTATOR_URI,
                          description = "URI of the gnamed entity annotator")
  private String annotatorUri;
  public static final String PARAM_ENTITY_NAMESPACE = "EntityNamespace";
  @ConfigurationParameter(name = PARAM_ENTITY_NAMESPACE, defaultValue = "gene",
                          description = "source (annotated) namespace; either 'gene' or 'protein'")
  private String entityNamespace;
  // === INTERNAL STATE === //
  /** The gnamed DB connection. */
  protected Connection conn;
  /** The prepared statement of the mapping query. */
  protected PreparedStatement stmt;
  /** The Annotator's logger instance. */
  protected Logger logger;
  /** Counter for the number of made mappings. */
  int count = 0;

  public static
  class Builder extends AnalysisComponentBuilder {
    protected
    Builder(Class<? extends AnalysisComponent> klass,
            ExternalResourceDescription gnamedJdbcDescription) {
      super(klass);
      setRequiredParameter(MODEL_KEY_GNAMED_JDBC_CONNECTOR, gnamedJdbcDescription);
    }

    public
    Builder(ExternalResourceDescription gnamedJdbcDescription) {
      this(GnamedRefAnnotator.class, gnamedJdbcDescription);
    }

    /** Switch all optional parameters from mapping Entrez genes to UniProt proteins. */
    public
    void proteins() {
      setOptionalParameter(PARAM_ENTITY_NAMESPACE, "protein");
      setOptionalParameter(
          PARAM_REF_QUERY_BASE,
          "SELECT id, accession FROM protein_refs WHERE namespace = ? AND id IN ?"
      );
      setOptionalParameter(PARAM_REF_NAMESPACE, "uni");
    }

    public
    void setRefNamespace(String ns) {
      setOptionalParameter(PARAM_REF_NAMESPACE, ns);
    }

    public
    void setAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_ANNOTATOR_URI, uri);
    }

    public
    void setEntityNamespace(String ns) {
      setOptionalParameter(PARAM_ENTITY_NAMESPACE, ns);
    }

    public
    void setSqlQuery(String query) {
      setOptionalParameter(PARAM_REF_QUERY_BASE, query);
    }

  }

  public static
  Builder configure(ExternalResourceDescription gnamedJdbcDescription) {
    return new Builder(gnamedJdbcDescription);
  }

  @Override
  public
  void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    try {
      conn = connector.getConnection();
      stmt = conn.prepareStatement(refQueryBase);
    } catch (final SQLException e) {
      throw new ResourceInitializationException(e);
    }

    logger.log(
        Level.CONFIG, "mapping ''%s'' annotations from %s to ''%s'' IDs", new String[] {
        entityNamespace, annotatorUri == null ? "anywhere" : annotatorUri, refNamespace
    }
    );
  }

  @Override
  public
  void process(JCas jcas) throws AnalysisEngineProcessException {
    Map<String, String> mappings = null;
    List<SemanticAnnotation> annotations = collect(jcas);
    Set<String> entity_ids = new HashSet<String>();

    for (SemanticAnnotation ann : annotations)
      entity_ids.add(ann.getIdentifier());

    try {
      mappings = map(entity_ids);
    } catch (final SQLException e) {
      throw new AnalysisEngineProcessException(e);
    }

    for (SemanticAnnotation ann : annotations)
      if (mappings.containsKey(ann.getIdentifier()))
        update(jcas, ann, mappings.get(ann.getIdentifier()));
  }

  List<SemanticAnnotation> collect(JCas jcas) {
    List<SemanticAnnotation> annotations = new LinkedList<SemanticAnnotation>();
    FSIterator<Annotation> it = SemanticAnnotation.getIterator(jcas);
    FSMatchConstraint cons = SemanticAnnotation.makeConstraint(jcas, annotatorUri, entityNamespace);
    it = jcas.createFilteredIterator(it, cons);
    while (it.hasNext()) annotations.add((SemanticAnnotation) it.next());
    return annotations;
  }

  Map<String, String> map(Set<String> entity_ids) throws SQLException {
    Map<String, String> mappings = new HashMap<String, String>();
    stmt.setString(1, refNamespace);
    stmt.setArray(2, conn.createArrayOf("bigint", entity_ids.toArray()));
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
      String entity_id = Integer.toString(rs.getInt(1));
      mappings.put(entity_id, rs.getString(2));
    }
    return mappings;
  }

  void update(JCas jcas, SemanticAnnotation ann, String refId) {
    Property ref = new Property(jcas);
    ref.setName(refNamespace);
    ref.setValue(refId);
    count++;

    FSArray oldProps = ann.getProperties();
    FSArray newProps;
    if (oldProps == null || oldProps.size() == 0) {
      newProps = new FSArray(jcas, 1);
      newProps.set(0, ref);
    } else {
      newProps = new FSArray(jcas, oldProps.size() + 1);
      for (int i = 0; i < oldProps.size(); ++i)
        newProps.set(i, oldProps.get(i));
      newProps.set(oldProps.size(), ref);
    }
    ann.setProperties(newProps);
  }

  @Override
  public
  void destroy() {
    super.destroy();
    logger.log(
        Level.CONFIG, "mapped %i ''%s'' annotations to ''%s'' IDs", new Object[] {
        count, entityNamespace, refNamespace
    }
    );
  }
}