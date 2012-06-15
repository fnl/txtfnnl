package txtfnnl.uima.collection;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;

public class TestFileSystemXmiConsumer {

	private static final String OUTPUT_DIR = "src/test/resources/output-test";
	File testDir = new File(OUTPUT_DIR);
	File outputFile = new File(testDir, "raw.ext.xmi");
	AnalysisEngine fileSystemXmiConsumer;
	CAS baseCas;
	CAS rawCas;
	CAS textCas;

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
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
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
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		outputFile.delete();
	}

}
