package txtfnnl.uima.analysis_component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.uima.tcas.SemanticAnnotation;

public class TestKnownEntityAnnotator {
  AnalysisEngineDescription annotatorDesc; // AE descriptor under test
  AnalysisEngine annotator; // AE under test
  Connection jdbc_resource; // example named entity DB resource
  File textFile; // example analysis file
  String docId; // doc ID of example analysis file
  JCas baseJCas;
  JCas textJCas;
  JCas rawJCas;
  static final String SEMANTIC_ANNOTATION_NAMESPACE = "http://example.com/namespace/";
  static final String SEMANTIC_ANNOTATION_IDENTIFIER = "entity type";
  static final String ENTITY_NAME = "entity mention";
  static final String ENTITY_NS = "namespace Y";
  static final String ENTITY_ID = "identifier Y";

  @Before
  public void setUp() throws IOException, SQLException, UIMAException {
    DisableLogging.enableLogging(Level.WARNING);
    // set up example analysis file (w/o content)
    textFile = File.createTempFile("example_text_", ".txt");
    textFile.deleteOnExit();
    docId = textFile.getName();
    docId = docId.substring(0, docId.lastIndexOf('.'));
    // set up an entity string map TSV file resource
    final File tmpMap = File.createTempFile("entity_string_map_", null);
    tmpMap.deleteOnExit();
    final BufferedWriter out = new BufferedWriter(new FileWriter(tmpMap));
    out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER + "\t" + ENTITY_NS + "\t" + ENTITY_ID +
        "\n");
    out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER + "\tgene-ns\tgene-id-1\n");
    out.write(docId + "\t" + SEMANTIC_ANNOTATION_IDENTIFIER + "\tgene-ns\tgene-id-2\n");
    out.close();
    // set up a named entity DB resource
    final String[] queries = new String[] { "SELECT name FROM entities "
        + "WHERE ns = ? AND id = ?" };
    final File tmpDb = File.createTempFile("jdbc_resource_", null);
    tmpDb.deleteOnExit();
    final String connectionUrl = "jdbc:h2:" + tmpDb.getCanonicalPath();
    jdbc_resource = DriverManager.getConnection(connectionUrl);
    final Statement stmt = jdbc_resource.createStatement();
    stmt.executeUpdate("CREATE TABLE entities(pk INT PRIMARY KEY,"
        + "                    ns VARCHAR, id VARCHAR, " + "                    name VARCHAR)");
    stmt.executeUpdate("INSERT INTO entities VALUES(1, '" + ENTITY_NS + "', '" + ENTITY_ID +
        "', '" + ENTITY_NAME + "')");
    stmt.executeUpdate("INSERT INTO entities VALUES(2, 'gene-ns', "
        + "'gene-id-1', 'tumor necrosis factor alpha')");
    stmt.executeUpdate("INSERT INTO entities VALUES(3, 'gene-ns', " + "'gene-id-2', 'TNF-alpha')");
    // create a case where the (case-insensitive) gene names overlap
    stmt.executeUpdate("INSERT INTO entities VALUES(4, 'gene-ns', " + "'gene-id-1', 'tnf-alpha')");
    stmt.close();
    jdbc_resource.commit();
    // finally, create the AE and some undefined example CAS instances.
    annotatorDesc = KnownEntityAnnotator.configure(SEMANTIC_ANNOTATION_NAMESPACE, queries, tmpMap,
        JdbcConnectionResourceImpl.configure(connectionUrl, "org.h2.Driver").create()).create();
    annotator = AnalysisEngineFactory.createPrimitive(annotatorDesc);
    baseJCas = annotator.newJCas();
    textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
    rawJCas = baseJCas.createView(Views.CONTENT_RAW.toString());
  }

  @Test(expected = ResourceInitializationException.class)
  public void testInitializeUimaContextWithoutParameters() throws ResourceInitializationException {
    AnalysisEngineFactory.createPrimitive(KnownEntityAnnotator.class);
    Assert.fail("not using the required parameters should have failed");
  }

  @Test
  public void testProcessJCas() throws CASRuntimeException, IOException,
      AnalysisEngineProcessException {
    // This example will test that given some example text, the default
    // setup entity string map and named entity DB resources, and the
    // configured AE, the AE annotates the string "entity mention" in
    // the example text as a SemanticAnnotation with the correct
    // properties
    final String text = "This is an " + ENTITY_NAME + " inside the text.\n";
    final int offset = "This is an ".length();
    final BufferedWriter out = new BufferedWriter(new FileWriter(textFile));
    out.write(text);
    out.close();
    textJCas.setDocumentText(text);
    rawJCas.setSofaDataURI("file:" + textFile.getCanonicalPath(), "text/plain");
    annotator.process(baseJCas.getCas());
    int count = 0;
    for (final SemanticAnnotation ann : JCasUtil.select(textJCas, SemanticAnnotation.class)) {
      Assert.assertEquals(offset, ann.getBegin());
      Assert.assertEquals(offset + ENTITY_NAME.length(), ann.getEnd());
      Assert.assertEquals(KnownEntityAnnotator.URI, ann.getAnnotator());
      Assert.assertEquals(SEMANTIC_ANNOTATION_NAMESPACE, ann.getNamespace());
      Assert.assertEquals(SEMANTIC_ANNOTATION_IDENTIFIER, ann.getIdentifier());
      Assert.assertEquals(1.0, ann.getConfidence(), 0.000001);
      final FSArray a = ann.getProperties();
      Assert.assertEquals(2, a.size());
      final Property p0 = (Property) a.get(0);
      final Property p1 = (Property) a.get(1);
      Assert.assertEquals("namespace", p0.getName());
      Assert.assertEquals(ENTITY_NS, p0.getValue());
      Assert.assertEquals("identifier", p1.getName());
      Assert.assertEquals(ENTITY_ID, p1.getValue());
      count++;
    }
    Assert.assertEquals(1, count);
  }

  @Test
  public void testCaseInsensitiveMatching() throws CASRuntimeException, IOException,
      AnalysisEngineProcessException {
    final String text = "The mouse Tumor necrosis factor alpha (Tnf-alpha) "
        + "gene is one of the earliest genes expressed.\n";
    final BufferedWriter out = new BufferedWriter(new FileWriter(textFile));
    out.write(text);
    out.close();
    textJCas.setDocumentText(text);
    rawJCas.setSofaDataURI("file:" + textFile.getCanonicalPath(), "text/plain");
    annotator.process(baseJCas.getCas());
    int count = 0;
    final Set<String> ids = new HashSet<String>();
    ids.add("gene-id-1");
    ids.add("gene-id-2");
    for (final SemanticAnnotation ann : JCasUtil.select(textJCas, SemanticAnnotation.class)) {
      final FSArray a = ann.getProperties();
      final Property p0 = (Property) a.get(0);
      final Property p1 = (Property) a.get(1);
      Assert.assertEquals("namespace", p0.getName());
      Assert.assertEquals("gene-ns", p0.getValue());
      Assert.assertEquals("identifier", p1.getName());
      if (count == 0) {
        Assert.assertEquals("gene-id-1", p1.getValue());
      } else {
        Assert.assertTrue(p1.getValue(), ids.remove(p1.getValue()));
      }
      if (count == 0) {
        Assert.assertEquals("Tumor necrosis factor alpha", ann.getCoveredText());
      } else {
        Assert.assertEquals("Tnf-alpha", ann.getCoveredText());
      }
      count++;
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void testGenerateRegex() {
    Assert.assertNull(KnownEntityAnnotator.generateRegex(new ArrayList<String>(),
        Pattern.UNICODE_CASE));
    final ArrayList<String> example = new ArrayList<String>();
    example.add("ot$1her");
    example.add("short");
    example.add("xlong-na\\Eme");
    final Pattern regex = KnownEntityAnnotator.generateRegex(example, Pattern.UNICODE_CASE);
    Assert.assertEquals("\\b\\Qxlong-na\\E\\\\E\\Qme\\E\\b|\\bxlong\\W*na\\W*Eme\\b" + "|"
        + "\\b\\Qot$1her\\E\\b|\\bot\\W*1\\W*her\\b" + "|" + "\\b\\Qshort\\E\\b|\\bshort\\b",
        regex.pattern());
  }
}
