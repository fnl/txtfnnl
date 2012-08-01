package txtfnnl.pipelines;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.resource.metadata.ConfigurationParameterSettings;

import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.collection.FileCollectionReader;
import txtfnnl.uima.collection.FileSystemCollectionReader;
import txtfnnl.uima.collection.FileSystemXmiWriter;
import txtfnnl.utils.IOUtils;

public class KnownGeneAnnotatorTest {

	@Test
	public void testDirectoryReaderSetup() throws IOException, UIMAException,
	        ClassNotFoundException {
		File inputDir = IOUtils.mkTmpDir();
		KnownGeneAnnotator gAnn = new KnownGeneAnnotator(inputDir,
		    "mime type", false, IOUtils.mkTmpDir(), null, true, null,
		    File.createTempFile("gene_", ".map"), "TODO", null, null);
		ConfigurationParameterSettings cps = gAnn.collectionReader
		    .getCollectionReaderMetaData().getConfigurationParameterSettings();

		assertEquals(inputDir.getCanonicalPath(),
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_DIRECTORY));
		assertEquals("mime type",
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_MIME_TYPE));
		assertEquals(Boolean.FALSE,
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_RECURSIVE));
	}

	@Test
	public void testFileReaderSetup() throws IOException, UIMAException,
	        ClassNotFoundException {
		File inputFile = File.createTempFile("input_", ".txt");
		KnownGeneAnnotator gAnn = new KnownGeneAnnotator(
		    new String[] { inputFile.getCanonicalPath() }, "mime type",
		    IOUtils.mkTmpDir(), null, true, null, File.createTempFile("gene_",
		        ".map"), "TODO", null, null);
		ConfigurationParameterSettings cps = gAnn.collectionReader
		    .getCollectionReaderMetaData().getConfigurationParameterSettings();

		assertArrayEquals(new String[] { inputFile.getCanonicalPath() },
		    (String[]) cps.getParameterValue(FileCollectionReader.PARAM_FILES));
		assertEquals("mime type",
		    cps.getParameterValue(FileCollectionReader.PARAM_MIME_TYPE));
	}

	@Test
	public void testXmiWriterSetup() throws IOException, UIMAException,
	        ClassNotFoundException {
		File outputDir = IOUtils.mkTmpDir();
		KnownGeneAnnotator gAnn = new KnownGeneAnnotator(IOUtils.mkTmpDir(),
		    null, true, outputDir, "encoding", false, null,
		    File.createTempFile("gene_", ".map"), "TODO", null, null);
		ConfigurationParameterSettings cps = gAnn.xmiWriter
		    .getAnalysisEngineMetaData().getConfigurationParameterSettings();

		assertEquals("encoding",
		    cps.getParameterValue(FileSystemXmiWriter.PARAM_ENCODING));
		assertEquals(outputDir.getCanonicalPath(),
		    cps.getParameterValue(FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY));
		assertEquals(Boolean.FALSE,
		    cps.getParameterValue(FileSystemXmiWriter.PARAM_OVERWRITE_FILES));
	}

	@Test
	public void testAnalysisEngineSetup() throws IOException, UIMAException,
	        ClassNotFoundException {
		KnownGeneAnnotator gAnn = new KnownGeneAnnotator(IOUtils.mkTmpDir(),
		    null, true, IOUtils.mkTmpDir(), null, true, "namespace",
		    File.createTempFile("gene_", ".map"), "TODO", null, null);
		ConfigurationParameterSettings cps = gAnn.knownEntityAED
		    .getAnalysisEngineMetaData().getConfigurationParameterSettings();

		assertEquals("namespace",
		    cps.getParameterValue(KnownEntityAnnotator.PARAM_NAMESPACE));
	}

	@Test
	public void testRunningThePipeline() throws IOException, UIMAException,
	        ClassNotFoundException {
		File inputFile = new File("src/test/resources/pubmed.xml");
		File outputDir = IOUtils.mkTmpDir();
		assert inputFile.exists() : "test file does not exist";
		File tmpDb = File.createTempFile("jdbc_resource_", null);
		tmpDb.deleteOnExit();
		String connectionUrl = "jdbc:h2:" + tmpDb.getCanonicalPath();
		DisableLogging.enableLogging(Level.WARNING);
		KnownGeneAnnotator gAnn = new KnownGeneAnnotator(
		    new String[] { inputFile.getCanonicalPath() }, null, outputDir,
		    "UTF-8", false, "namespace", File.createTempFile("gene_",
		        ".map"), connectionUrl, null, null);
		gAnn.run();
		File outputFile = new File(outputDir, "pubmed.xml.xmi");
		assertTrue(outputFile.getCanonicalPath() + " does not exist",
		    outputFile.exists());
	}

}
