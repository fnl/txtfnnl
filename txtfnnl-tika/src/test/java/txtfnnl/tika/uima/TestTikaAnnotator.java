package txtfnnl.tika.uima;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.tika.sax.ElsevierXMLContentHandler;
import txtfnnl.tika.uima.TikaAnnotator.Builder;
import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.DocumentAnnotation;
import txtfnnl.uima.tcas.StructureAnnotation;

public class TestTikaAnnotator {
  @Before
  public void setUp() throws UIMAException, IOException {
    DisableLogging.enableLogging(Level.WARNING);
  }

  AnalysisEngine getEngine(String encoding, boolean normalizeGreek,
      Class<? extends ContentHandler> xmlHandler) throws UIMAException, IOException {
    Builder b = TikaAnnotator.configure();
    b.setEncoding(encoding).setXmlHandlerClass(xmlHandler);
    if (normalizeGreek) b.normalizeGreek();
    return AnalysisEngineFactory.createPrimitive(b.create());
  }

  AnalysisEngine getEngine() throws UIMAException, IOException {
    return getEngine(null, false, null);
  }

  @Test
  public void testConfiguration() throws UIMAException, IOException {
    final AnalysisEngineDescription aed = TikaAnnotator.configure().create();
    aed.doFullValidation();
  }

  @Test
  public void testProcessRequiresRawView() throws UIMAException, IOException {
    DisableLogging.disableLogging();
    final AnalysisEngine tikaAnnotator = getEngine();
    final JCas baseJCas = tikaAnnotator.newJCas();
    assertThrows(baseJCas, "No sofaFS with name CONTENT_RAW found.", CASRuntimeException.class);
    baseJCas.createView(Views.CONTENT_RAW.toString());
    assertThrows(baseJCas, "no SOFA data stream", AssertionError.class);
  }

  private void assertThrows(JCas cas, String message, Class<?> type) throws UIMAException,
      IOException {
    boolean thrown = false;
    final AnalysisEngine tikaAnnotator = getEngine();
    try {
      tikaAnnotator.process(cas);
    } catch (final AnalysisEngineProcessException e) {
      thrown = true;
      Assert.assertEquals(message, e.getCause().getMessage());
      Assert.assertEquals(type, e.getCause().getClass());
    } finally {
      Assert.assertTrue("expected exception not thrown", thrown);
    }
  }

  @Test
  public void testProcessCreatesNewPlainTextView() throws UIMAException, IOException {
    final AnalysisEngine tikaAnnotator = getEngine(null, true, null);
    final JCas baseJCas = tikaAnnotator.newJCas();
    final JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
    jCas.setSofaDataString("text ß", "text/plain"); // latin small sharp S
    tikaAnnotator.process(baseJCas);
    Assert.assertNotNull(baseJCas.getView(Views.CONTENT_TEXT.toString()));
    Assert.assertEquals("text beta", baseJCas.getView(Views.CONTENT_TEXT.toString())
        .getDocumentText());
  }

  @Test
  public void testProcessHTML() throws UIMAException, IOException {
    final AnalysisEngine tikaAnnotator = getEngine();
    final JCas baseJCas = tikaAnnotator.newJCas();
    JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
    jCas.setSofaDataString("<html><body><p id=1>test</p></body></html>", "text/html");
    tikaAnnotator.process(baseJCas);
    jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
    int count = 0;
    for (final StructureAnnotation ann : JCasUtil.select(jCas, StructureAnnotation.class)) {
      Assert.assertEquals(0, ann.getBegin());
      Assert.assertEquals(4, ann.getEnd());
      Assert.assertEquals(TikaAnnotator.class.getName(), ann.getAnnotator());
      Assert.assertEquals("http://www.w3.org/1999/xhtml#", ann.getNamespace());
      Assert.assertEquals("p", ann.getIdentifier());
      Assert.assertEquals(1.0, ann.getConfidence(), 0.0000001);
      Assert.assertNotNull(ann.getProperties());
      Assert.assertEquals(1, ann.getProperties().size());
      final Property p = ann.getProperties(0);
      Assert.assertEquals("id", p.getName());
      Assert.assertEquals("1", p.getValue());
      count++;
    }
    Assert.assertEquals(1, count);
  }

