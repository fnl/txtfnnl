package txtfnnl.uima.collection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.IOUtils;

@Ignore
public class TestRelationshipPatternLineWriter {
  RelationshipPatternLineWriter writer;
  AnalysisEngine ae;
  JCas jcas;

  @Before
  public void setUp() throws UIMAException, IOException {
    DisableLogging.enableLogging(Level.WARNING);
    writer = new RelationshipPatternLineWriter();
    ae = AnalysisEngineFactory.createPrimitive(RelationshipPatternLineWriter
        .configure("http://purl.org/relationship/"));
    jcas = ae.newJCas();
  }

  @Test
  public void testInitializeUimaContext() throws UIMAException, IOException {
    for (final String p : new String[] { TextWriter.PARAM_OUTPUT_DIRECTORY,
        TextWriter.PARAM_ENCODING }) {
      Assert.assertNull("Parameter " + p + " does not default to null.",
          ae.getConfigParameterValue(p));
    }
    Assert.assertFalse((Boolean) ae.getConfigParameterValue(TextWriter.PARAM_OVERWRITE_FILES));
    Assert.assertTrue((Boolean) ae.getConfigParameterValue(TextWriter.PARAM_PRINT_TO_STDOUT));
  }

  @Test
  public void testIterateAnnotationsNull() {
    final List<TextAnnotation[]> result = writer.listSeparateEntities(null);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void testIterateAnnotationsZero() {
    final FSArray a = createTAArray(0);
    final List<TextAnnotation[]> result = writer.listSeparateEntities(a);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void testIterateAnnotationsDefault() {
    final FSArray a = createTAArray(2);
    final List<TextAnnotation[]> result = writer.listSeparateEntities(a);
    Assert.assertEquals(2, result.size());
    Assert.assertEquals(1, result.get(0).length);
    Assert.assertEquals(1, result.get(1).length);
  }

  @Test
  public void testIterateAnnotationsThreeTypes() {
    final List<TextAnnotation[]> result = writer.listSeparateEntities(createTAArray(3, true));
    Assert.assertEquals(1, result.size());
    Assert.assertEquals(3, result.get(0).length);
  }

  @Test
  public void testIterateAnnotationsMultipleTypes() {
    final FSArray a = createTAArray(7, true);
    ((TextAnnotation) a.get(0)).setIdentifier("a");
    ((TextAnnotation) a.get(1)).setIdentifier("b");
    ((TextAnnotation) a.get(2)).setIdentifier("c");
    ((TextAnnotation) a.get(3)).setIdentifier("a");
    ((TextAnnotation) a.get(4)).setIdentifier("c");
    ((TextAnnotation) a.get(5)).setIdentifier("b");
    ((TextAnnotation) a.get(6)).setIdentifier("c");
    final Set<String> check = new HashSet<String>();
    check.add("a");
    check.add("b");
    check.add("c");
    final List<TextAnnotation[]> result = writer.listSeparateEntities(a);
    Assert.assertEquals(12, result.size());
    for (int i = 0; i < 12; i++) {
      Assert.assertEquals(3, result.get(i).length);
      final Set<String> c = new HashSet<String>(check);
      for (final TextAnnotation ta : result.get(i)) {
        Assert.assertTrue(c.remove(ta.getIdentifier()));
      }
    }
  }

  @Test
  public void testRegroupEntitiesZero() {
    final FSArray a = new FSArray(jcas, 0);
    final Map<String, List<TextAnnotation>> result = writer.groupEntities(a);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void testRegroupEntitiesNull() {
    final Map<String, List<TextAnnotation>> result = writer.groupEntities(null);
    Assert.assertEquals(0, result.size());
  }

  @Test
  public void testRegroupEntitiesDefault() {
    final FSArray a = createTAArray(2);
    final Map<String, List<TextAnnotation>> result = writer.groupEntities(a);
    Assert.assertEquals(1, result.size());
    Assert.assertTrue(result.containsKey("ns:id"));
    Assert.assertEquals(2, result.get("ns:id").size());
    Assert.assertEquals(a.get(0), result.get("ns:id").get(1));
    Assert.assertEquals(a.get(1), result.get("ns:id").get(0));
  }

  @Test
  public void testOrderEntitiesZero() {
    TextAnnotation[] a0, a3;
    a0 = writer.orderEntities(new TextAnnotation[0]);
    a3 = writer.orderEntities(new TextAnnotation[3]);
    Assert.assertNotNull(a0);
    Assert.assertNotNull(a3);
    Assert.assertEquals(0, a0.length);
    Assert.assertNull(a3[0]);
  }

  @Test
  public void testOrderEntitiesNull() {
    final TextAnnotation[] a = writer.orderEntities(null);
    Assert.assertNull(a);
  }

  @Test
  public void testOrderEntitiesDefault() {
    TextAnnotation[] a = new TextAnnotation[3];
    a[0] = getTextAnnotation("id-1");
    a[1] = getTextAnnotation("id-2");
    a[2] = getTextAnnotation("id-3");
    a[2].setBegin(0);
    a[2].setEnd(10);
    a[1].setBegin(1);
    a[1].setEnd(9);
    a[0].setBegin(10);
    a[0].setEnd(12);
    a = writer.orderEntities(a);
    Assert.assertNotNull(a);
    Assert.assertEquals(3, a.length);
    Assert.assertEquals(0, a[0].getBegin());
    Assert.assertEquals("id-3", a[0].getIdentifier());
    Assert.assertEquals(1, a[1].getBegin());
    Assert.assertEquals("id-2", a[1].getIdentifier());
    Assert.assertEquals(10, a[2].getBegin());
    Assert.assertEquals("id-1", a[2].getIdentifier());
  }

  @Test
  public void testOrderEntitiesOverlapping() {
    TextAnnotation[] a = new TextAnnotation[3];
    a[0] = getTextAnnotation("id-1");
    a[1] = getTextAnnotation("id-2");
    a[2] = getTextAnnotation("id-3");
    a[2].setBegin(0);
    a[2].setEnd(10);
    a[1].setBegin(1);
    a[1].setEnd(9);
    a[0].setBegin(5);
    a[0].setEnd(10);
    a = writer.orderEntities(a);
    Assert.assertNull(a);
  }

  @Test
  public void testIterateOverlappingEntities() {
    final FSArray test = createTAArray(5);
    final TextAnnotation[] a = new TextAnnotation[5];
    a[0] = (TextAnnotation) test.get(0);
    a[1] = (TextAnnotation) test.get(1);
    a[2] = (TextAnnotation) test.get(2);
    a[3] = (TextAnnotation) test.get(3);
    a[4] = (TextAnnotation) test.get(4);
    a[0].setIdentifier("type-0");
    a[0].setBegin(0);
    a[0].setEnd(10);
    a[1].setIdentifier("type-0");
    a[1].setBegin(0);
    a[1].setEnd(15);
    a[2].setIdentifier("type-1");
    a[2].setBegin(0);
    a[2].setEnd(5);
    a[3].setIdentifier("type-1");
    a[3].setBegin(7);
    a[3].setEnd(12);
    a[4].setIdentifier("type-2");
    a[4].setBegin(10);
    a[4].setEnd(15);
    final List<TextAnnotation[]> result = writer.listSeparateEntities(test);
    Assert.assertEquals(2, result.size());
    for (int i = 0; i < result.size(); ++i) {
      final TextAnnotation[] ta = result.get(i);
      Assert.assertEquals(ta[0].getIdentifier(), "type-0");
      Assert.assertEquals(ta[1].getIdentifier(), "type-1");
      Assert.assertEquals(ta[2].getIdentifier(), "type-2");
    }
  }

  private FSArray createTAArray(int size) {
    return createTAArray(size, false);
  }

  private FSArray createTAArray(int size, boolean randomId) {
    final FSArray a = new FSArray(jcas, size);
    for (int i = 0; i < size; i++) {
      a.set(i, getTextAnnotation(randomId ? UUID.randomUUID().toString() : "id"));
    }
    return a;
  }

  private TextAnnotation getTextAnnotation(String id) {
    final TextAnnotation ta = new TextAnnotation(jcas);
    ta.setIdentifier(id);
    ta.setNamespace("ns:");
    return ta;
  }

  @Test
  public void testClean() {
    Assert.assertEquals("a \n\nb", writer.clean(" \n  a  \n\n b\n "));
  }

  @Test
  public void testProcessRelationship() throws AnalysisEngineProcessException {
    final String test = "This is a sentence.";
    jcas.setDocumentText(test);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    final RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas);
    final AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(SyntaxAnnotation.type);
    writer.printToStdout = true;
    writer.process(relAnn, test, annIdx,
        RelationshipPatternLineWriter.makeConstituentSyntaxAnnotationConstraint(jcas), jcas);
    Assert.assertTrue(outputStream.toString(),
        outputStream.toString().startsWith("[[ns:id]]" + test.substring(0, test.length() - 1)));
  }

  @Test
  public void testProcessInnerRelationship() throws AnalysisEngineProcessException {
    final String test = "This is a sentence.";
    jcas.setDocumentText(test);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    final RelationshipAnnotation relAnn = createDummyAnnotation(test, jcas, 5, 7); // is
    final AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(SyntaxAnnotation.type);
    writer.printToStdout = true;
    writer.process(relAnn, test, annIdx,
        RelationshipPatternLineWriter.makeConstituentSyntaxAnnotationConstraint(jcas), jcas);
    Assert.assertTrue(outputStream.toString(),
        outputStream.toString().startsWith("This [[ns:id]] a sentence."));
  }

  RelationshipAnnotation createDummyAnnotation(String test, JCas aJCas) {
    return createDummyAnnotation(test, aJCas, 0, 0);
  }

  RelationshipAnnotation createDummyAnnotation(String text, JCas aJCas, int start, int end) {
    // sentence
    final SentenceAnnotation sentAnn = new SentenceAnnotation(aJCas, 0, text.length());
    sentAnn.setNamespace(SentenceAnnotator.NAMESPACE);
    sentAnn.setIdentifier(SentenceAnnotator.IDENTIFIER);
    sentAnn.addToIndexes(aJCas);
    // entity
    final SemanticAnnotation entityAnn = new SemanticAnnotation(aJCas, start, end);
    entityAnn.setNamespace("ns:");
    entityAnn.setIdentifier("id");
    entityAnn.addToIndexes(aJCas);
    // relationship
    final RelationshipAnnotation relAnn = new RelationshipAnnotation(aJCas);
    relAnn.setNamespace("http://purl.org/relationship/");
    final FSArray sources = new FSArray(aJCas, 1);
    sources.set(0, sentAnn);
    sources.addToIndexes(aJCas);
    final FSArray targets = new FSArray(aJCas, 1);
    targets.set(0, entityAnn);
    targets.addToIndexes(aJCas);
    relAnn.setSources(sources);
    relAnn.setTargets(targets);
    relAnn.addToIndexes(aJCas);
    return relAnn;
  }

  @Test
  public void testProcessJCasToStdout() throws UIMAException, IOException {
    final String input = "Hello World!";
    final ByteArrayOutputStream outputStream = processHelper(input);
    Assert.assertTrue(outputStream.toString(),
        outputStream.toString().startsWith("[[ns:id]]" + input));
  }

  @Test
  public void testProcessJCasOutputDir() throws UIMAException, IOException {
    final File tmpDir = IOUtils.mkTmpDir();
    final File existing = new File(tmpDir, "test.txt.txt");
    existing.deleteOnExit();
    tmpDir.deleteOnExit();
    Assert.assertTrue(existing.createNewFile());
    ae = AnalysisEngineFactory.createPrimitive(RelationshipPatternLineWriter.configure(
        "http://purl.org/relationship/", tmpDir, "UTF-32", false, false, 0));
    final String input = "Hello World!";
    processHelper(input);
    final File created = new File(tmpDir, "test.txt.2.txt");
    Assert.assertTrue(created.exists());
    final FileInputStream fis = new FileInputStream(created);
    final String result = IOUtils.read(fis, "UTF-32");
    Assert.assertTrue(result, result.startsWith("[[ns:id]]" + input));
    fis.close();
    existing.delete();
    created.delete();
    tmpDir.delete();
  }

  ByteArrayOutputStream processHelper(String input) throws CASException,
      ResourceInitializationException, AnalysisEngineProcessException {
    final JCas textCas = jcas.createView(Views.CONTENT_TEXT.toString());
    final JCas rawCas = jcas.createView(Views.CONTENT_RAW.toString());
    rawCas.setSofaDataURI("test.txt", "mime");
    textCas.setDocumentText(input);
    createDummyAnnotation(input, textCas);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    ae.process(jcas);
    return outputStream;
  }
}
