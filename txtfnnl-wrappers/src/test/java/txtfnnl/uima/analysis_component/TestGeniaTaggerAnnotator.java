/**
 * 
 */
package txtfnnl.uima.analysis_component;

import static org.junit.Assert.*;

import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class TestGeniaTaggerAnnotator {

	private AnalysisEngineDescription annotatorDesc;
	private AnalysisEngine sentenceAnnotator;
	private AnalysisEngine geniaTaggerAnnotator;
	private JCas baseJCas;
	private JCas textJCas;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		DisableLogging.enableLogging(Level.WARNING);

		// set up AE descriptor under test
		sentenceAnnotator = AnalysisEngineFactory.createPrimitive(SentenceAnnotator.configure());
		annotatorDesc = AnalysisEngineFactory
		    .createPrimitiveDescription(GeniaTaggerAnnotator.class);
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
		FSIterator<Annotation> tokenIt = TokenAnnotation.getIterator(textJCas);

		String[] tokens = new String[] {
		    "ARL",
		    ",",
		    "a",
		    "regulator",
		    "of",
		    "cell",
		    "death",
		    "localized",
		    "inside",
		    "the",
		    "nucleus",
		    ",",
		    "has",
		    "been",
		    "shown",
		    "to",
		    "bind",
		    "the",
		    "p53",
		    "promoter",
		    "." };
		String[] chunks = new String[] {
		    "NP",
		    null,
		    "NP",
		    "NP",
		    "PP",
		    "NP",
		    "NP",
		    "VP",
		    "PP",
		    "NP",
		    "NP",
		    null,
		    "VP",
		    "VP",
		    "VP",
		    "VP",
		    "VP",
		    "NP",
		    "NP",
		    "NP",
		    null };
		assertEquals(tokens.length, chunks.length);
		int tokenIdx = 0;
		int chunkIdx = 0;

		while (tokenIt.hasNext()) {
			TokenAnnotation ann = (TokenAnnotation) tokenIt.next();
			assertEquals("token " + tokenIdx, tokens[tokenIdx++], ann.getCoveredText());
			assertEquals("chunk " + chunkIdx, chunks[chunkIdx++], ann.getChunk());
		}

		assertEquals("token count", tokens.length, tokenIdx);
		assertEquals("chunk count", chunks.length, chunkIdx);
	}
}
