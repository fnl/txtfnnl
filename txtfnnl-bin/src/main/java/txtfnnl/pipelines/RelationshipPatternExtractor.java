/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.resource.ExternalResourceDescription;

import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.LinkGrammarAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.RelationshipPatternLineWriter;
import txtfnnl.utils.IOUtils;

/**
 * Extract known relationships between known entities as plain text patterns.
 * 
 * The entities and relationships are "known", because for each file, the IDs
 * of the contained entities and the set of relationships they have must be
 * provided. These IDs are used to fetch their actual names from a DB
 * resource, and finally, a regular expression is used to detect the presence
 * of those names in the text. Potential relationship patterns are then
 * extracted by looking for sentences where all entities in a known
 * relationship are co-mentioned within that sentence.
 * 
 * @author Florian Leitner
 */
public class RelationshipPatternExtractor extends Pipeline {

	static final String DEFAULT_NAMESPACE = "rel:";
	static final String DEFAULT_MAPPING_FILE = "doc2rel.map";

	private RelationshipPatternExtractor() {
		throw new AssertionError("n/a");
	}

	public static void main(String[] arguments) {
		CommandLine cmd = null;
		CommandLineParser parser = new PosixParser();
		Options opts = new Options();

		Pipeline.addLogHelpAndInputOptions(opts);
		Pipeline.addTikaOptions(opts);
		Pipeline.addOutputOptions(opts);
		Pipeline.addJdbcResourceOptions(opts, EntityMentionAnnotator.DEFAULT_JDBC_DRIVER,
		    EntityMentionAnnotator.DEFAULT_DB_PROVIDER, EntityMentionAnnotator.DEFAULT_DATABASE);

		// sentence splitter options
		opts.addOption("d", "split-double-lines", false, "split sentences on double newlines");
		opts.addOption("s", "split-lines", false, "split sentences on single newlines");

		// entity annotator options setup
		opts.addOption("Q", "query-file", true, "file with SQL SELECT queries");
		OptionBuilder.withLongOpt("query");
		OptionBuilder.withArgName("SELECT");
		OptionBuilder.hasArgs();
		OptionBuilder.withDescription("one or more SQL SELECT queries");
		opts.addOption(OptionBuilder.create('q'));
		opts.addOption("m", "entity-map", true, "name of the entity map file [" +
		                                        EntityMentionAnnotator.DEFAULT_MAPPING_FILE + "]");
		opts.addOption("n", "entity-namespace", true, "namespace of the entity annotations [" +
		                                              EntityMentionAnnotator.DEFAULT_NAMESPACE +
		                                              "]");

		// relationship annotator options setup
		opts.addOption("M", "rel-map", true, "name of the relationship mappings file [" +
		                                     DEFAULT_MAPPING_FILE + "]");
		opts.addOption("N", "rel-namespace", true, "namespace of the relationship annotations [" +
		                                           DEFAULT_NAMESPACE + "]");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = Pipeline
		    .loggingSetup(cmd, opts, "txtfnnl pre [options] <directory|files...>\n");

		// output options
		String encoding = Pipeline.outputEncoding(cmd);
		File outputDirectory = Pipeline.outputDirectory(cmd);
		boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);

		// sentence splitter
		String splitSentences = null; // d, s
		if (cmd.hasOption('s'))
			splitSentences = "single";
		else if (cmd.hasOption('d'))
			splitSentences = "double";
		else
			splitSentences = "";

		// DB resource
		ExternalResourceDescription jdbcResource = null;

		try {
			jdbcResource = Pipeline.getJdbcResource(cmd, l,
			    EntityMentionAnnotator.DEFAULT_JDBC_DRIVER,
			    EntityMentionAnnotator.DEFAULT_DB_PROVIDER,
			    EntityMentionAnnotator.DEFAULT_DATABASE);
		} catch (IOException e) {
			System.err.println("JDBC resoruce setup failed:\n" + e.getMessage());
			System.exit(1); // == exit ==
		} catch (ClassNotFoundException e) {
			System.err.println("JDBC resoruce setup failed:\n" + e.getMessage());
			System.exit(1); // == exit ==
		}

		/* BEGIN entity annotator */
		String queryFileName = cmd.getOptionValue('Q');
		String entityMapPath = cmd
		    .getOptionValue('m', EntityMentionAnnotator.DEFAULT_MAPPING_FILE);
		String namespace = cmd.getOptionValue('n', EntityMentionAnnotator.DEFAULT_NAMESPACE);
		String[] queries = cmd.getOptionValues('q');
		File entityMap; // m

		if (queryFileName != null) {
			File queryFile = new File(queryFileName);

			if (!queryFile.isFile() || !queryFile.canRead()) {
				System.err.println("query file '" + queryFile + "' not a (readable) file");
				System.exit(1); // == exit ==
			}

			String[] fileQueries = null;

			try {
				fileQueries = IOUtils.read(new FileInputStream(queryFile), encoding).split("\n");
			} catch (Exception e) {
				System.err.println("could not read query file '" + queryFile + "': " +
				                   e.getMessage());
				System.exit(1); // == exit ==
			}

			if (queries == null || queries.length == 0) {
				queries = fileQueries;
			} else {
				String[] tmp = new String[queries.length + fileQueries.length];
				System.arraycopy(queries, 0, tmp, 0, queries.length);
				System.arraycopy(fileQueries, 0, tmp, queries.length, fileQueries.length);
				queries = tmp;
			}
		}

		entityMap = new File(entityMapPath);

		if (!entityMap.isFile() || !entityMap.canRead()) {
			System.err.println("cannot read entity map file '" + entityMapPath + "'");
			System.exit(1); // == exit ==
		}

		if (queries == null || queries.length == 0)
			queries = EntityMentionAnnotator.DEFAULT_SQL_QUERIES;
		/* END entity annotator */

		// relationship annotator
		String relMapPath = cmd.getOptionValue('M', DEFAULT_MAPPING_FILE);
		String relNamespace = cmd.getOptionValue('N', DEFAULT_NAMESPACE);
		File relMap = new File(relMapPath);

		if (!relMap.isFile() || !relMap.canRead()) {
			System.err.println("cannot read relationship map file '" + relMapPath + "'");
			System.exit(1); // == exit ==
		}

		try {
			Pipeline pipeline = new Pipeline(5);
			pipeline.setReader(cmd);
			pipeline.configureTika(cmd);
			pipeline.set(1, SentenceAnnotator.configure(splitSentences));
			pipeline.set(2,
			    KnownEntityAnnotator.configure(namespace, queries, entityMap, jdbcResource));
			pipeline.set(3,
			    KnownRelationshipAnnotator.configure(namespace, relNamespace, relMap, true));
			pipeline.set(4, LinkGrammarAnnotator.configure());
			pipeline.setConsumer(RelationshipPatternLineWriter.configure(relNamespace,
			    outputDirectory, encoding, outputDirectory == null, overwriteFiles, 1000));
			pipeline.run();
		} catch (UIMAException e) {
			l.severe("UIMAException: " + e.getMessage());

			if (l.isLoggable(Level.FINE))
				e.printStackTrace();
			else
				System.err.println(e.getMessage());

			System.exit(1); // == exit ==
		} catch (IOException e) {
			l.severe("IOException: " + e.getMessage());
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		System.exit(0);
	}
}
