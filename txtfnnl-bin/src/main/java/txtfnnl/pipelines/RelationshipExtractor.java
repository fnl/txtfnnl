package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.analysis_component.BioLemmatizerAnnotator;
import txtfnnl.uima.analysis_component.GazetteerAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.RelationshipFilterAnnotator;
import txtfnnl.uima.analysis_component.SentenceFilterAnnotator;
import txtfnnl.uima.analysis_component.SyntaxPatternAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.RelationshipWriter;
import txtfnnl.uima.resource.JdbcGazetteerResource;

/**
 * A pattern-based relationship extractor between normalized entities.
 * <p>
 * Input files can be read from a directory or listed explicitly, while output lines are written to
 * some directory or to STDOUT. Relationships are written on a single line. A relationship consists
 * of the document URL, the relationship type, the actor entity ID, the target entity ID, and the
 * sentence evidence where the relationship was found, all separated by tabs.
 * <p>
 * The default setup assumes gene and/or protein entities found in a <a
 * href="https://github.com/fnl/gnamed">gnamed</a> database.
 * 
 * @author Florian Leitner
 */
public class RelationshipExtractor extends Pipeline {
  static final String DEFAULT_DATABASE = "gnamed";
  static final String DEFAULT_JDBC_DRIVER = "org.postgresql.Driver";
  static final String DEFAULT_DB_PROVIDER = "postgresql";
  // default entity set: all human and mouse gene symbols (roughly 300k symbols)
  static final String DEFAULT_SQL_QUERY = "SELECT g.id, s.value FROM "
      + "genes AS g, gene_strings AS s WHERE "
      + "g.id = s.id AND g.species_id IN (9606, 10090) AND s.cat = 'symbol'";

  private RelationshipExtractor() {
    throw new AssertionError("n/a");
  }

