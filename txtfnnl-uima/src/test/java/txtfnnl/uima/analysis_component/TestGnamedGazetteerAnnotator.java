package txtfnnl.uima.analysis_component;

import static org.junit.Assert.assertEquals;

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

import txtfnnl.uima.resource.GnamedGazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

public class TestGnamedGazetteerAnnotator {
  AnalysisEngineDescription annotator;
  AnalysisEngine engine;
  File gazetteerResource;
  String url;
  String query = "SELECT id, taxon, name FROM entities";
  AnalysisEngineDescription descriptor;

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    tmpDb.deleteOnExit();
    DisableLogging.enableLogging(Level.WARNING);
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
    descriptor = GazetteerAnnotator.configure(
        "namespaceX",
        GnamedGazetteerResource.configure(url, "org.h2.Driver", query).boundaryMatch()
            .idMatching().create()).create();
  }

  private void createTable(String... names) throws SQLException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY, taxon INT, name VARCHAR)");
    stmt.close();
    final PreparedStatement insert = conn.prepareStatement("INSERT INTO entities VALUES(?, ?, ?)");
    for (int i = 0; i < names.length; ++i) {
      insert.setInt(1, i);
      insert.setInt(2, 123);
      insert.setString(3, names[i]);
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
    createTable("missed", "IL-1beta", "Hprt");
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(descriptor);
    final JCas jcas = makeJCas(ae, "that has IL-1\u03B2 and HPRT in it");
    ae.process(jcas);
    FSIterator<Annotation> it = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      assertEquals("namespaceX", ann.getNamespace());
      if (count == 0) {
        // Greek letter matching
        assertEquals(ann.toString(), "1", ann.getIdentifier());
        assertEquals(ann.toString(), 9, ann.getBegin());
        assertEquals(ann.toString(), 14, ann.getEnd());
//        assertTrue(Double.toString(ann.getConfidence()),
//            ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else if (count == 1) {
        // ID-matching
        assertEquals(ann.toString(), "1", ann.getIdentifier());
        assertEquals(ann.toString(), 12, ann.getBegin());
        assertEquals(ann.toString(), 13, ann.getEnd());
//        assertTrue(Double.toString(ann.getConfidence()),
//            ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else if (count == 2) {
        // Case-insensitive matching
        assertEquals(ann.toString(), "2", ann.getIdentifier());
        assertEquals(ann.toString(), 19, ann.getBegin());
        assertEquals(ann.toString(), 23, ann.getEnd());
//        assertTrue(Double.toString(ann.getConfidence()),
//            ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else {
        System.err.println(ann.toString());
      }
      ++count;
    }
    assertEquals(3, count);
  }
}
