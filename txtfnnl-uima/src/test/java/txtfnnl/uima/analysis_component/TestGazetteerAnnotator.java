package txtfnnl.uima.analysis_component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.resource.JdbcGazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

public class TestGazetteerAnnotator {
  AnalysisEngineDescription annotator;
  AnalysisEngine engine;
  File gazetteerResource;
  String url;
  String query = "SELECT id, name FROM entities";
  AnalysisEngineDescription descriptor;

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    tmpDb.deleteOnExit();
    DisableLogging.enableLogging(Level.WARNING);
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
    descriptor = GazetteerAnnotator.configure("entityNS",
        JdbcGazetteerResource.configure(url, "org.h2.Driver", query).create()).create();
  }

  private void createTable(String... names) throws SQLException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY,"
        + "                    name VARCHAR)");
    stmt.close();
    final PreparedStatement insert = conn.prepareStatement("INSERT INTO entities VALUES(?, ?)");
    for (int i = 0; i < names.length; ++i) {
      insert.setInt(1, i);
      insert.setString(2, names[i]);
      assertEquals(1, insert.executeUpdate());
    }
    insert.close();
    conn.close();
  }

  private JCas makeJCas(AnalysisEngine ae, String text) throws UIMAException {
    JCas baseCas = ae.newJCas();
    baseCas.setDocumentText(text);
    return baseCas;
  }

  @Test
  public void testProcess() throws SQLException, UIMAException {
    createTable("NAME1", "Name2");
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(descriptor);
    final JCas jcas = makeJCas(ae, "that has NAME2 in it");
    ae.process(jcas);
    FSIterator<Annotation> it = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      assertEquals("entityNS", ann.getNamespace());
      assertTrue(ann.getIdentifier(), "1".equals(ann.getIdentifier()));
      assertEquals(9, ann.getBegin());
      assertEquals(14, ann.getEnd());
      assertTrue(Double.toString(ann.getConfidence()),
          ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      ++count;
    }
    assertEquals(1, count);
  }
}
