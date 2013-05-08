package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;

import txtfnnl.uima.analysis_component.GeneAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.AnnotationLineWriter;
import txtfnnl.uima.collection.OutputWriter;
import txtfnnl.uima.collection.XmiWriter;
import txtfnnl.uima.resource.GnamedGazetteerResource;

/**
 * A pipeline to detect gene and protein names that match names recorded in databases.
 * <p>
 * Input files can be read from a directory or listed explicitly, while output lines are written to
 * some directory or to STDOUT. Output is written as tab-separated values, where each line contains
 * the matched text, the gene ID, the taxon ID, and a confidence value.
 * <p>
 * The default setup assumes gene and/or protein entities found in a <a
 * href="https://github.com/fnl/gnamed">gnamed</a> database.
 * 
 * @author Florian Leitner
 */
public class GeneNormalization extends Pipeline {
  static final String DEFAULT_DATABASE = "gnamed";
  static final String DEFAULT_JDBC_DRIVER = "org.postgresql.Driver";
  static final String DEFAULT_DB_PROVIDER = "postgresql";
  // default: all known gene and protein symbols
  static final String SQL_QUERY = "SELECT gr.accession, g.species_id, ps.value "
      + "FROM gene_refs AS gr, genes AS g, genes2proteins AS g2p, protein_strings AS ps "
      + "WHERE gr.namespace = 'gi' AND gr.id = g.id AND g.id = g2p.gene_id AND g2p.protein_id = ps.id AND ps.cat = 'symbol' "
      + "UNION SELECT gr.accession, g.species_id, gs.value "
      + "FROM gene_refs AS gr, genes AS g, gene_strings AS gs "
      + "WHERE gr.namespace = 'gi' AND gr.id = g.id AND gr.id = gs.id AND gs.cat = 'symbol'";

  private GeneNormalization() {
    throw new AssertionError("n/a");
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    final String geneAnnotationNamespace = "gene";
    CommandLine cmd = null;
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addJdbcResourceOptions(opts, DEFAULT_JDBC_DRIVER, DEFAULT_DB_PROVIDER,
        DEFAULT_DATABASE);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    Pipeline.addSentenceAnnotatorOptions(opts);
    // tokenizer options setup
    opts.addOption("G", "genia", true,
        "use GENIA (with the dir containing 'morphdic/') instead of OpenNLP");
    // query options
    opts.addOption("Q", "query", true, "SQL query that produces gene ID, tax ID, name triplets");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl gn [options] <directory|files...>\n");
    // (GENIA) tokenizer
    final File geniaDir = cmd.getOptionValue('G') == null ? null : new File(
        cmd.getOptionValue('G'));
    // DB query used to fetch the gazetteer's entities
    final String querySql = cmd.hasOption('Q') ? cmd.getOptionValue('Q') : SQL_QUERY;
    // DB gazetteer resource setup
    final String dbUrl = Pipeline.getJdbcUrl(cmd, l, DEFAULT_DB_PROVIDER, DEFAULT_DATABASE);
    GnamedGazetteerResource.Builder gazetteer = null;
    try {
      // create builder
      gazetteer = GnamedGazetteerResource.configure(dbUrl,
          Pipeline.getJdbcDriver(cmd, DEFAULT_JDBC_DRIVER), querySql);
    } catch (final ClassNotFoundException e) {
      System.err.println("JDBC driver class unknown:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    }
    gazetteer.idMatching();
    gazetteer.boundaryMatch();
    Pipeline.configureAuthentication(cmd, gazetteer);
    // output
    OutputWriter.Builder writer;
    if (Pipeline.rawXmi(cmd)) {
      writer = Pipeline.configureWriter(cmd,
          XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd)));
    } else {
      writer = Pipeline.configureWriter(cmd, AnnotationLineWriter.configure())
          .setAnnotatorUri(GeneAnnotator.URI).setAnnotationNamespace(geneAnnotationNamespace)
          .printSurroundings().printPosTag();
    }
    try {
      // 0:tika, 1:splitter, 2:tokenizer, (3:NOOP), 4:gazetteer
      final Pipeline gn = new Pipeline(5);
      gn.setReader(cmd);
      gn.configureTika(cmd);
      gn.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      if (geniaDir == null) {
        gn.set(2, Pipeline.textEngine(TokenAnnotator.configure().create()));
        // TODO: lemmatization might not be needed?
        // gn.set(3, Pipeline.textEngine(BioLemmatizerAnnotator.configure().create()));
        gn.set(3, Pipeline.multiviewEngine(NOOPAnnotator.configure().create()));
      } else {
        GeniaTaggerAnnotator.Builder tagger = GeniaTaggerAnnotator.configure();
        tagger.setDirectory(geniaDir);
        gn.set(2, Pipeline.textEngine(tagger.create()));
        // the GENIA Tagger already lemmatizes; nothing to do here
        gn.set(3, Pipeline.multiviewEngine(NOOPAnnotator.configure().create()));
      }
      GeneAnnotator.Builder geneAnnotator = GeneAnnotator.configure(geneAnnotationNamespace,
          gazetteer.create());
      geneAnnotator.setTextNamespace(SentenceAnnotator.NAMESPACE).setTextIdentifier(
          SentenceAnnotator.IDENTIFIER);
      gn.set(4, Pipeline.textEngine(geneAnnotator.create()));
      gn.setConsumer(Pipeline.textEngine(writer.create()));
      gn.run();
    } catch (final UIMAException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      e.printStackTrace();
      System.exit(1); // == EXIT ==
    } catch (final IOException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    System.exit(0);
  }
}
