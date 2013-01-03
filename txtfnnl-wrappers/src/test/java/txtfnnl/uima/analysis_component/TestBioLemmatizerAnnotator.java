package txtfnnl.uima.analysis_component;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

public class TestBioLemmatizerAnnotator {
  AnalysisEngineDescription annotator;
  AnalysisEngine engine;
  JCas jcas;

  @Before
  public void setUp() throws UIMAException, IOException {
    annotator = BioLemmatizerAnnotator.configure();
    engine = AnalysisEngineFactory.createPrimitive(annotator);
  }

  @Test
  public void testDestroy() {
    engine.destroy();
    Assert.assertTrue("success", true);
  }

  @Test
  public void testProcessCAS() throws UIMAException, IOException {
    final JCas baseJCas = engine.newJCas();
    jcas = baseJCas.createView(Views.CONTENT_TEXT.toString());
    final String text = "These are two nice sentences. And this could be getting better.";
    final String[] lemma = { "these", "be", "two", "nice", "sentence", ".", "and", "this",
        "could", "be", "get", "well", "." };
    final int[] beginPositions = { 0, 6, 10, 14, 19, 28, 30, 34, 39, 45, 48, 56, 62 };
    final int[] endPositions = { 5, 9, 13, 18, 28, 29, 33, 38, 44, 47, 55, 62, 63 };
    final String[] tags = { "DT", "VBZ", "CD", "JJ", "NNS", ".", "CC", "DT", "VBD", "VBZ", "VBG",
        "RBR", "." };
    jcas.setDocumentText(text);
    addSentence(0, 29);
    addSentence(30, 63);
    addTokens(beginPositions, endPositions, tags);
    engine.process(baseJCas.getCas());
    int count = 0;
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    final AnnotationIndex<Annotation> tokenIdx = jcas.getAnnotationIndex(TokenAnnotation.type);
    while (sentenceIt.hasNext()) {
      final Annotation sentence = sentenceIt.next();
      final FSIterator<Annotation> tokenIt = tokenIdx.subiterator(sentence);
      while (tokenIt.hasNext()) {
        final TokenAnnotation token = (TokenAnnotation) tokenIt.next();
        // make sure we are looking at the right token
        Assert.assertEquals(Integer.toString(token.getBegin()) + " begin at " + count,
            beginPositions[count], token.getBegin());
        Assert.assertEquals(Integer.toString(token.getEnd()) + " end at " + count,
            endPositions[count], token.getEnd());
        Assert.assertEquals("token '" + token.getCoveredText() + "'", lemma[count],
            token.getStem());
        count++;
      }
    }
    Assert.assertEquals(beginPositions.length, count);
  }

  private void addSentence(int begin, int end) {
    final SentenceAnnotation ann = new SentenceAnnotation(jcas, begin, end);
    ann.setAnnotator(SentenceAnnotator.URI);
    ann.setConfidence(1.0);
    ann.setIdentifier(SentenceAnnotator.IDENTIFIER);
    ann.setNamespace(SentenceAnnotator.NAMESPACE);
    ann.addToIndexes(jcas);
  }

  private void addTokens(int[] begins, int[] ends, String[] tags) {
    for (int i = 0; i < begins.length; ++i) {
      final TokenAnnotation ann = new TokenAnnotation(jcas, begins[i], ends[i]);
      ann.setAnnotator(TokenAnnotator.URI);
      ann.setConfidence(1.0);
      ann.setIdentifier(TokenAnnotator.IDENTIFIER);
      ann.setNamespace(TokenAnnotator.NAMESPACE);
      ann.setPos(tags[i]);
      ann.addToIndexes(jcas);
    }
  }
}
