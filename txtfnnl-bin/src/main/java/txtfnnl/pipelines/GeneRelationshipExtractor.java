/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.uima.util.UimaUtil;

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
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.RelationshipSentenceLineWriter;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.uima.resource.LineBasedStringMapResource;
import txtfnnl.uima.resource.RelationshipStringMapResource;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class GeneRelationshipExtractor implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription knownEntityAED;
	AnalysisEngineDescription knownRelationshipAED;
	AnalysisEngineDescription sentenceWriter;

	static final String DEFAULT_ENITY_NAMESPACE = GeneMentionAnnotator.DEFAULT_NAMESPACE;
	static final String DEFAULT_RELATIONSHIP_NAMESPACE = "http://purl.org/relationship/";
	static final String DEFAULT_DATABASE = "gnamed";
	static final String DEFAULT_RELATIONSHIP_FILE = "doc2rel.map";
	static final String[] SQL_QUERIES = GeneMentionAnnotator.SQL_QUERIES;

	private GeneRelationshipExtractor(File outputDir,
	                                  String characterEncoding,
	                                  boolean overwriteFiles,
	                                  boolean splitLine, String entityNs,
	                                  String relationshipNs, File geneMap,
	                                  File relMap, String dbUrl,
	                                  String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		tikaAED = PipelineUtils.getTikaAnnotator(true, characterEncoding);
		sentenceAED = AnalysisEngineFactory.createAnalysisEngineDescription(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, (splitLine
		            ? "single"
		            : "multi"));
		sentenceWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    RelationshipSentenceLineWriter.class,
		    UimaUtil.SENTENCE_TYPE_PARAMETER,
		    SentenceAnnotator.SENTENCE_TYPE_NAME,
		    RelationshipSentenceLineWriter.PARAM_OUTPUT_DIRECTORY,
		    ((outputDir == null) ? null : outputDir.getCanonicalPath()),
		    RelationshipSentenceLineWriter.PARAM_RELATIONSHIP_NAMESPACE,
		    relationshipNs, RelationshipSentenceLineWriter.PARAM_ENCODING,
		    characterEncoding,
		    RelationshipSentenceLineWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(overwriteFiles));

		knownEntityAED = AnalysisEngineFactory.createPrimitiveDescription(
		    KnownEntityAnnotator.class, KnownEntityAnnotator.PARAM_NAMESPACE,
		    entityNs, KnownEntityAnnotator.PARAM_QUERIES, SQL_QUERIES);
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

		knownRelationshipAED = AnalysisEngineFactory
		    .createPrimitiveDescription(KnownRelationshipAnnotator.class,
		        KnownRelationshipAnnotator.PARAM_ENTITY_NAMESPACE, entityNs,
		        KnownRelationshipAnnotator.PARAM_RELATIONSHIP_NAMESPACE,
		        relationshipNs);
		ExternalResourceFactory.createDependencyAndBind(knownRelationshipAED,
		    KnownRelationshipAnnotator.MODEL_KEY_EVIDENCE_STRING_MAP,
		    RelationshipStringMapResource.class,
		    "file:" + relMap.getCanonicalPath());
	}

	public GeneRelationshipExtractor(File inputDirectory, String mimeType,
	                                 boolean recurseDirectory,
	                                 File outputDirectory,
	                                 String characterEncoding,
	                                 boolean replaceFiles, boolean splitLine,
	                                 String entityNs, String relNs,
	                                 File geneMap, File relMap, String dbUrl,
	                                 String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, splitLine,
		    entityNs, relNs, geneMap, relMap, dbUrl, dbUser, dbPass);
		collectionReader = PipelineUtils.getCollectionReader(inputDirectory,
		    mimeType, recurseDirectory);
	}

	public GeneRelationshipExtractor(String[] inputFiles, String mimeType,
	                                 File outputDirectory,
	                                 String characterEncoding,
	                                 boolean replaceFiles, boolean splitLine,
	                                 String entityNs, String relNs,
	                                 File geneMap, File relMap, String dbUrl,
	                                 String dbUser, String dbPass)
	        throws IOException, UIMAException, ClassNotFoundException {
		this(outputDirectory, characterEncoding, replaceFiles, splitLine,
		    entityNs, relNs, geneMap, relMap, dbUrl, dbUser, dbPass);
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
		    knownEntityAED, knownRelationshipAED, sentenceWriter);
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
		File relMap;
		File geneMap;
		File inputDirectory = null;
		File outputDirectory = null;
		Pipeline annotator;
		Options opts = new Options();
		String dbUrl;

		opts.addOption("d", "db-name", true, "name of the 'gnamed' DB [" +
		                                     DEFAULT_DATABASE + "]");
		opts.addOption("e", "entity-namespace", true,
		    "namespace of the gene annotations [" + DEFAULT_ENITY_NAMESPACE +
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
		    GeneRelationshipExtractor.class.getName(), cmd, opts,
		    "txtfnnl gre [options] <output dir> <input dir|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		boolean replace = cmd.hasOption('X');
		String dbName = cmd.getOptionValue('d');
		String entityNamespace = cmd.getOptionValue('e');
		boolean splitLine = cmd.hasOption('l');
		String relMapPath = cmd.getOptionValue('m');
		String dbPass = cmd.getOptionValue('p');
		String relNamespace = cmd.getOptionValue('n');
		String outputDirPath = cmd.getOptionValue('o');
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

		try {
			geneMap = KnownEntityAnnotator.createFromRelationshipMap(relMap,
			    LineBasedStringMapResource.DEFAULT_SEPARATOR);

			if (inputDirectory == null)
				annotator = new GeneRelationshipExtractor(inputFiles,
				    mimeType, outputDirectory, encoding, replace, splitLine,
				    entityNamespace, relNamespace, geneMap, relMap, dbUrl,
				    dbUser, dbPass);
			else
				annotator = new GeneRelationshipExtractor(inputDirectory,
				    mimeType, recursive, outputDirectory, encoding, replace,
				    splitLine, entityNamespace, relNamespace, geneMap, relMap,
				    dbUrl, dbUser, dbPass);

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