  @Test
  public void testProcessElsevierXML() throws UIMAException, IOException {
    AnalysisEngine tikaAnnotator = getEngine();
    final JCas baseJCas = tikaAnnotator.newJCas();
    JCas jCas = baseJCas.createView(Views.CONTENT_RAW.toString());
    jCas.setSofaDataString("<ce:para xmlns:ce=\"url\" val='1' >"
        + "<ce:para val='1' >test</ce:para>" + "again</ce:para>", "text/xml");
    tikaAnnotator = AnalysisEngineFactory.createPrimitive(TikaAnnotator.configure()
        .setXmlHandlerClass(ElsevierXMLContentHandler.class).create());
    tikaAnnotator.process(baseJCas);
    jCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
    Assert.assertEquals("test\n\nagain", jCas.getDocumentText());
    int count = 0;
    for (final DocumentAnnotation ann : JCasUtil.select(jCas, DocumentAnnotation.class)) {
      Assert.assertEquals("Content-Type", ann.getNamespace());
      Assert.assertEquals("text/xml", ann.getIdentifier());
      ++count;
    }
    Assert.assertEquals(1, count);
    for (final StructureAnnotation ann : JCasUtil.select(jCas, StructureAnnotation.class)) {
      Assert.assertEquals("url#", ann.getNamespace());
      Assert.assertEquals("ce:para", ann.getIdentifier());
      Assert.assertEquals(1.0, ann.getConfidence(), 0.0000001);
      Assert.assertNotNull(ann.getProperties());
      Assert.assertEquals(1, ann.getProperties().size());
      final Property p = ann.getProperties(0);
      Assert.assertEquals("val", p.getName());
      Assert.assertEquals("1", p.getValue());
      count++;
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void testHandleMetadata() throws UIMAException, IOException {
    final AnalysisEngine tikaAnnotator = getEngine();
    final JCas baseJCas = tikaAnnotator.newJCas();
    final Metadata metadata = new Metadata();
    final TikaAnnotator real = new TikaAnnotator();
    metadata.add("test_name", "test_value");
    real.handleMetadata(metadata, baseJCas);
    int count = 0;
    for (final DocumentAnnotation ann : JCasUtil.select(baseJCas, DocumentAnnotation.class)) {
      Assert.assertEquals(real.getAnnotatorURI(), ann.getAnnotator());
      Assert.assertEquals("test_name", ann.getNamespace());
      Assert.assertEquals("test_value", ann.getIdentifier());
      Assert.assertEquals(1.0, ann.getConfidence(), 0.0000001);
      Assert.assertNull(ann.getProperties());
      count++;
    }
    Assert.assertEquals(1, count);
  }

  @Test
  public void testEncoding() throws CASRuntimeException, IOException, UIMAException {
    final AnalysisEngine tikaAnnotator = getEngine("UTF-8", true, null);
    final JCas baseJCas = tikaAnnotator.newJCas();
    JCas rawCas = baseJCas.createView(Views.CONTENT_RAW.toString());
    final File infile = new File("src/test/resources/encoding.html");
    rawCas.setSofaDataURI("file:" + infile.getCanonicalPath(), null);
    tikaAnnotator.process(baseJCas);
    JCas textCas = baseJCas.getView(Views.CONTENT_TEXT.toString());
    final String text = textCas.getDocumentText();
    Assert.assertTrue(text, text.indexOf("and HIF-1alpha. A new p300") > -1);
    int count = 0;
    for (final DocumentAnnotation ann : JCasUtil.select(textCas, DocumentAnnotation.class)) {
      if (ann.getNamespace().equals("title")) {
        Assert.assertEquals("Functional role of p35srj, a novel p300/CBP binding protein, "
            + "during transactivation by HIF-1", ann.getIdentifier());
      } else if (ann.getNamespace().equals("Content-Location")) {
        Assert.assertEquals("http://www.ncbi.nlm.nih.gov/pmc/articles/PMC316375/",
            ann.getIdentifier());
      } else if (ann.getNamespace().equals("Content-Encoding")) {
        Assert.assertEquals("UTF-8", ann.getIdentifier());
      } else if (ann.getNamespace().equals("resourceName")) {
        Assert.assertEquals("encoding.html", ann.getIdentifier());
      } else {
        Assert.fail();
      }
      count++;
    }
    Assert.assertEquals(4, count);
  }
}
