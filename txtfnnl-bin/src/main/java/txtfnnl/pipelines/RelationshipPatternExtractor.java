/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.uima.util.UimaUtil;

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
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.RelationshipPatternLineWriter;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.uima.resource.LineBasedStringMapResource;
import txtfnnl.uima.resource.RelationshipStringMapResource;
import txtfnnl.utils.IOUtils;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class RelationshipPatternExtractor implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription knownEntityAED;
	AnalysisEngineDescription knownRelationshipAED;
	AnalysisEngineDescription parserAED;
	AnalysisEngineDescription patternWriter;

	static final String DEFAULT_ENITY_NAMESPACE = EntityMentionAnnotator.DEFAULT_NAMESPACE;
	static final String DEFAULT_RELATIONSHIP_NAMESPACE = "rel:";
	static final String DEFAULT_DATABASE = "gnamed";
	static final String DEFAULT_RELATIONSHIP_FILE = "doc2rel.map";
	static final String[] DEFAULT_SQL_QUERIES = EntityMentionAnnotator.DEFAULT_SQL_QUERIES;

	private RelationshipPatternExtractor(File outputDir,
	                                     String characterEncoding,
	                                     boolean overwriteFiles,
	                                     boolean splitLine, String entityNs,
	                                     String relationshipNs,
	                                     File entityMap, File relMap,
	                                     String[] queries, String dbUrl,
	                                     String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		tikaAED = PipelineUtils.getTikaAnnotator(true, characterEncoding);
		sentenceAED = AnalysisEngineFactory.createAnalysisEngineDescription(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, (splitLine
		            ? "single"
		            : "multi"));
		parserAED = AnalysisEngineFactory.createPrimitiveDescription(
		    GeniaTaggerAnnotator.class, UimaUtil.SENTENCE_TYPE_PARAMETER,
		    SentenceAnnotator.SENTENCE_TYPE_NAME);
		patternWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    RelationshipPatternLineWriter.class,
		    UimaUtil.SENTENCE_TYPE_PARAMETER,
		    SentenceAnnotator.SENTENCE_TYPE_NAME,
		    RelationshipPatternLineWriter.PARAM_OUTPUT_DIRECTORY,
		    ((outputDir == null) ? null : outputDir.getCanonicalPath()),
		    RelationshipPatternLineWriter.PARAM_RELATIONSHIP_NAMESPACE,
		    relationshipNs, RelationshipPatternLineWriter.PARAM_ENCODING,
		    characterEncoding,
		    RelationshipPatternLineWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(overwriteFiles));

		knownEntityAED = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    entityNs, KnownEntityAnnotator.PARAM_QUERIES, queries);
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

		knownRelationshipAED = AnalysisEngineFactory
		    .createPrimitiveDescription(KnownRelationshipAnnotator.class,
		        KnownRelationshipAnnotator.PARAM_REMOVE_SENTENCE_ANNOTATIONS,
		        Boolean.TRUE,
		        KnownRelationshipAnnotator.PARAM_ENTITY_NAMESPACE, entityNs,
		        KnownRelationshipAnnotator.PARAM_RELATIONSHIP_NAMESPACE,
		        relationshipNs);
		ExternalResourceFactory.createDependencyAndBind(knownRelationshipAED,
		    KnownRelationshipAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    RelationshipStringMapResource.class,
		    "file:" + relMap.getCanonicalPath());
	}

	public RelationshipPatternExtractor(File inputDirectory, String mimeType,
	                                    boolean recurseDirectory,
	                                    File outputDirectory,
	                                    String characterEncoding,
	                                    boolean replaceFiles,
	                                    boolean splitLine, String entityNs,
	                                    String relNs, File entityMap,
	                                    File relMap, String[] queries,
	                                    String dbUrl, String dbUser,
	                                    String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, splitLine,
		    entityNs, relNs, entityMap, relMap, queries, dbUrl, dbUser, dbPass);
		collectionReader = PipelineUtils.getCollectionReader(inputDirectory,
		    mimeType, recurseDirectory);
	}

	public RelationshipPatternExtractor(String[] inputFiles, String mimeType,
	                                    File outputDirectory,
	                                    String characterEncoding,
	                                    boolean replaceFiles,
	                                    boolean splitLine, String entityNs,
	                                    String relNs, File entityMap,
	                                    File relMap, String[] queries,
	                                    String dbUrl, String dbUser,
	                                    String dbPass) throws IOException,
	        UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, splitLine,
		    entityNs, relNs, entityMap, relMap, queries, dbUrl, dbUser, dbPass);
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
		SimplePipeline.runPipeline(collectionReader, tikaAED, sentenceAED,
		    knownEntityAED, knownRelationshipAED, parserAED, patternWriter);
	}

	/**
	 * Execute a known relationship extractor pipeline.
	 * 
	 * @param arguments command line arguments; see --help for more
	 *        information.
	 */
	public static void main(String[] arguments) {
		CommandLine cmd = null;
		CommandLineParser parser = new PosixParser();
		File relMap;
		File entityMap;
		File inputDirectory = null;
		File outputDirectory = null;
		Pipeline annotator;
		Options opts = new Options();
		String dbUrl;

		opts.addOption("d", "db-name", true, "name of the 'gnamed' DB [" +
		                                     DEFAULT_DATABASE + "]");
		opts.addOption("e", "entity-namespace", true,
		    "namespace of the entity annotations [" + DEFAULT_ENITY_NAMESPACE +
		            "]");
		opts.addOption("l", "line-split", false,
		    "split sentences at single newlines [multi-newlines only]");
		opts.addOption("m", "rel-map", true,
		    "name of the relationship mappings file [" +
		            DEFAULT_RELATIONSHIP_FILE + "]");
		opts.addOption("n", "rel-namespace", true,
		    "namespace of the relationship annotations [" +
		            DEFAULT_RELATIONSHIP_NAMESPACE + "]");
		opts.addOption("o", "output-directory", true,
		    "output directory for writing files [STDOUT]");
		opts.addOption("p", "db-password", true,
		    "password for the DB server (if any is needed)");
		opts.addOption("Q", "query-file", true, "file with SQL SELECT queries");
		OptionBuilder.withLongOpt("query");
		OptionBuilder.withArgName("SELECT");
		OptionBuilder.hasArgs();
		OptionBuilder.withDescription("one or more SQL SELECT queries");
		opts.addOption(OptionBuilder.create('q'));
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
		    RelationshipPatternExtractor.class.getName(), cmd, opts,
		    "txtfnnl gre [options] <output dir> <input dir|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		String queryFileName = cmd.getOptionValue('Q');
		boolean replace = cmd.hasOption('X');
		String dbName = cmd.getOptionValue('d');
		String entityNamespace = cmd.getOptionValue('e');
		boolean splitLine = cmd.hasOption('l');
		String relMapPath = cmd.getOptionValue('m');
		String relNamespace = cmd.getOptionValue('n');
		String outputDirPath = cmd.getOptionValue('o');
		String dbPass = cmd.getOptionValue('p');
		String[] queries = cmd.getOptionValues('q');
		String dbHost = cmd.getOptionValue('s');
		String dbUser = cmd.getOptionValue('u');

		if (inputFiles.length == 0) {
			System.err.println("no input files");
			System.exit(1); // == exit ==
		}

		if (outputDirPath != null) {
			outputDirectory = new File(outputDirPath);

			if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
				System.err.println("cannot write to directory '" +
				                   outputDirectory.getPath() + "'");
				System.exit(1); // == exit ==
			}
		}

		if (entityNamespace == null)
			entityNamespace = DEFAULT_ENITY_NAMESPACE;

		if (relNamespace == null)
			relNamespace = DEFAULT_RELATIONSHIP_NAMESPACE;

		if (relMapPath == null)
			relMapPath = DEFAULT_RELATIONSHIP_FILE;

		if (dbName == null)
			dbName = DEFAULT_DATABASE;

		if (dbHost == null)
			dbHost = "localhost";

		dbUrl = "jdbc:postgresql://" + dbHost + "/" + dbName;

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

		relMap = new File(relMapPath);

		if (!relMap.isFile() || !relMap.canRead()) {
			System.err.println("cannot read relationship map file '" +
			                   relMapPath + "'");
			System.exit(1); // == exit ==
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

		try {
			if (queries == null || queries.length == 0)
				queries = EntityMentionAnnotator.DEFAULT_SQL_QUERIES;

			entityMap = KnownEntityAnnotator.createFromRelationshipMap(relMap,
			    LineBasedStringMapResource.DEFAULT_SEPARATOR);

			if (inputDirectory == null)
				annotator = new RelationshipPatternExtractor(inputFiles,
				    mimeType, outputDirectory, encoding, replace, splitLine,
				    entityNamespace, relNamespace, entityMap, relMap, queries,
				    dbUrl, dbUser, dbPass);
			else
				annotator = new RelationshipPatternExtractor(inputDirectory,
				    mimeType, recursive, outputDirectory, encoding, replace,
				    splitLine, entityNamespace, relNamespace, entityMap,
				    relMap, queries, dbUrl, dbUser, dbPass);

			annotator.run();
		} catch (UIMAException e) {
			l.severe("UIMAException: " + e.getMessage());

			if (l.isLoggable(Level.FINE))
				e.printStackTrace();
			else
				System.err.println(e.getMessage());

			System.exit(1); // == exit ==
		} catch (IOException e) {
			l.severe("IOException: " + e.getMessage());

			if (l.isLoggable(Level.FINE))
				e.printStackTrace();
			else
				System.err.println(e.getMessage());

			System.exit(1); // == exit ==
		} catch (ClassNotFoundException e) {
			l.severe("ClassNotFoundException: " + e.getMessage());

			if (l.isLoggable(Level.FINE))
				e.printStackTrace();
			else
				System.err.println(e.getMessage() + " not found");

			System.exit(1); // == exit ==
		}

		System.exit(0);
	}
}
