package txtfnnl.uima.analysis_component.opennlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

public class TestSentenceAnnotator {

	AnalysisEngine sentenceAnnotator;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		sentenceAnnotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPSentenceAEDescriptor");
	}

	@Test
	public void testDestroy() {
		sentenceAnnotator.destroy();
		assertTrue("success", true);
	}

	@Test
	public void testProcessCAS() throws UIMAException, IOException {
		processTest("This is one sentence.", " ");
	}

	@Test
	public void testDefaultMultilineSplit() throws UIMAException, IOException {
		sentenceAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, "multi");
		processTest("This is a closed sentence.", "\n\n");
	}

	@Test
	public void testMultilineSplit() throws UIMAException, IOException {
		sentenceAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, "multi");
		processTest("This is an open sentence", "\n\t\n");
	}

	@Test
	public void testDefaultLineSplit() throws UIMAException, IOException {
		sentenceAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, "single");
		processTest("This is a closed sentence.", "\n\n");
	}

	@Test
	public void testLineSplit() throws UIMAException, IOException {
		sentenceAnnotator = AnalysisEngineFactory.createAnalysisEngine(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, "single");
		processTest("This is an open sentence", "\n\n");
	}

	void processTest(String s1, String join) throws UIMAException, IOException {
		String s2 = "And this is another sentence.";
		Iterator<Integer> offsets = Arrays.asList(
		    new Integer[] {
		        0,
		        s1.length(),
		        s1.length() + join.length(),
		        s1.length() + s2.length() + join.length() }).iterator();
		JCas baseJCas = sentenceAnnotator.newJCas();
		JCas textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
		textJCas.setDocumentText(s1 + join + s2);
		sentenceAnnotator.process(baseJCas.getCas());
		int count = 0;
		int begin, end;
		FSIterator<Annotation> it = SentenceAnnotator.getSentenceIterator(
		    textJCas, SentenceAnnotator.SENTENCE_TYPE_NAME);

		while (it.hasNext()) {
			SyntaxAnnotation ann = (SyntaxAnnotation) it.next();
			begin = offsets.next();
			end = offsets.next();
			assertEquals(ann.getOffset().toString(), begin, ann.getBegin());
			assertEquals(ann.getOffset().toString(), end, ann.getEnd());
			assertEquals(SentenceAnnotator.URI, ann.getAnnotator());
			assertEquals(SentenceAnnotator.NAMESPACE, ann.getNamespace());
			assertEquals(SentenceAnnotator.IDENTIFIER, ann.getIdentifier());
			assertEquals(0.9999, ann.getConfidence(), 0.001);
			assertNull(ann.getProperties());
			count++;
		}

		assertEquals(2, count);
	}
}
