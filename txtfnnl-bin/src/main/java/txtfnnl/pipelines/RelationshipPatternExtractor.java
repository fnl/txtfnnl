package txtfnnl.pipelines;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.analysis_component.KnownEntityAnnotator;
import txtfnnl.uima.analysis_component.KnownRelationshipAnnotator;
import txtfnnl.uima.analysis_component.LinkGrammarAnnotator;
import txtfnnl.utils.IOUtils;

/**
 * Extract known relationships between known entities as plain text patterns.
 * <p>
 * The entities and relationships are "known", because for each file, the IDs of the contained
 * entities and the set of relationships they have must be provided. These IDs are used to fetch
 * their actual names from a DB resource, and finally, a regular expression is used to detect the
 * presence of those names in the text. Potential relationship patterns are then extracted by
 * looking for sentences where all entities in a known relationship are co-mentioned within that
 * sentence.
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
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    Pipeline.addJdbcResourceOptions(opts, EntityMentionAnnotator.DEFAULT_JDBC_DRIVER,
        EntityMentionAnnotator.DEFAULT_DB_PROVIDER, EntityMentionAnnotator.DEFAULT_DATABASE);
    // sentence splitter options
    Pipeline.addSentenceAnnotatorOptions(opts);
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
        EntityMentionAnnotator.DEFAULT_NAMESPACE + "]");
    // relationship annotator options setup
    opts.addOption("M", "rel-map", true, "name of the relationship mappings file [" +
        DEFAULT_MAPPING_FILE + "]");
    opts.addOption("N", "rel-namespace", true, "namespace of the relationship annotations [" +
        DEFAULT_NAMESPACE + "]");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl patterns [options] <directory|files...>\n");
    // output options
    // DB resource
    ExternalResourceDescription jdbcResource = null;
    try {
      jdbcResource = Pipeline.getJdbcConnectionResource(cmd, l, EntityMentionAnnotator.DEFAULT_JDBC_DRIVER,
          EntityMentionAnnotator.DEFAULT_DB_PROVIDER, EntityMentionAnnotator.DEFAULT_DATABASE);
    } catch (final ClassNotFoundException e) {
      System.err.println("JDBC resoruce setup failed:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    } catch (ResourceInitializationException e) {
      System.err.println("JDBC resoruce setup failed:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    }
    /* BEGIN entity annotator */
    final String queryFileName = cmd.getOptionValue('Q');
    final String entityMapPath = cmd.getOptionValue('m',
        EntityMentionAnnotator.DEFAULT_MAPPING_FILE);
    final String namespace = cmd.getOptionValue('n', EntityMentionAnnotator.DEFAULT_NAMESPACE);
    String[] queries = cmd.getOptionValues('q');
    File entityMap; // m
    if (queryFileName != null) {
      final File queryFile = new File(queryFileName);
      if (!queryFile.isFile() || !queryFile.canRead()) {
        System.err.print("cannot read query file ");
        System.err.println(queryFile);
        System.exit(1); // == EXIT ==
      }
      String[] fileQueries = null;
      try {
        fileQueries = IOUtils.read(new FileInputStream(queryFile), Pipeline.inputEncoding(cmd)).split("\n");
      } catch (final Exception e) {
        System.err.print("cannot read query file ");
        System.err.print(queryFile);
        System.err.print(":");
        System.err.println(e.getLocalizedMessage());
        System.exit(1); // == EXIT ==
      }
      if (queries == null || queries.length == 0) {
        queries = fileQueries;
      } else {
        final String[] tmp = new String[queries.length + fileQueries.length];
        System.arraycopy(queries, 0, tmp, 0, queries.length);
        System.arraycopy(fileQueries, 0, tmp, queries.length, fileQueries.length);
        queries = tmp;
      }
    }
    entityMap = new File(entityMapPath);
    if (!entityMap.isFile() || !entityMap.canRead()) {
      System.err.print("cannot read entity map file ");
      System.err.println(entityMapPath);
      System.exit(1); // == EXIT ==
    }
    if (queries == null || queries.length == 0) {
      queries = EntityMentionAnnotator.DEFAULT_SQL_QUERIES;
    }
    /* END entity annotator */
    // relationship annotator
    final String relMapPath = cmd.getOptionValue('M', DEFAULT_MAPPING_FILE);
    final String relNamespace = cmd.getOptionValue('N', DEFAULT_NAMESPACE);
    final File relMap = new File(relMapPath);
    if (!relMap.isFile() || !relMap.canRead()) {
      System.err.print("cannot read relationship map file ");
      System.err.println(relMapPath);
      System.exit(1); // == EXIT ==
    }
    try {
      final Pipeline pipeline = new Pipeline(5);
      pipeline.setReader(cmd);
      pipeline.configureTika(cmd);
      pipeline.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      KnownEntityAnnotator.Builder keab = KnownEntityAnnotator.configure(namespace, queries,
          entityMap, jdbcResource);
      pipeline.set(2, Pipeline.multiviewEngine(keab.create()));
      pipeline.set(
          3,
          Pipeline.multiviewEngine(KnownRelationshipAnnotator
              .configure(namespace, relNamespace, relMap).removeSentenceAnnotations().create()));
      pipeline.set(4, Pipeline.textEngine(LinkGrammarAnnotator.configure().create()));
      /* TODO: broken (will this pipeline be used, anyways???)
      RelationshipPatternLineWriter.Builder writer = RelationshipPatternLineWriter.configureWriter(cmd,
          RelationshipPatternLineWriter.configure());
      pipeline
          .setConsumer(Pipeline.multiviewEngine(RelationshipPatternLineWriter.configure(
              relNamespace, outputDirectory, encoding, outputDirectory == null, overwriteFiles,
              1000)));
              */
      pipeline.run();
    } catch (final UIMAException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    } catch (final IOException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    System.exit(0);
  }
}
