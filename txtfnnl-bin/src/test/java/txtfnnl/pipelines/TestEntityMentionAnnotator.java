package txtfnnl.pipelines;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

public class TestEntityMentionAnnotator {

	@Test
	public void testDirectoryReaderSetup() throws IOException, UIMAException,
	        ClassNotFoundException {
		File inputDir = IOUtils.mkTmpDir();
		EntityMentionAnnotator gAnn = new EntityMentionAnnotator(inputDir,
		    "mime type", false, IOUtils.mkTmpDir(), null, true, null,
		    File.createTempFile("gene_", ".map"), null, "TODO", null, null);
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
		EntityMentionAnnotator gAnn = new EntityMentionAnnotator(
		    new String[] { inputFile.getCanonicalPath() }, "mime type",
		    IOUtils.mkTmpDir(), null, true, null, File.createTempFile("gene_",
		        ".map"), null, "TODO", null, null);
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
		EntityMentionAnnotator gAnn = new EntityMentionAnnotator(
		    IOUtils.mkTmpDir(), null, true, outputDir, "encoding", false,
		    null, File.createTempFile("gene_", ".map"), null, "TODO", null,
		    null);
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
		EntityMentionAnnotator gAnn = new EntityMentionAnnotator(
		    IOUtils.mkTmpDir(), null, true, IOUtils.mkTmpDir(), null, true,
		    "namespace", File.createTempFile("gene_", ".map"), null, "TODO",
		    null, null);
		ConfigurationParameterSettings cps = gAnn.knownEntityAED
		    .getAnalysisEngineMetaData().getConfigurationParameterSettings();

		assertEquals("namespace",
		    cps.getParameterValue(KnownEntityAnnotator.PARAM_NAMESPACE));
	}

	@Test
	public void testRunningThePipeline() throws IOException, UIMAException,
	        ClassNotFoundException, SQLException {
		File inputFile = new File("src/test/resources/pubmed.xml");
		File outputDir = IOUtils.mkTmpDir();
		assert inputFile.exists() : "test file does not exist";
		File tmpDb = File.createTempFile("jdbc_resource_", null);
		tmpDb.deleteOnExit();
		String connectionUrl = "jdbc:h2:" + tmpDb.getCanonicalPath();
		Connection jdbc_resource = DriverManager.getConnection(connectionUrl);
		Statement stmt = jdbc_resource.createStatement();
		stmt.executeUpdate("CREATE TABLE gene_refs(id INT PRIMARY KEY,"
		                   + "                    namespace VARCHAR,"
		                   + "                    accession VARCHAR)");
		stmt.executeUpdate("CREATE TABLE genes2proteins(gene_id INT,"
		                   + "                    protein_id INT)");
		stmt.executeUpdate("CREATE TABLE protein_strings(id INT PRIMARY KEY,"
		                   + "                    cat VARCHAR,"
		                   + "                    value VARCHAR)");
		stmt.executeUpdate("CREATE TABLE gene_strings(id INT PRIMARY KEY,"
		                   + "                    cat VARCHAR,"
		                   + "                    value VARCHAR)");
		EntityMentionAnnotator gAnn = new EntityMentionAnnotator(
		    new String[] { inputFile.getCanonicalPath() }, null, outputDir,
		    "UTF-8", false, "namespace", File.createTempFile("gene_", ".map"),
		    EntityMentionAnnotator.DEFAULT_SQL_QUERIES, connectionUrl, null,
		    null);
		Level l = DisableLogging.disableLogging();
		DisableLogging.enableLogging(Level.SEVERE);
		gAnn.run();
		DisableLogging.enableLogging(l);
		File outputFile = new File(outputDir, "pubmed.xml.xmi");
		assertTrue(outputFile.getCanonicalPath() + " does not exist",
		    outputFile.exists());
	}

}
