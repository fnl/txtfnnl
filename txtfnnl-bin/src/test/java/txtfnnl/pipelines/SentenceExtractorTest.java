package txtfnnl.pipelines;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.resource.metadata.ConfigurationParameterSettings;

import org.uimafit.testing.util.DisableLogging;

import txtfnnl.opennlp.uima.sentdetect.SentenceLineWriter;
import txtfnnl.uima.collection.FileCollectionReader;
import txtfnnl.uima.collection.FileSystemCollectionReader;
import txtfnnl.utils.IOUtils;

public class SentenceExtractorTest {

	@Test
	public void testDirectoryReaderSetup() throws IOException, UIMAException {
		File inputDir = IOUtils.mkTmpDir();
		SentenceSplitter se = new SentenceSplitter(inputDir, "mime type",
		    true, null, null, true, true);
		ConfigurationParameterSettings cps = se.collectionReader
		    .getCollectionReaderMetaData().getConfigurationParameterSettings();

		assertEquals(inputDir.getCanonicalPath(),
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_DIRECTORY));
		assertEquals("mime type",
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_MIME_TYPE));
		assertEquals(Boolean.TRUE,
		    cps.getParameterValue(FileSystemCollectionReader.PARAM_RECURSIVE));
	}

	@Test
	public void testFileReaderSetup() throws IOException, UIMAException {
		File inputFile = File.createTempFile("out",
		    Long.toString(System.nanoTime()));
		String[] inputFiles = new String[] { inputFile.getAbsolutePath() };
		SentenceSplitter se = new SentenceSplitter(inputFiles, null, null,
		    null, true, true);
		ConfigurationParameterSettings cps = se.collectionReader
		    .getCollectionReaderMetaData().getConfigurationParameterSettings();

		assertArrayEquals(inputFiles,
		    (Object[]) cps.getParameterValue(FileCollectionReader.PARAM_FILES));
		assertNull(cps.getParameterValue(FileCollectionReader.PARAM_MIME_TYPE));
	}

	@Test
	public void testLineWriterSetup() throws IOException, UIMAException {
		File inputFile = File.createTempFile("input",
		    Long.toString(System.nanoTime()));
		String[] inputFiles = new String[] { inputFile.getAbsolutePath() };
		File outputDir = IOUtils.mkTmpDir();
		SentenceSplitter se = new SentenceSplitter(inputFiles, null,
		    outputDir, "encoding", true, true);
		ConfigurationParameterSettings cps = se.sentenceLineWriter
		    .getMetaData().getConfigurationParameterSettings();

		assertEquals("encoding",
		    cps.getParameterValue(SentenceLineWriter.PARAM_ENCODING));
		assertEquals(outputDir.getCanonicalPath(),
		    cps.getParameterValue(SentenceLineWriter.PARAM_OUTPUT_DIRECTORY));
		assertEquals(Boolean.TRUE,
		    cps.getParameterValue(SentenceLineWriter.PARAM_OVERWRITE_FILES));
		assertEquals(Boolean.TRUE,
		    cps.getParameterValue(SentenceLineWriter.PARAM_JOIN_LINES));
	}

	@Test
	public void testRunningThePipeline() throws UIMAException, IOException {
		File inputFile = new File("src/test/resources/pubmed.xml");
		assert inputFile.exists() : "test file does not exist";
		ByteArrayOutputStream outContent = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outContent));
		DisableLogging.enableLogging(Level.WARNING);
		SentenceSplitter se = new SentenceSplitter(
		    new String[] { inputFile.getCanonicalPath() }, null, null,
		    "UTF-8", false, false);
		se.run();
		String content = outContent.toString();
		assertTrue(content.indexOf("studied.\nAs(4)O(6)") > 0);
		System.setOut(null);
	}
}