  public static ExternalResourceDescription getGazetteerResource(CommandLine cmd, Logger l,
      String querySql, boolean idMatching, boolean exactCaseMatching, String defaultDriverClass,
      String defaultProvider, String defaultDbName) throws ResourceInitializationException,
      ClassNotFoundException {
    // driver class name
    final String driverClass = cmd.getOptionValue('D', defaultDriverClass);
    Class.forName(driverClass);
    // db url
    final String dbHost = cmd.getOptionValue('H', "localhost");
    final String dbProvider = cmd.getOptionValue('P', defaultProvider);
    final String dbName = cmd.getOptionValue('d', defaultDbName);
    final String dbUrl = String.format("jdbc:%s://%s/%s", dbProvider, dbHost, dbName);
    l.log(Level.INFO, "JDBC URL: {0}", dbUrl);
    // create builder
    JdbcGazetteerResource.Builder b = JdbcGazetteerResource
        .configure(dbUrl, driverClass, querySql).idMatching();
    // set username/password options
    if (cmd.hasOption('u')) b.setUsername(cmd.getOptionValue('u'));
    if (cmd.hasOption('p')) b.setPassword(cmd.getOptionValue('p'));
    return b.create();
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    CommandLine cmd = null;
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addJdbcResourceOptions(opts, DEFAULT_JDBC_DRIVER, DEFAULT_DB_PROVIDER,
        DEFAULT_DATABASE);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    opts.addOption("S", "successive-newlines", false, "split sentences on successive newlines");
    opts.addOption("s", "single-newlines", false, "split sentences on single newlines");
    // sentence filter options
    opts.addOption("f", "filter-sentences", true, "retain sentences using a file of regex matches");
    opts.addOption("F", "filter-remove", false, "filter removes sentences with matches");
    // tokenizer options setup
    opts.addOption("G", "genia", true,
        "use GENIA (with the dir containing 'morphdic/') instead of OpenNLP");
    // semantic patterns - REQUIRED!
    opts.addOption("p", "patterns", true, "match sentences with semantic patterns");
    // actor and target ID, name pairs
    opts.addOption("a", "actor-sql", true, "SQL query that produces actor ID, name pairs");
    opts.addOption("t", "target-sql", true, "SQL query that produces target ID, name pairs");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl rex [options] -p <patterns> <directory|files...>\n");
    // sentence splitter
    String splitSentences = null; // S, s
    if (cmd.hasOption('s')) {
      splitSentences = "single";
    } else if (cmd.hasOption('S')) {
      splitSentences = "successive";
    }
    // sentence filter
    final File sentenceFilterPatterns = cmd.hasOption('f') ? new File(cmd.getOptionValue('f'))
        : null;
    final boolean removingSentenceFilter = cmd.hasOption('F');
    // (GENIA) tokenizer
    final String geniaDir = cmd.getOptionValue('G');
    // semantic patterns
    File patterns = null;
    try {
      patterns = new File(cmd.getOptionValue('p'));
    } catch (NullPointerException e) {
      l.severe("no patterns file");
      System.err.println("patterns file missing");
      System.exit(1); // == EXIT ==
    }
    // entity queries
    final String actorSQL = cmd.hasOption('a') ? cmd.getOptionValue('a') : DEFAULT_SQL_QUERY;
    final String targetSQL = cmd.hasOption('t') ? cmd.getOptionValue('t') : DEFAULT_SQL_QUERY;
    // DB resource
    ExternalResourceDescription actorGazetteer = null;
    ExternalResourceDescription targetGazetteer = null;
    try {
      actorGazetteer = getGazetteerResource(cmd, l, actorSQL, true, false, DEFAULT_JDBC_DRIVER,
          DEFAULT_DB_PROVIDER, DEFAULT_DATABASE);
      targetGazetteer = getGazetteerResource(cmd, l, targetSQL, true, false, DEFAULT_JDBC_DRIVER,
          DEFAULT_DB_PROVIDER, DEFAULT_DATABASE);
    } catch (final ResourceInitializationException e) {
      System.err.println("JDBC resoruce setup failed:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    } catch (final ClassNotFoundException e) {
      System.err.println("JDBC driver class unknown:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    }
    // output (format)
    final String encoding = Pipeline.outputEncoding(cmd);
    final File outputDirectory = Pipeline.outputDirectory(cmd);
    final boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
    try {
      // 0:tika, 1:splitter, 2:filter, 3:tokenizer, 4:lemmatizer,
      // 5:patternMatcher, 6:actor gazetteer, 7:target gazetteer, 8:filter
      final Pipeline rex = new Pipeline(9);
      rex.setReader(cmd);
      rex.configureTika(cmd);
      rex.set(1, SentenceAnnotator.configure(splitSentences));
      if (sentenceFilterPatterns == null) rex.set(2, NOOPAnnotator.configure());
      else rex.set(2,
          SentenceFilterAnnotator.configure(sentenceFilterPatterns, removingSentenceFilter));
      if (geniaDir == null) {
        rex.set(3, TokenAnnotator.configure());
        rex.set(4, BioLemmatizerAnnotator.configure());
      } else {
        rex.set(3, GeniaTaggerAnnotator.configure(new File(geniaDir)));
        // the GENIA Tagger already lemmatizes; nothing to do
        rex.set(4, NOOPAnnotator.configure());
      }
      rex.set(5, SyntaxPatternAnnotator.configure(patterns, "\t", true, null, null));
      rex.set(6, GazetteerAnnotator.configure("actor", actorGazetteer).setTextNamespace("entity")
          .setTextIdentifier("actor").create());
      rex.set(7, GazetteerAnnotator.configure("target", targetGazetteer)
          .setTextNamespace("entity").setTextIdentifier("target").create());
      rex.set(8, RelationshipFilterAnnotator.configure(SyntaxPatternAnnotator.URI, "relation",
          "binary", SyntaxPatternAnnotator.URI, "entity", null, GazetteerAnnotator.URI, null,
          null, false));
      rex.setConsumer(RelationshipWriter.configure(outputDirectory, encoding,
          outputDirectory == null, overwriteFiles, true, null, true, true));
      rex.run();
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
