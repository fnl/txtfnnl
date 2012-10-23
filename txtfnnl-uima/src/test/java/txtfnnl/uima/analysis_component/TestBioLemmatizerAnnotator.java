package txtfnnl.uima.analysis_component;

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
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.PartOfSpeechAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.SyntaxAnnotation;

public class TestBioLemmatizerAnnotator {

	AnalysisEngine annotator;
	JCas jcas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		annotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.bioLemmatizerAEDescriptor");
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
		String text = "These are two nice sentences. And this could be getting better.";
		String[] lemma = {
		    "these",
		    "be",
		    "two",
		    "nice",
		    "sentence",
		    ".",
		    "and",
		    "this",
		    "could",
		    "be",
		    "get",
		    "well",
		    "." };
		int[] beginPositions = {
		    0,
		    6,
		    10,
		    14,
		    19,
		    28,
		    30,
		    34,
		    39,
		    45,
		    48,
		    56,
		    62 };
		int[] endPositions = {
		    5,
		    9,
		    13,
		    18,
		    28,
		    29,
		    33,
		    38,
		    44,
		    47,
		    55,
		    62,
		    63 };
		String[] tags = {
		    "DT",
		    "VBZ",
		    "CD",
		    "JJ",
		    "NNS",
		    ".",
		    "CC",
		    "DT",
		    "VBD",
		    "VBZ",
		    "VBG",
		    "RBR",
		    "." };
		jcas.setDocumentText(text);
		addSentence(0, 29);
		addSentence(30, 63);
		addTokens(beginPositions, endPositions, tags);
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
				// make sure we are looking at the right token
				assertEquals(Integer.toString(token.getBegin()) +
				             " begin at " + count, beginPositions[count],
				    token.getBegin());
				assertEquals(Integer.toString(token.getEnd()) + " end at " +
				             count, endPositions[count], token.getEnd());
				assertEquals("token '" + token.getCoveredText() + "'",
				    lemma[count], BioLemmatizerAnnotator.getLemma(token));
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

	private void addTokens(int[] begins, int[] ends, String[] tags) {
		for (int i = 0; i < begins.length; ++i) {
			SyntaxAnnotation ann = new SyntaxAnnotation(jcas, begins[i],
			    ends[i]);
			ann.setAnnotator(TokenAnnotator.URI);
			ann.setConfidence(1.0);
			ann.setIdentifier(TokenAnnotator.IDENTIFIER);
			ann.setNamespace(TokenAnnotator.NAMESPACE);
			Property tag = new Property(jcas);
			Property prob = new Property(jcas);
			tag.setName(PartOfSpeechAnnotator.POS_TAG_VALUE_PROPERTY_NAME);
			prob.setName(PartOfSpeechAnnotator.POS_TAG_CONFIDENCE_PROPERTY_NAME);
			tag.setValue(tags[i]);
			prob.setValue("1.0");
			FSArray properties = new FSArray(jcas, 2);
			properties.set(0, tag);
			properties.set(1, prob);
			ann.setProperties(properties);
			ann.addToIndexes(jcas);
		}
	}
}
