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
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.SyntaxAnnotation;

public class TestChunkAnnotator {

	AnalysisEngine annotator;
	JCas jcas;

	@Before
	public void setUp() throws UIMAException, IOException {
		DisableLogging.enableLogging(Level.WARNING);
		annotator = AnalysisEngineFactory
		    .createAnalysisEngine("txtfnnl.uima.openNLPChunkAEDescriptor");
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
		// TODO: what happened to And????
		int[] beginPositions = { 0, 5, 8, 29, 34, 37 };
		int[] endPositions = { 4, 7, 23, 33, 36, 48 };
		String[] tags = {
		    "DT",
		    "VBZ",
		    "DT",
		    "JJ",
		    "NN",
		    ".",
		    "CC",
		    "DT",
		    "VBZ",
		    "DT",
		    "CD",
		    "." };
		String[] chunks = { "NP", // This
		    "VP", // is
		    "NP", // a nice sentence
		    // And ???
		    "NP", // this
		    "VP", // is
		    "NP", // another one
		};
		jcas.setDocumentText(text);
		addSentence(0, 24);
		addSentence(25, 49);
		addTokens(new int[] { 0, 5, 8, 10, 15, 23, 25, 29, 34, 37, 45, 48 },
		    new int[] { 4, 7, 9, 14, 23, 24, 28, 33, 36, 44, 48, 49 }, tags);
		annotator.process(baseJCas.getCas());

		int count = 0;
		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(jcas);
		FSMatchConstraint chunkConstraint = ChunkAnnotator
		    .makeChunkConstraint(jcas);
		AnnotationIndex<Annotation> annIdx = jcas
		    .getAnnotationIndex(SyntaxAnnotation.type);

		while (sentenceIt.hasNext()) {
			SyntaxAnnotation sentence = (SyntaxAnnotation) sentenceIt.next();
			FSIterator<Annotation> chunkIt = jcas.createFilteredIterator(
			    annIdx.subiterator(sentence, true, true), chunkConstraint);

			while (chunkIt.hasNext()) {
				SyntaxAnnotation chunk = (SyntaxAnnotation) chunkIt.next();
				// make sure we are looking at the right token
				assertEquals("begin at index " + count, beginPositions[count],
				    chunk.getBegin());
				assertEquals("end at index " + count, endPositions[count],
				    chunk.getEnd());
				assertEquals("chunk '" + chunk.getCoveredText() + "'", 0.9,
				    chunk.getConfidence(), 0.1);
				assertEquals("chunk '" + chunk.getCoveredText() + "'",
				    chunks[count], chunk.getIdentifier());
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
