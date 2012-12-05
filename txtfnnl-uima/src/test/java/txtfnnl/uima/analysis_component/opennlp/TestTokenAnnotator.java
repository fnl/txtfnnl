package txtfnnl.uima.analysis_component.opennlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

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
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
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
		AnalysisEngineDescription aed = TokenAnnotator.configure();
		aed.doFullValidation();
	}

	@Test
	public void testReadDefaultModels() throws IOException {
		for (String path : new String[] {
		    TokenAnnotator.DEFAULT_TOKEN_MODEL_FILE,
		    TokenAnnotator.DEFAULT_POS_MODEL_FILE,
		    TokenAnnotator.DEFAULT_CHUNK_MODEL_FILE }) {
			path = path.substring("file:".length()); // remove URL prefix
			InputStream is = ClassLoader.getSystemResourceAsStream(path);
			assertNotNull(path, is);
			int bytes = is.read();
			assertTrue("" + bytes + " bytes read from " + path, bytes > 0);
		}
	}

	@Test
	public void testDestroy() throws ResourceInitializationException, UIMAException, IOException {
		annotator = AnalysisEngineFactory.createPrimitive(TokenAnnotator.configure());
		annotator.destroy();
		assertTrue("success", true);
	}

	@Test
	public void testProcess() throws UIMAException, IOException {
		annotator = AnalysisEngineFactory.createPrimitive(TokenAnnotator.configure());
		JCas baseJCas = annotator.newJCas();
		jcas = baseJCas.createView(Views.CONTENT_TEXT.toString());
		String text = "This is a nice sentence. And this is another one.";
		int[] beginPositions = { 0, 5, 8, 10, 15, 23, 25, 29, 34, 37, 45, 48 };
		int[] endPositions = { 4, 7, 9, 14, 23, 24, 28, 33, 36, 44, 48, 49 };
		String[] tags = { "DT", // This
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
		String[] chunks = { "NP", // This
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
		FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
		AnnotationIndex<Annotation> annIdx = jcas.getAnnotationIndex(TokenAnnotation.type);

		while (sentenceIt.hasNext()) {
			SentenceAnnotation sentence = (SentenceAnnotation) sentenceIt.next();
			FSIterator<Annotation> tokenIt = annIdx.subiterator(sentence, true, true);

			while (tokenIt.hasNext()) {
				TokenAnnotation token = (TokenAnnotation) tokenIt.next();
				assertEquals(Integer.toString(token.getBegin()), beginPositions[count],
				    token.getBegin());
				assertEquals(Integer.toString(token.getEnd()), endPositions[count], token.getEnd());
				assertEquals(TokenAnnotator.URI, token.getAnnotator());
				assertEquals(TokenAnnotator.NAMESPACE, token.getNamespace());
				assertEquals(TokenAnnotator.IDENTIFIER, token.getIdentifier());
				assertEquals(0.999, token.getConfidence(), 0.01);
				assertEquals(tags[count], token.getPos());
				assertEquals(chunks[count], token.getChunk());
				count++;
			}
		}

		assertEquals(beginPositions.length, count);
	}

	private void addSentence(int begin, int end) {
		SentenceAnnotation ann = new SentenceAnnotation(jcas, begin, end);
		ann.setAnnotator(SentenceAnnotator.URI);
		ann.setConfidence(1.0);
		ann.setIdentifier(SentenceAnnotator.IDENTIFIER);
		ann.setNamespace(SentenceAnnotator.NAMESPACE);
		ann.addToIndexes(jcas);
	}
}
