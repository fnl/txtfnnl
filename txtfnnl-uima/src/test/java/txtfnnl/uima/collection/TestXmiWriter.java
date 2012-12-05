package txtfnnl.uima.collection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.utils.IOUtils;

public class TestXmiWriter {

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
			XmiWriter.configure(testDir));
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
	public void testFormatXmi() throws UnsupportedEncodingException,
	        IOException, UIMAException {
		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    XmiWriter.configure(testDir, false, true));
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		String xml = IOUtils.read(new FileInputStream(outputFile), "UTF-8");
		assertTrue(xml.indexOf("encoding=\"UTF-8\"") > -1);
		assertTrue(xml.indexOf("\n") > -1);
		outputFile.delete();
	}

	@Test
	public void testUseXml11() throws UnsupportedEncodingException,
	        IOException, UIMAException {
		String enc = System.getProperty("file.encoding");
		
		if (IOUtils.isMacOSX())
			enc = "UTF-8";

		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    XmiWriter.configure(testDir, false, false, true));
		baseCas = fileSystemXmiConsumer.newCAS();
		rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
		textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
		textCas.setDocumentText("test:\f");
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		fileSystemXmiConsumer.process(baseCas);
		String xml = IOUtils.read(new FileInputStream(outputFile), enc);
		assertTrue(xml, xml.indexOf("<?xml version=\"1.1\"") == 0);
		Pattern regex = Pattern.compile("encoding *= *\"(.*?)\"");
		Matcher match = regex.matcher(xml);
		assertTrue(xml, match.find());
		assertTrue(enc + " != " + match.group(1),
		    xml.indexOf("encoding=\"" + enc + "\"") > -1);
		outputFile.delete();
	}

	@Test
	public void testOverwriteFiles() throws IOException, UIMAException {
		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    XmiWriter.configure(testDir, true));
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		assertTrue(outputFile.createNewFile());
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		File altFile = new File(testDir, "raw.ext.2.xmi");
		assertFalse("file " + altFile.getAbsolutePath() + " exists",
		    altFile.exists());
		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    XmiWriter.class,
		    XmiWriter.PARAM_OUTPUT_DIRECTORY, OUTPUT_DIR,
		    XmiWriter.PARAM_OVERWRITE_FILES, Boolean.FALSE);
		fileSystemXmiConsumer.process(baseCas);
		assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
		    outputFile.exists());
		assertTrue("file " + altFile.getAbsolutePath() + " does not exist",
		    altFile.exists());
		outputFile.delete();
		altFile.delete();
	}

	@Test
	public void testEncoding() throws UnsupportedEncodingException,
	        IOException, UIMAException {
		fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(
		    XmiWriter.configure(testDir, "UTF-16", false, false, false));
		rawCas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
		fileSystemXmiConsumer.process(baseCas);
		String xml = IOUtils.read(new FileInputStream(outputFile), "UTF-16");
		assertTrue(xml, xml.indexOf("<?xml version=\"1.0\"") == 0);
		outputFile.delete();
	}

}