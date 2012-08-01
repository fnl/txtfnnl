/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.factory.ExternalResourceFactory;
import org.uimafit.pipeline.SimplePipeline;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.collection.FileCollectionReader;
import txtfnnl.uima.collection.FileSystemCollectionReader;
import txtfnnl.uima.collection.FileSystemXmiWriter;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.utils.IOUtils;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class KnownGeneAnnotator {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription knownEntityAED;
	AnalysisEngineDescription xmiWriter;

	static final String DEFAULT_NAMESPACE = "http://purl.org/bio-entity/";
	static final String DEFAULT_DATABASE = "gnamed";
	static final String DEFAULT_GMAP_FILE = "doc2gene.map";
	static final String[] SQL_QUERIES = new String[] {
	    "SELECT protein_strings.value FROM gene_refs "
	            + "LEFT OUTER JOIN genes2proteins "
	            + "ON gene_refs.id = genes2proteins.gene_id "
	            + "LEFT OUTER JOIN protein_strings "
	            + "ON genes2proteins.protein_id = protein_strings.id "
	            + "WHERE gene_refs.namespace=? "
	            + "AND gene_refs.accession=? "
	            + "AND protein_strings.cat IN ('name', 'symbol')",
	    "SELECT gene_strings.value FROM gene_refs "
	            + "LEFT OUTER JOIN gene_strings USING (id) "
	            + "WHERE gene_refs.namespace=? "
	            + "AND gene_refs.accession=? "
	            + "AND gene_strings.cat IN ('name', 'symbol')" };

	private KnownGeneAnnotator(File outputDir, String characterEncoding,
	                           boolean replaceFiles, String namespace,
	                           File geneMap, String dbUrl, String dbUser,
	                           String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		xmiWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    FileSystemXmiWriter.class, FileSystemXmiWriter.PARAM_ENCODING,
		    characterEncoding, FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY,
		    outputDir.getCanonicalPath(),
		    FileSystemXmiWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(replaceFiles));

		if (characterEncoding == null)
			tikaAED = AnalysisEngineFactory
			    .createAnalysisEngineDescription("txtfnnl.uima.tikaAEDescriptor");
		else
			tikaAED = AnalysisEngineFactory.createAnalysisEngineDescription(
			    "txtfnnl.uima.tikaAEDescriptor", TikaAnnotator.PARAM_ENCODING,
			    characterEncoding);
		knownEntityAED = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    namespace, KnownEntityAnnotator.PARAM_QUERIES, SQL_QUERIES);
		ExternalResourceFactory.createDependencyAndBind(knownEntityAED,
		    KnownEntityAnnotator.MODEL_KEY_ENTITY_STRING_MAP,
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

	public KnownGeneAnnotator(File inputDirectory, String mimeType,
	                          boolean recurseDirectory, File outputDirectory,
	                          String characterEncoding, boolean replaceFiles,
	                          String namespace, File geneMap, String dbUrl,
	                          String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    geneMap, dbUrl, dbUser, dbPass);
		assert inputDirectory.isDirectory() && inputDirectory.canRead() : inputDirectory
		    .getAbsolutePath() + " is not a (readable) directory";
		collectionReader = CollectionReaderFactory.createDescription(
		    FileSystemCollectionReader.class,
		    FileSystemCollectionReader.PARAM_DIRECTORY,
		    inputDirectory.getCanonicalPath(),
		    FileSystemCollectionReader.PARAM_MIME_TYPE, mimeType,
		    FileSystemCollectionReader.PARAM_RECURSIVE,
		    Boolean.valueOf(recurseDirectory));
	}

	public KnownGeneAnnotator(String[] inputFiles, String mimeType,
	                          File outputDirectory, String characterEncoding,
	                          boolean replaceFiles, String namespace,
	                          File geneMap, String dbUrl, String dbUser,
	                          String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    geneMap, dbUrl, dbUser, dbPass);
		collectionReader = CollectionReaderFactory.createDescription(
		    FileCollectionReader.class, FileCollectionReader.PARAM_FILES,
		    inputFiles, FileCollectionReader.PARAM_MIME_TYPE, mimeType);
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
		Logger l = Logger.getLogger(SentenceExtractor.class.getName() +
		                            ".main()");
		Options opts = new Options();
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		File outputDirectory = null;
		File inputDirectory = null;
		KnownGeneAnnotator annotator;
		File geneMap = null;
		String dbUrl = null;
		String enc = Charset.defaultCharset().toString();

		if (IOUtils.isMacOSX())
			enc = "UTF-8";

		opts.addOption("d", "db-name", true, "name of the 'gnamed' DB [" +
		                                     DEFAULT_DATABASE + "]");
		opts.addOption("e", "encoding", true,
		    "set an encoding for output files [" + enc + "]");
		opts.addOption("g", "gene-map", true, "name of the gene map file [" +
		                                      DEFAULT_GMAP_FILE + "]");
		opts.addOption("h", "help", false, "show this help document");
		opts.addOption("i", "info", false, "log INFO-level messages [WARN]");
		opts.addOption("m", "mime-type", true,
		    "define one MIME type for all input files [Tika.detect]");
		opts.addOption("n", "namespace", true,
		    "namespace of the gene annotations [" + DEFAULT_NAMESPACE + "]");
		opts.addOption("p", "db-password", true,
		    "password for the DB server (if any is needed)");
		opts.addOption("q", "quiet", false,
		    "log SEVERE-level messages only [WARN]");
		opts.addOption("r", "recursive", false,
		    "include files in all sub-directories of input directory [false]");
		opts.addOption("s", "db-server", true,
		    "hostname of the DB server (incl. port) [localhost]");
		opts.addOption("u", "db-username", true,
		    "username for the DB server (if any is needed)");
		opts.addOption("v", "verbose", false, "log FINE-level messages [WARN]");
		opts.addOption("x", "replace-files", false,
		    "replace files in the output directory if they exist [false]");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		String[] inputFiles = cmd.getArgs();

		String dbName = cmd.getOptionValue('d');
		String encoding = cmd.getOptionValue('e');
		String geneMapPath = cmd.getOptionValue('g');
		String mimeType = cmd.getOptionValue('m');
		String namespace = cmd.getOptionValue('n');
		String dbPass = cmd.getOptionValue('p');
		boolean recursive = cmd.hasOption('r');
		String dbHost = cmd.getOptionValue('s');
		String dbUser = cmd.getOptionValue('u');
		boolean replace = cmd.hasOption('x');

		if (cmd.hasOption('h') || inputFiles.length == 0) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(
			    "txtfnnl gma [options] <output dir> <input dir|files...>\n",
			    opts);
			System.out
			    .println("\n(c) Florian Leitner 2012. All rights reserved.");
			System.exit(cmd.hasOption('h') ? 0 : 1); // == exit ==
		}

		Logger rootLogger = Logger.getLogger("");

		if (cmd.hasOption('q'))
			rootLogger.setLevel(Level.SEVERE);
		else if (cmd.hasOption('v'))
			rootLogger.setLevel(Level.FINE);
		else if (!cmd.hasOption('i'))
			rootLogger.setLevel(Level.WARNING);

		if (namespace == null)
			namespace = DEFAULT_NAMESPACE;

		if (geneMapPath == null)
			geneMapPath = DEFAULT_GMAP_FILE;

		geneMap = new File(geneMapPath);

		if (!geneMap.isFile() || !geneMap.canRead()) {
			System.err.println("cannot read gene map file '" + geneMapPath +
			                   "'");
			System.exit(1); // == exit ==
		}

		if (dbName == null)
			dbName = DEFAULT_DATABASE;

		if (dbHost == null)
			dbHost = "localhost";

		dbUrl = "jdbc:postgresql://" + dbHost + "/" + dbName;

		if (inputFiles.length > 1) {
			outputDirectory = new File(inputFiles[0]);

			if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
				System.err.println("cannot write to directory '" +
				                   outputDirectory.getPath() + "'");
				System.exit(1); // == exit ==
			}

			inputFiles = Arrays.copyOfRange(inputFiles, 1, inputFiles.length);

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
		} else {
			System.err.println("too few arguments (" + inputFiles.length +
			                   "/2+)");
			System.exit(1); // == exit ==
		}

		try {
			if (inputDirectory == null)
				annotator = new KnownGeneAnnotator(inputFiles, mimeType,
				    outputDirectory, encoding, replace, namespace, geneMap,
				    dbUrl, dbUser, dbPass);
			else
				annotator = new KnownGeneAnnotator(inputDirectory, mimeType,
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
