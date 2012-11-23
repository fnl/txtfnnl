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
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.ChunkAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

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
		sentenceAnnotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPSentenceAEDescriptor");
		annotatorDesc = AnalysisEngineFactory
		    .createPrimitiveDescription(GeniaTaggerAnnotator.class);
		geniaTaggerAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    annotatorDesc, Views.CONTENT_TEXT.toString());
		baseJCas = sentenceAnnotator.newJCas();
		textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
	}

	@Test
	public void producesAnnotations() throws AnalysisEngineProcessException {
		textJCas
		    .setDocumentText("ARL, a regulator of cell death localized inside the nucleus, has been shown to bind the p53 promoter.");
		sentenceAnnotator.process(baseJCas.getCas());
		geniaTaggerAnnotator.process(baseJCas.getCas());
		AnnotationIndex<Annotation> idx = textJCas
		    .getAnnotationIndex(SyntaxAnnotation.type);
		FSMatchConstraint tokenCons = TokenAnnotator
		    .makeTokenConstraint(textJCas);
		FSMatchConstraint chunkCons = ChunkAnnotator
		    .makeChunkConstraint(textJCas);
		FSIterator<Annotation> tokenIt = textJCas.createFilteredIterator(idx.iterator(),
		    tokenCons);
		FSIterator<Annotation> chunkIt = textJCas.createFilteredIterator(idx.iterator(),
		    chunkCons);

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
		    "NP",
		    "PP",
		    "NP",
		    "VP",
		    "PP",
		    "NP",
		    "VP",
		    "NP" };
		int tokenIdx = 0;
		int chunkIdx = 0;

		while (tokenIt.hasNext()) {
			assertEquals("token " + tokenIdx, tokens[tokenIdx++], tokenIt
			    .next().getCoveredText());
		}

		while (chunkIt.hasNext()) {
			assertEquals("chunk " + chunkIdx, chunks[chunkIdx++],
			    ((SyntaxAnnotation) chunkIt.next()).getIdentifier());
		}

		assertEquals("token count", tokens.length, tokenIdx);
		assertEquals("chunk count", chunks.length, chunkIdx);
	}
}
