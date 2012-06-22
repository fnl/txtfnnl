package txtfnnl.opennlp.uima.sentdetect;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.jcas.JCas;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.TextAnnotation;

public class SentenceLineWriterTest {

	public static final String DESCRIPTOR = "txtfnnl.uima.openNLPSentenceLineWriterDescriptor";

	@Before
	public void setUp() {
		DisableLogging.enableLogging(Level.WARNING);
	}

	@Test
	public void testInitializeUimaContext() throws UIMAException, IOException {
		AnalysisEngine slw = AnalysisEngineFactory
		    .createAnalysisEngine(DESCRIPTOR);

		for (String p : new String[] {
		    SentenceLineWriter.PARAM_OUTPUT_DIRECTORY,
		    SentenceLineWriter.PARAM_OVERWRITE_FILES,
		    SentenceLineWriter.PARAM_PRINT_TO_STDOUT,
		    SentenceLineWriter.PARAM_ENCODING }) {
			assertNull("Parameter " + p + " does not default to null.",
			    slw.getConfigParameterValue(p));
		}
	}

	@Test
	public void testProcessJCas() throws UIMAException, IOException {
		AnalysisEngine slw = AnalysisEngineFactory
		    .createAnalysisEngine(DESCRIPTOR);

		String s1 = "This is\na sentence.";
		String s2 = "This is\nanother one.";
		JCas baseCas = slw.newJCas();
		JCas rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
		JCas textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
		rawCas.setSofaDataURI("file:/dummy", "mime/dummy");
		textCas.setDocumentText(s1 + " " + s2);
		TextAnnotation a1 = new TextAnnotation(textCas);
		TextAnnotation a2 = new TextAnnotation(textCas);
		a1.setBegin(0);
		a1.setEnd(s1.length());
		a1.setNamespace(SentenceAnnotator.NAMESPACE);
		a1.setIdentifier(SentenceAnnotator.IDENTIFIER);
		a1.addToIndexes();
		a2.setBegin(s1.length() + " ".length());
		a2.setEnd(s1.length() + s2.length() + " ".length());
		a2.setNamespace(SentenceAnnotator.NAMESPACE);
		a2.setIdentifier(SentenceAnnotator.IDENTIFIER);
		a2.addToIndexes();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outputStream));
		slw.process(baseCas);
		String result = s1 + System.getProperty("line.separator") + s2 +
		                System.getProperty("line.separator");
		assertEquals(result, outputStream.toString());

	}

}
