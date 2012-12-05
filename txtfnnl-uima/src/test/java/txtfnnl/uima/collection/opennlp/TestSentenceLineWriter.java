package txtfnnl.uima.collection.opennlp;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.SentenceLineWriter;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.utils.IOUtils;

public class TestSentenceLineWriter {

	private static final String SENTENCE_1 = "  This is\na sentence.  ";
	private static final String SENTENCE_2 = "  This is\nanother one.  ";

	@Before
	public void setUp() {
		DisableLogging.enableLogging(Level.WARNING);
	}

	@Test
	public void testInitializeUimaContext() throws UIMAException, IOException {
		AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter.configure());

		for (String p : new String[] {
		    SentenceLineWriter.PARAM_OUTPUT_DIRECTORY,
		    SentenceLineWriter.PARAM_ENCODING }) {
			assertNull("Parameter " + p + " does not default to null.",
			    slw.getConfigParameterValue(p));
		}

		for (String p : new String[] { SentenceLineWriter.PARAM_OVERWRITE_FILES }) {
			assertFalse("Parameter " + p + " does not default to false.",
			    (Boolean) slw.getConfigParameterValue(p));
		}
		for (String p : new String[] {
		    SentenceLineWriter.PARAM_PRINT_TO_STDOUT,
		    SentenceLineWriter.PARAM_JOIN_LINES }) {
			assertTrue("Parameter " + p + " does not default to true.",
			    (Boolean) slw.getConfigParameterValue(p));
		}

	}

	@Test
	public void testProcessJCasToStdout() throws UIMAException, IOException {
		AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter.configure());
		ByteArrayOutputStream outputStream = processHelper(slw);
		String result = SENTENCE_1.replace('\n', ' ').trim() +
		                System.getProperty("line.separator") +
		                SENTENCE_2.replace('\n', ' ').trim() +
		                System.getProperty("line.separator");

		assertEquals(result, outputStream.toString());
	}

	@Test
	public void testProcessJCasDisableJoinLines() throws UIMAException, IOException {
		AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter
		    .configure(false));
		String result = SENTENCE_1.trim() + System.getProperty("line.separator") +
		                SENTENCE_2.trim() + System.getProperty("line.separator");
		ByteArrayOutputStream outputStream = processHelper(slw);

		assertEquals(result, outputStream.toString());
	}

	@Test
	public void testProcessJCasOutputDir() throws UIMAException, IOException {
		File tmpDir = IOUtils.mkTmpDir();
		File existing = new File(tmpDir, "test.txt.txt");

		assertTrue(existing.createNewFile());

		AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter.configure(
		    tmpDir, "UTF-32"));
		String result = SENTENCE_1.replace('\n', ' ').trim() +
		                System.getProperty("line.separator") +
		                SENTENCE_2.replace('\n', ' ').trim() +
		                System.getProperty("line.separator");
		processHelper(slw);
		File created = new File(tmpDir, "test.txt.2.txt");
		FileInputStream fis = new FileInputStream(created);

		assertTrue(created.exists());
		assertEquals(result, IOUtils.read(fis, "UTF-32"));

		existing.delete();
		created.delete();
		tmpDir.delete();
	}

	ByteArrayOutputStream processHelper(AnalysisEngine sentenceLineWriter)
	        throws ResourceInitializationException, CASException, AnalysisEngineProcessException {
		JCas baseCas = sentenceLineWriter.newJCas();
		JCas rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
		JCas textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
		rawCas.setSofaDataURI("http://example.com/test.txt", "mime/dummy");
		textCas.setDocumentText(SENTENCE_1 + " " + SENTENCE_2);
		SentenceAnnotation a1 = new SentenceAnnotation(textCas);
		SentenceAnnotation a2 = new SentenceAnnotation(textCas);
		a1.setBegin(0);
		a1.setEnd(SENTENCE_1.length());
		a1.setNamespace(SentenceAnnotator.NAMESPACE);
		a1.setIdentifier(SentenceAnnotator.IDENTIFIER);
		a1.addToIndexes();
		a2.setBegin(SENTENCE_1.length() + " ".length());
		a2.setEnd(SENTENCE_1.length() + SENTENCE_2.length() + " ".length());
		a2.setNamespace(SentenceAnnotator.NAMESPACE);
		a2.setIdentifier(SentenceAnnotator.IDENTIFIER);
		a2.addToIndexes();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		sentenceLineWriter.process(baseCas);
		return outputStream;
	}

}
