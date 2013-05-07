package txtfnnl.uima.analysis_component.opennlp;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.tcas.SentenceAnnotation;

public class TestSentenceAnnotator {
  AnalysisEngine sentenceAnnotator;

  @Before
  public void setUp() {
    DisableLogging.enableLogging(Level.WARNING);
  }

  @Test
  public void testDestroy() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator.configure()
        .create());
    sentenceAnnotator.destroy();
    Assert.assertTrue("success", true);
  }

  @Test
  public void testProcessCAS() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator.configure()
        .create());
    processTest("This is one sentence.", " ");
  }

  @Test
  public void testNoLineSplitting() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator.configure()
        .splitIgnoringNewlines().create());
    processTest("This is one sentence.", " ");
    final JCas jcas = sentenceAnnotator.newJCas();
    final String oneSentence = "This\tis\n\none sentence\nwritten\nover\tseveral\n\n\nlines.";
    jcas.setDocumentText(oneSentence);
    sentenceAnnotator.process(jcas.getCas());
    int count = 0;
    final FSIterator<Annotation> it = SentenceAnnotation.getIterator(jcas);
    while (it.hasNext()) {
      final SentenceAnnotation ann = (SentenceAnnotation) it.next();
      Assert.assertEquals(ann.getOffset().toString(), 0, ann.getBegin());
      Assert.assertEquals(ann.getOffset().toString(), oneSentence.length(), ann.getEnd());
      Assert.assertEquals(0.995, ann.getConfidence(), 0.005);
      count++;
    }
    Assert.assertEquals(1, count);
  }

  @Test
  public void testDefaultMultilineSplit() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator
        .configure().splitOnSuccessiveNewlines().create());
    processTest("This is a closed sentence.", "\n\n");
  }

  @Test
  public void testMultilineSplit() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator
        .configure().splitOnSuccessiveNewlines().create());
    processTest("This is an open sentence", "\n\t\n");
  }

  @Test
  public void testDefaultSingleLineSplit() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator
        .configure().splitOnSingleNewlines().create());
    processTest("This is a closed sentence.", "\n\n");
  }

  @Test
  public void testSingleLineSplit() throws UIMAException, IOException {
    sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator
        .configure().splitOnSingleNewlines().create());
    processTest("This is an open sentence", "\n");
  }

  void processTest(String s1, String join) throws UIMAException, IOException {
    final String s2 = "And this is another sentence.";
    final Iterator<Integer> offsets = Arrays.asList(
        new Integer[] { 0, s1.length(), s1.length() + join.length(),
            s1.length() + s2.length() + join.length() }).iterator();
    final JCas jcas = sentenceAnnotator.newJCas();
    jcas.setDocumentText(s1 + join + s2);
    sentenceAnnotator.process(jcas);
    int count = 0;
    int begin, end;
    final FSIterator<Annotation> it = SentenceAnnotation.getIterator(jcas);
    while (it.hasNext()) {
      final SentenceAnnotation ann = (SentenceAnnotation) it.next();
      begin = offsets.next();
      end = offsets.next();
      Assert.assertEquals(ann.getOffset().toString(), begin, ann.getBegin());
      Assert.assertEquals(ann.getOffset().toString(), end, ann.getEnd());
      Assert.assertEquals(SentenceAnnotator.URI, ann.getAnnotator());
      Assert.assertEquals(SentenceAnnotator.NAMESPACE, ann.getNamespace());
      Assert.assertEquals(SentenceAnnotator.IDENTIFIER, ann.getIdentifier());
      Assert.assertEquals(0.9999, ann.getConfidence(), 0.001);
      Assert.assertNull(ann.getProperties());
      count++;
    }
    Assert.assertEquals(2, count);
  }
}
