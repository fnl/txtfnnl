package txtfnnl.uima.analysis_component;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.junit.Before;
import org.junit.Test;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;
import txtfnnl.uima.resource.GnamedGazetteerResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestGnamedGazetteerAnnotator {
  String url;
  String query = "SELECT id, taxon, name FROM entities";

  @Before
  public void setUp() throws Exception {
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    tmpDb.deleteOnExit();
    DisableLogging.enableLogging(Level.WARNING);
    url = "jdbc:h2:" + tmpDb.getCanonicalPath();
  }

  private void createTable(String... names) throws SQLException {
    final Connection conn = DriverManager.getConnection(url);
    final Statement stmt = conn.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(id INT PRIMARY KEY, taxon INT, name VARCHAR)");
    stmt.close();
    final PreparedStatement insert = conn.prepareStatement("INSERT INTO entities VALUES(?, ?, ?)");
    for (int i = 0; i < names.length; ++i) {
      insert.setInt(1, i);
      insert.setInt(2, Integer.parseInt(names[i].substring(names[i].indexOf(":tax") + 4)));
      insert.setString(3, names[i].substring(0, names[i].indexOf(":tax")));
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
    AnalysisEngineDescription descriptor = GazetteerAnnotator.configure("namespaceX", GnamedGazetteerResource.configure(url, "org.h2.Driver", query).boundaryMatch().idMatching().create()).create();
    createTable("missed:tax1", "IL-1beta:tax1", "Hprt:tax1", "hprt:tax2");
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(descriptor);
    final JCas jcas = makeJCas(ae, "that has IL-1\u03B2 and HPRT in it");
    ae.process(jcas);
    FSIterator<Annotation> it = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    boolean foundTax1 = false;
    boolean foundTax2 = false;
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      assertEquals("namespaceX", ann.getNamespace());
      if (count == 0) {
        // Greek letter matching
        assertEquals(ann.toString(), "1", ann.getIdentifier());
        assertEquals(ann.toString(), 9, ann.getBegin());
        assertEquals(ann.toString(), 14, ann.getEnd());
        // assertTrue(Double.toString(ann.getConfidence()),
        // ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else if (count == 1) {
        // ID-matching
        assertEquals(ann.toString(), "1", ann.getIdentifier());
        assertEquals(ann.toString(), 12, ann.getBegin());
        assertEquals(ann.toString(), 13, ann.getEnd());
        // assertTrue(Double.toString(ann.getConfidence()),
        // ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else if (count > 1) {
        // Case-insensitive matching
        // Alt. taxon matching
        if (!(foundTax1 || foundTax2)) {
          if ("2".equals(ann.getIdentifier())) foundTax1 = true;
          else if ("3".equals(ann.getIdentifier())) foundTax2 = true;
          else fail(ann.toString());
        } else if (foundTax1) {
          assertEquals(ann.toString(), "3", ann.getIdentifier());
          foundTax2 = true;
        } else if (foundTax2) {
          assertEquals(ann.toString(), "2", ann.getIdentifier());
          foundTax1 = true;
        } else {
          fail(ann.toString());
        }
        assertEquals(ann.toString(), 19, ann.getBegin());
        assertEquals(ann.toString(), 23, ann.getEnd());
        // assertTrue(Double.toString(ann.getConfidence()),
        // ann.getConfidence() >= 3.0 / 5 && ann.getConfidence() != 1.0);
      } else {
        System.err.println(ann.toString());
      }
      ++count;
    }
    assertEquals(4, count);
    if (!foundTax1) {
      fail("did not annotate tax1");
    } else if (!foundTax2) {
      fail("did not annotated tax2");
    }
  }

  @Test
  public void testExpansion() throws SQLException, UIMAException {
    AnalysisEngineDescription descriptor = GazetteerAnnotator.configure("namespaceX", GnamedGazetteerResource.configure(url, "org.h2.Driver", query).boundaryMatch().generateVariants().create()).create();
    createTable("gene7:tax1", "gene8:tax1", "gene9:tax1", "proteinA:tax2", "proteinB:tax2");
    final AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(descriptor);
    final JCas jcas = makeJCas(ae, " gene7-9 : gene7/9 and proteinA-B");
    ae.process(jcas);
    FSIterator<Annotation> it = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      if (ann.getOffset().start() == 1) {
        if (ann.getOffset().end() == 6) {
          assertEquals(ann.toString(), "0", ann.getIdentifier());
        } else if (ann.getOffset().end() == 8) {
          if (!("1".equals(ann.getIdentifier()) || "2".equals(ann.getIdentifier()))) fail(ann.toString());
        } else {
          fail(ann.toString());
        }
      } else if (ann.getOffset().start() == 11) {
        if (ann.getOffset().end() == 16) assertEquals(ann.toString(), "0", ann.getIdentifier());
        else if (ann.getOffset().end() == 18) assertEquals(ann.toString(), "2", ann.getIdentifier());
        else fail(ann.toString());
      } else if (ann.getOffset().start() == 23) {
        if (ann.getOffset().end() == 31) assertEquals(ann.toString(), "3", ann.getIdentifier());
        else if (ann.getOffset().end() == 33) assertEquals(ann.toString(), "4", ann.getIdentifier());
        else fail(ann.toString());
      } else {
        fail(ann.toString());
      }
      ++count;
      //System.out.println(ann.toString());
    }
    assertEquals(7, count);
  }
}
