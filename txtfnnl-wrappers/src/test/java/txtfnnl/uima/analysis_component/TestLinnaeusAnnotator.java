package txtfnnl.uima.analysis_component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.tcas.SemanticAnnotation;

public class TestLinnaeusAnnotator {
  static final File SPECIES = new File("src/test/resources/linnaeus/properties.conf");
  static final File PROXY = new File("src/test/resources/linnaeus/properties-proxy.conf");
  LinnaeusAnnotator.Builder config;
  Level realLvl;

  @Before
  public void setUp() {
    realLvl = DisableLogging.disableLogging();
    DisableLogging.enableLogging(Level.WARNING);
    config = LinnaeusAnnotator.configure(SPECIES);
  }

  public JCas getAnnotatedJCas() throws UIMAException {
    AnalysisEngine engine = AnalysisEngineFactory.createPrimitive(config.create());
    JCas jcas = engine.newJCas();
    jcas.setDocumentText("This is a murine human.");
    engine.process(jcas);
    return jcas;
  }

  @After
  public void tearDown() {
    DisableLogging.enableLogging(realLvl);
  }

  @Test
  public final void testProxyAnnotation() throws UIMAException {
    config.setConfigurationFilePath(PROXY);
    JCas jcas = getAnnotatedJCas();
    FSIterator<Annotation> iter = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    while (iter.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) iter.next();
      if ("murine".equals(ann.getCoveredText())) assertEquals("10090", ann.getIdentifier());
      else if ("human".equals(ann.getCoveredText())) assertEquals("9606", ann.getIdentifier());
      else fail("unexpected annotation " + ann.toString() + " on " + ann.getCoveredText());
      ++count;
    }
    assertEquals(2, count);
  }

  @Test
  public final void testSpeciesAnnotation() throws UIMAException {
    config.setConfigurationFilePath(SPECIES);
    JCas jcas = getAnnotatedJCas();
    FSIterator<Annotation> iter = jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
    int count = 0;
    while (iter.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) iter.next();
      if ("human".equals(ann.getCoveredText())) assertEquals("9606", ann.getIdentifier());
      else fail("unexpected annotation " + ann.toString() + " on " + ann.getCoveredText());
      ++count;
    }
    assertEquals(1, count);
  }
}
