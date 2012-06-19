package txtfnnl.opennlp.uima.sentdetect;

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
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.TextAnnotation;

public class SentenceAnnotatorTest {

	AnalysisEngine sentenceAnnotator;
	JCas baseJCas;
	JCas textJCas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		sentenceAnnotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPSentenceAEDescriptor");
		baseJCas = sentenceAnnotator.newJCas();
		textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
	}

	@Test
	public void testDestroy() {
		sentenceAnnotator.destroy();
		assertTrue("success", true);
	}

	@Test
	public void testProcessCAS() throws AnalysisEngineProcessException {
		String s1 = "This is one sentence.";
		String s2 = "And this is another sentence.";
		Iterator<Integer> offsets = Arrays.asList(
		    new Integer[] {
		        0,
		        s1.length(),
		        s1.length() + 1,
		        s1.length() + s2.length() + 1 }).iterator();
		textJCas.setDocumentText(s1 + " " + s2);
		sentenceAnnotator.process(baseJCas.getCas());
		int count = 0;
		int begin, end;

		for (TextAnnotation ann : JCasUtil.select(textJCas,
		    TextAnnotation.class)) {
			begin = offsets.next();
			end = offsets.next();
			assertEquals(begin, ann.getBegin());
			assertEquals(end, ann.getEnd());
			assertEquals(SentenceAnnotator.URI, ann.getAnnotator());
			assertEquals(SentenceAnnotator.annotationNamespace,
			    ann.getNamespace());
			assertEquals(SentenceAnnotator.annotationIdentifier,
			    ann.getIdentifier());
			assertEquals(0.9999, ann.getConfidence(), 0.001);
			assertNull(ann.getProperties());
			count++;
		}

		assertEquals(2, count);
	}

}
