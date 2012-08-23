/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
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
import txtfnnl.utils.IOUtils;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class EntityMentionAnnotator implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription knownEntityAED;
	AnalysisEngineDescription xmiWriter;

	static final String DEFAULT_NAMESPACE = "entity:";
	static final String DEFAULT_DATABASE = "gnamed";
	static final String DEFAULT_GMAP_FILE = "doc2entity.map";
	static final String[] DEFAULT_SQL_QUERIES = new String[] {
	    "SELECT DISTINCT p.value FROM gene_refs AS g "
	            + "JOIN genes2proteins AS g2p ON g.id = g2p.gene_id "
	            + "JOIN protein_strings AS p ON g2p.protein_id = p.id "
	            + "WHERE p.cat IN ('name', 'symbol') "
	            + "AND g.namespace=? AND g.accession=?",
	    "SELECT s.value FROM gene_refs AS r "
	            + "JOIN gene_strings AS s ON r.id = s.id "
	            + "WHERE s.cat IN ('name', 'symbol') "
	            + "AND r.namespace=? AND r.accession=?" };

	private EntityMentionAnnotator(File outputDir, String characterEncoding,
	                               boolean replaceFiles, String namespace,
	                               File entityMap, String[] queries,
	                               String dbUrl, String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		tikaAED = PipelineUtils.getTikaAnnotator(true, characterEncoding);
		xmiWriter = PipelineUtils.getXmiFileWriter(outputDir,
		    characterEncoding, replaceFiles);

		knownEntityAED = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    namespace, KnownEntityAnnotator.PARAM_QUERIES, queries);
		ExternalResourceFactory.createDependencyAndBind(knownEntityAED,
		    KnownEntityAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    EntityStringMapResource.class,
		    "file:" + entityMap.getCanonicalPath());
		Class.forName("org.postgresql.Driver");
		ExternalResourceFactory.createDependencyAndBind(knownEntityAED,
		    KnownEntityAnnotator.MODEL_KEY_JDBC_CONNECTION,
		    JdbcConnectionResourceImpl.class, dbUrl,
		    JdbcConnectionResourceImpl.PARAM_DRIVER_CLASS,
		    "org.postgresql.Driver",
		    JdbcConnectionResourceImpl.PARAM_USERNAME, dbUser,
		    JdbcConnectionResourceImpl.PARAM_PASSWORD, dbPass);
	}

	public EntityMentionAnnotator(File inputDirectory, String mimeType,
	                              boolean recurseDirectory,
	                              File outputDirectory,
	                              String characterEncoding,
	                              boolean replaceFiles, String namespace,
	                              File entityMap, String[] queries,
	                              String dbUrl, String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    entityMap, queries, dbUrl, dbUser, dbPass);
		collectionReader = PipelineUtils.getCollectionReader(inputDirectory,
		    mimeType, recurseDirectory);
	}

	public EntityMentionAnnotator(String[] inputFiles, String mimeType,
	                              File outputDirectory,
	                              String characterEncoding,
	                              boolean replaceFiles, String namespace,
	                              File entityMap, String[] queries,
	                              String dbUrl, String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, namespace,
		    entityMap, queries, dbUrl, dbUser, dbPass);
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
	 * Execute a known entity annotator pipeline.
	 * 
	 * @param arguments command line arguments; see --help for more
	 *        information.
	 */
	public static void main(String[] arguments) {
		CommandLine cmd = null;
		CommandLineParser parser = new PosixParser();
		File entityMap;
		File inputDirectory = null;
		File outputDirectory;
		Pipeline annotator;
		Options opts = new Options();
		String dbUrl;

		opts.addOption("d", "db-name", true, "name of the 'gnamed' DB [" +
		                                     DEFAULT_DATABASE + "]");
		opts.addOption("m", "entity-map", true, "name of the entity map file [" +
		                                      DEFAULT_GMAP_FILE + "]");
		opts.addOption("n", "namespace", true,
		    "namespace of the entity annotations [" + DEFAULT_NAMESPACE + "]");
		opts.addOption("Q", "query-file", true, "file with SQL SELECT queries");
		OptionBuilder.withLongOpt("query");
		OptionBuilder.withArgName("SELECT");
		OptionBuilder.hasArgs();
		OptionBuilder.withDescription("one or more SQL SELECT queries");
		opts.addOption(OptionBuilder.create('q'));
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
		    EntityMentionAnnotator.class.getName(), cmd, opts,
		    "txtfnnl gma [options] <output dir> <input dir|files...>\n");

		String[] inputFiles = cmd.getArgs();
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		String queryFileName = cmd.getOptionValue('Q');
		boolean recursive = cmd.hasOption('R');
		boolean replace = cmd.hasOption('X');
		String dbName = cmd.getOptionValue('d');
		String entityMapPath = cmd.getOptionValue('m');
		String namespace = cmd.getOptionValue('n');
		String dbPass = cmd.getOptionValue('p');
		String[] queries = cmd.getOptionValues('q');
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

		if (entityMapPath == null)
			entityMapPath = DEFAULT_GMAP_FILE;

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

		if (queryFileName != null) {
			File queryFile = new File(queryFileName);

			if (!queryFile.isFile() || !queryFile.canRead()) {
				System.err.println("query file '" + queryFile +
				                   "' not a (readable) file");
				System.exit(1); // == exit ==
			}

			String[] fileQueries = null;

			try {
				fileQueries = IOUtils.read(new FileInputStream(queryFile),
				    encoding).split("\n");
			} catch (Exception e) {
				System.err.println("could not read query file '" + queryFile +
				                   "': " + e.getMessage());
				System.exit(1); // == exit ==
			}

			if (queries == null || queries.length == 0) {
				queries = fileQueries;
			} else {
				String[] tmp = new String[queries.length + fileQueries.length];
				System.arraycopy(queries, 0, tmp, 0, queries.length);
				System.arraycopy(fileQueries, 0, tmp, queries.length,
				    fileQueries.length);
				queries = tmp;
			}
		}

		entityMap = new File(entityMapPath);

		if (!entityMap.isFile() || !entityMap.canRead()) {
			System.err.println("cannot read entity map file '" + entityMapPath +
			                   "'");
			System.exit(1); // == exit ==
		}

		try {
			if (queries == null || queries.length == 0)
				queries = EntityMentionAnnotator.DEFAULT_SQL_QUERIES;

			if (inputDirectory == null)
				annotator = new EntityMentionAnnotator(inputFiles, mimeType,
				    outputDirectory, encoding, replace, namespace, entityMap,
				    queries, dbUrl, dbUser, dbPass);
			else
				annotator = new EntityMentionAnnotator(inputDirectory,
				    mimeType, recursive, outputDirectory, encoding, replace,
				    namespace, entityMap, queries, dbUrl, dbUser, dbPass);

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
