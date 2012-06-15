package txtfnnl.uima.collection;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;

public class TestFileSystemXmiWriter {

	private static final String OUTPUT_DIR = "src/test/resources/output-test";
	private File testDir = new File(OUTPUT_DIR);
	private File outputFile = new File(testDir, "raw.ext.xmi");
	private AnalysisEngine fileSystemXmiConsumer;
	private CAS baseCas;
	private CAS rawCas;
	private CAS textCas;

	@Before
	public void setUp() throws Exception {
		// clean up possible left-overs
		if (outputFile.exists())
			outputFile.delete();
		if (testDir.exists())
			testDir.delete();

		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    FileSystemXmiWriter.class,
		    FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY, OUTPUT_DIR);
		baseCas = fileSystemXmiConsumer.newCAS();
		rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
		textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
		textCas.setDocumentText("test");

		if (outputFile.exists())
			throw new AssertionError("file " + outputFile.getAbsolutePath() +
			                         " exists");
	}

	@After
	public void tearDown() throws Exception {
		testDir.deleteOnExit();
	}

	@Test
	public void testProcess() throws AnalysisEngineProcessException {
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		outputFile.delete();
	}

	@Test
	public void testMissingRawSofaURI() throws AnalysisEngineProcessException {
		fileSystemXmiConsumer.process(baseCas);
		File altFile = new File(testDir, "doc-000001.xmi");
		assertTrue("file " + altFile.getAbsolutePath() + " does not exist",
			altFile.exists());
		altFile.delete();
	}
	
	@Test
	public void testFormatXMI() throws ResourceInitializationException, AnalysisEngineProcessException, FileNotFoundException {
		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    FileSystemXmiWriter.class,
		    FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY, OUTPUT_DIR,
		    FileSystemXmiWriter.PARAM_FORMAT_XMI, Boolean.TRUE);
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		outputFile.delete();
	}
}
