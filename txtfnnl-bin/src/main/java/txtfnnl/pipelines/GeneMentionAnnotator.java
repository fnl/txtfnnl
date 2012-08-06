/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.pipeline.SimplePipeline;

import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class GeneMentionAnnotator implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription knownEntityAED;
	AnalysisEngineDescription xmiWriter;

	static final String DEFAULT_NAMESPACE = "http://purl.org/bio-entity/";
	static final String DEFAULT_DATABASE = "gnamed";
	static final String DEFAULT_GMAP_FILE = "doc2gene.map";
	static final String[] SQL_QUERIES = new String[] {
	    "SELECT DISTINCT p.value FROM gene_refs AS g "
	            + "JOIN genes2proteins AS g2p ON g.id = g2p.gene_id "
	            + "JOIN protein_strings AS p ON g2p.protein_id = p.id "
	            + "WHERE p.cat IN ('name', 'symbol') "
	            + "AND g.namespace=? AND g.accession=?",
	    "SELECT s.value FROM gene_refs AS r "
	            + "JOIN gene_strings AS s ON r.id = s.id "
	            + "WHERE s.cat IN ('name', 'symbol') "
	            + "AND r.namespace=? AND r.accession=?" };

	private GeneMentionAnnotator(File outputDir, String characterEncoding,
	                             boolean replaceFiles, String namespace,
	                             File geneMap, String dbUrl, String dbUser,
	                             String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		tikaAED = PipelineUtils.getTikaAnnotator(true,
		    characterEncoding);
		xmiWriter = PipelineUtils.getXmiFileWriter(outputDir,
		    characterEncoding, replaceFiles);
		
		knownEntityAED = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    namespace, KnownEntityAnnotator.PARAM_QUERIES, SQL_QUERIES);
		ExternalResourceFactory.createDependencyAndBind(knownEntityAED,
		    KnownEntityAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    EntityStringMapResource.class,
		    "file:" + geneMap.getCanonicalPath());
		Class.forName("org.postgresql.Driver");
		ExternalResourceFactory.createDependencyAndBind(knownEntityAED,
		    KnownEntityAnnotator.MODEL_KEY_JDBC_CONNECTION,
		    JdbcConnectionResourceImpl.class, dbUrl,
		    JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
		    "org.postgresql.Driver",
		    JdbcConnectionResourceImpl.PARAM_USERNAME, dbUser,
		    JdbcConnectionResourceImpl.PARAM_PASSWORD, dbPass);
	}

	public GeneMentionAnnotator(File inputDirectory, String mimeType,
	                            boolean recurseDirectory,
	                            File outputDirectory,
	                            String characterEncoding,
	                            boolean replaceFiles, String namespace,
	                            File geneMap, String dbUrl, String dbUser,
	                            String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    geneMap, dbUrl, dbUser, dbPass);
		collectionReader = PipelineUtils.getCollectionReader(inputDirectory,
		    mimeType, recurseDirectory);
	}

	public GeneMentionAnnotator(String[] inputFiles, String mimeType,
	                            File outputDirectory,
	                            String characterEncoding,
	                            boolean replaceFiles, String namespace,
	                            File geneMap, String dbUrl, String dbUser,
	                            String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    geneMap, dbUrl, dbUser, dbPass);
		collectionReader = PipelineUtils.getCollectionReader(inputFiles,
		    mimeType);
	}

	/**
	 * Run the configured pipeline.
	 * 
	 * @throws UIMAException
	 * @throws IOException
	 */
	public void run() throws UIMAException, IOException {
		SimplePipeline.runPipeline(collectionReader, tikaAED, knownEntityAED,
		    xmiWriter);
	}

	/**
	 * Execute a known gene entity annotation pipeline.
	 * 
	 * @param arguments command line arguments; see --help for more
	 *        information.
	 */
	public static void main(String[] arguments) {
		CommandLine cmd = null;
		CommandLineParser parser = new PosixParser();
		File geneMap;
		File inputDirectory = null;
		File outputDirectory;
		Pipeline annotator;
		Options opts = new Options();
		String dbUrl;

		opts.addOption("d", "db-name", true, "name of the 'gnamed' DB [" +
		                                     DEFAULT_DATABASE + "]");
		opts.addOption("m", "gene-map", true, "name of the gene map file [" +
		                                      DEFAULT_GMAP_FILE + "]");
		opts.addOption("n", "namespace", true,
		    "namespace of the gene annotations [" + DEFAULT_NAMESPACE + "]");
		opts.addOption("p", "db-password", true,
		    "password for the DB server (if any is needed)");
		opts.addOption("s", "db-server", true,
		    "hostname of the DB server (incl. port) [localhost]");
		opts.addOption("u", "db-username", true,
		    "username for the DB server (if any is needed)");

		PipelineUtils.addLogAndHelpOptions(opts);

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = PipelineUtils.loggingSetup(
		    GeneMentionAnnotator.class.getName(), cmd, opts,
		    "txtfnnl gma [options] <output dir> <input dir|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		boolean replace = cmd.hasOption('X');
		String dbName = cmd.getOptionValue('d');
		String geneMapPath = cmd.getOptionValue('m');
		String namespace = cmd.getOptionValue('n');
		String dbPass = cmd.getOptionValue('p');
		String dbHost = cmd.getOptionValue('s');
		String dbUser = cmd.getOptionValue('u');

		if (inputFiles.length == 1) {
			System.err.println("too few arguments (" + inputFiles.length +
			                   "/2+)");
			System.exit(1); // == exit ==
		}
		outputDirectory = new File(inputFiles[0]);
		inputFiles = Arrays.copyOfRange(inputFiles, 1, inputFiles.length);

		if (namespace == null)
			namespace = DEFAULT_NAMESPACE;

		if (geneMapPath == null)
			geneMapPath = DEFAULT_GMAP_FILE;

		if (dbName == null)
			dbName = DEFAULT_DATABASE;

		if (dbHost == null)
			dbHost = "localhost";

		dbUrl = "jdbc:postgresql://" + dbHost + "/" + dbName;

		if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
			System.err.println("cannot write to directory '" +
			                   outputDirectory.getPath() + "'");
			System.exit(1); // == exit ==
		}

		if (inputFiles.length == 1) {
			inputDirectory = new File(inputFiles[0]);

			if (inputDirectory.isFile() && inputDirectory.canRead())
				inputDirectory = null;
		} else {
			for (String fn : inputFiles) {
				File tmp = new File(fn);

				if (!tmp.canRead() || !tmp.isFile()) {
					System.err.println("path '" + fn +
					                   "' not a (readable) file");
					System.exit(1); // == exit ==
				}
			}
		}

		geneMap = new File(geneMapPath);

		if (!geneMap.isFile() || !geneMap.canRead()) {
			System.err.println("cannot read gene map file '" + geneMapPath +
			                   "'");
			System.exit(1); // == exit ==
		}

		try {
			if (inputDirectory == null)
				annotator = new GeneMentionAnnotator(inputFiles, mimeType,
				    outputDirectory, encoding, replace, namespace, geneMap,
				    dbUrl, dbUser, dbPass);
			else
				annotator = new GeneMentionAnnotator(inputDirectory, mimeType,
				    recursive, outputDirectory, encoding, replace, namespace,
				    geneMap, dbUrl, dbUser, dbPass);

			annotator.run();
		} catch (UIMAException e) {
			l.severe("UIMAException: " + e.getMessage());
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		} catch (IOException e) {
			l.severe("IOException: " + e.getMessage());
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		} catch (ClassNotFoundException e) {
			l.severe("ClassNotFoundException: " + e.getMessage());
			System.err.println(e.getMessage() + " not found");
			System.exit(1); // == exit ==
		}

		System.exit(0);
	}
}
