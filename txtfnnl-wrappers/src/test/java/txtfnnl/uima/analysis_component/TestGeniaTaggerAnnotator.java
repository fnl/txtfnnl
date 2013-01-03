/**
 * 
 */
package txtfnnl.uima.analysis_component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * @author Florian Leitner
 */
public class TestGeniaTaggerAnnotator {
  private static AnalysisEngine geniaTaggerAnnotator;
  private static AnalysisEngine sentenceAnnotator;
  private static AnalysisEngineDescription annotatorDesc;
  private JCas baseJCas;
  private JCas textJCas;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    if (annotatorDesc == null) annotatorDesc = GeniaTaggerAnnotator.configure();
    if (sentenceAnnotator == null)
      sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator.configure());
    if (geniaTaggerAnnotator == null)
      geniaTaggerAnnotator = AnalysisEngineFactory.createAnalysisEngine(annotatorDesc,
          Views.CONTENT_TEXT.toString());
    baseJCas = sentenceAnnotator.newJCas();
    textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
  }

  @Test
  public void producesAnnotations() throws AnalysisEngineProcessException {
    textJCas
        .setDocumentText("ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter.");
    sentenceAnnotator.process(baseJCas.getCas());
    geniaTaggerAnnotator.process(baseJCas.getCas());
    final FSIterator<Annotation> tokenIt = TokenAnnotation.getIterator(textJCas);
    final String[] tokens = new String[] { "ARL", ",", "a", "regulator", "of", "cell", "death",
        "localized", "inside", "the", "nucleus", ",", "has", "been", "shown", "to", "bind", "the",
        "p53", "promoter", "." };
    final String[] chunks = new String[] { "NP", null, "NP", "NP", "PP", "NP", "NP", "VP", "PP",
        "NP", "NP", null, "VP", "VP", "VP", "VP", "VP", "NP", "NP", "NP", null };
    Assert.assertEquals(tokens.length, chunks.length);
    int tokenIdx = 0;
    int chunkIdx = 0;
    while (tokenIt.hasNext()) {
      final TokenAnnotation ann = (TokenAnnotation) tokenIt.next();
      Assert.assertEquals("token " + tokenIdx, tokens[tokenIdx++], ann.getCoveredText());
      Assert.assertEquals("chunk " + chunkIdx, chunks[chunkIdx++], ann.getChunk());
    }
    Assert.assertEquals("token count", tokens.length, tokenIdx);
    Assert.assertEquals("chunk count", chunks.length, chunkIdx);
  }
}
