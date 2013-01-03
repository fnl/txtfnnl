package txtfnnl.uima.analysis_component.opennlp;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

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
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

public class TestTokenAnnotator {
  AnalysisEngine annotator;
  JCas jcas;

  @Before
  public void setUp() {
    DisableLogging.enableLogging(Level.WARNING);
  }

  @Test
  public void testConfigure() throws UIMAException, IOException {
    final AnalysisEngineDescription aed = TokenAnnotator.configure();
    aed.doFullValidation();
  }

  @Test
  public void testReadDefaultModels() throws IOException {
    for (String path : new String[] { TokenAnnotator.DEFAULT_TOKEN_MODEL_FILE,
        TokenAnnotator.DEFAULT_POS_MODEL_FILE, TokenAnnotator.DEFAULT_CHUNK_MODEL_FILE }) {
      path = path.substring("file:".length()); // remove URL prefix
      final InputStream is = ClassLoader.getSystemResourceAsStream(path);
      Assert.assertNotNull(path, is);
      final int bytes = is.read();
      Assert.assertTrue("" + bytes + " bytes read from " + path, bytes > 0);
    }
  }

  @Test
  public void testDestroy() throws ResourceInitializationException, UIMAException, IOException {
    annotator = AnalysisEngineFactory.createPrimitive(TokenAnnotator.configure());
    annotator.destroy();
    Assert.assertTrue("success", true);
  }

  @Test
  public void testProcess() throws UIMAException, IOException {
    annotator = AnalysisEngineFactory.createPrimitive(TokenAnnotator.configure());
    final JCas baseJCas = annotator.newJCas();
    jcas = baseJCas.createView(Views.CONTENT_TEXT.toString());
    final String text = "This is a nice sentence. And this is another one.";
    final int[] beginPositions = { 0, 5, 8, 10, 15, 23, 25, 29, 34, 37, 45, 48 };
    final int[] endPositions = { 4, 7, 9, 14, 23, 24, 28, 33, 36, 44, 48, 49 };
    final String[] tags = { "DT", // This
        "VBZ", // is
        "DT", // a
        "JJ", // nice
        "NN", // sentence
        ".", // .
        "CC", // And
        "DT", // this
        "VBZ", // is
        "DT", // another
        "CD", // one
        "." }; // .
    final String[] chunks = { "NP", // This
        "VP", // is
        "NP", // a
        "NP", // nice
        "NP", // sentence
        null, // .
        null, // And
        "NP", // this
        "VP", // is
        "NP", // another
        "NP", // one
        null }; // .
    jcas.setDocumentText(text);
    addSentence(0, 24);
    addSentence(25, 49);
    annotator.process(baseJCas.getCas());
    int count = 0;
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    final AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(TokenAnnotation.type);
    while (sentenceIt.hasNext()) {
      final SentenceAnnotation sentence = (SentenceAnnotation) sentenceIt.next();
      final FSIterator<Annotation> tokenIt = annIdx.subiterator(sentence, true, true);
      while (tokenIt.hasNext()) {
        final TokenAnnotation token = (TokenAnnotation) tokenIt.next();
        Assert.assertEquals(Integer.toString(token.getBegin()), beginPositions[count],
            token.getBegin());
        Assert.assertEquals(Integer.toString(token.getEnd()), endPositions[count], token.getEnd());
        Assert.assertEquals(TokenAnnotator.URI, token.getAnnotator());
        Assert.assertEquals(TokenAnnotator.NAMESPACE, token.getNamespace());
        Assert.assertEquals(TokenAnnotator.IDENTIFIER, token.getIdentifier());
        Assert.assertEquals(0.999, token.getConfidence(), 0.01);
        Assert.assertEquals(tags[count], token.getPos());
        Assert.assertEquals(chunks[count], token.getChunk());
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
}
