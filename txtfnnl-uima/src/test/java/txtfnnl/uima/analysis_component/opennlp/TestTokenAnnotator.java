package txtfnnl.uima.analysis_component.opennlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

public class TestTokenAnnotator {

	AnalysisEngine annotator;
	JCas jcas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		annotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPTokenAEDescriptor");
	}

	@Test
	public void testDestroy() {
		annotator.destroy();
		assertTrue("success", true);
	}

	@Test
	public void testProcessCAS() throws UIMAException, IOException {
		JCas baseJCas = annotator.newJCas();
		jcas = baseJCas.createView(Views.CONTENT_TEXT.toString());
		String text = "This is a nice sentence. And this is another one.";
		int[] beginPositions = { 0, 5, 8, 10, 15, 23, 25, 29, 34, 37, 45, 48 };
		int[] endPositions = { 4, 7, 9, 14, 23, 24, 28, 33, 36, 44, 48, 49 };
		jcas.setDocumentText(text);
        addSentence(0, 24);
        addSentence(25, 49);
		annotator.process(baseJCas.getCas());

		int count = 0;
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(jcas);
		FSMatchConstraint tokenConstraint = TokenAnnotator
		    .makeTokenConstraint(jcas);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);

		while (sentenceIt.hasNext()) {
			SyntaxAnnotation sentence = (SyntaxAnnotation) sentenceIt.next();
			FSIterator<Annotation> tokenIt = jcas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), tokenConstraint);

			while (tokenIt.hasNext()) {
				SyntaxAnnotation token = (SyntaxAnnotation) tokenIt.next();
				assertEquals(Integer.toString(token.getBegin()), beginPositions[count],
				    token.getBegin());
				assertEquals(Integer.toString(token.getEnd()), endPositions[count],
				    token.getEnd());
				assertEquals(TokenAnnotator.URI, token.getAnnotator());
				assertEquals(TokenAnnotator.NAMESPACE, token.getNamespace());
				assertEquals(TokenAnnotator.IDENTIFIER, token.getIdentifier());
				assertEquals(0.999, token.getConfidence(), 0.01);
				count++;
			}
		}

		assertEquals(beginPositions.length, count);
	}

	private void addSentence(int begin, int end) {
	    SyntaxAnnotation ann = new SyntaxAnnotation(jcas, begin, end);
		ann.setAnnotator(SentenceAnnotator.URI);
		ann.setConfidence(1.0);
		ann.setIdentifier(SentenceAnnotator.IDENTIFIER);
		ann.setNamespace(SentenceAnnotator.NAMESPACE);
		ann.addToIndexes(jcas);
    }
}
