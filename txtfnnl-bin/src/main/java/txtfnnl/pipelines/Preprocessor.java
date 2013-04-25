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

import txtfnnl.uima.analysis_component.BioLemmatizerAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.XmiWriter;

/**
 * A pipeline for (nearly arbitrary) input files to annotate sentences, tokens, lemmas, PoS tags,
 * and chunks in <a href ="http://uima.apache.org/d/uimaj-2.4.0/references.html#ugr.ref.xmi">UIMA
 * XMI format</a>.
 * <p>
 * Input files can be read (recursively) from a directory or listed explicitly, while output files
 * are written to another directory or to the current [working] directory (CWD). Output files are
 * written using the <a href
 * ="http://uima.apache.org/d/uimaj-2.4.0/references.html#ugr.ref.xmi">UIMA XMI format</a>.
 * 
 * @author Florian Leitner
 */
public class Preprocessor extends Pipeline {
  private Preprocessor() {
    throw new AssertionError("n/a");
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    // fix option output-directory: always write files, never use STDOUT
    opts.addOption("o", "output-directory", true, "output directory for writing files [CWD]");
    // sentence splitter options
    opts.addOption("S", "split-anywhere", false, "do not use newlines for splitting");
    opts.addOption("s", "single-newlines", false, "split sentences on single newlines");
    // tokenizer options setup
    opts.addOption("G", "genia", true, "use GENIA (giving its model dir) instead of OpenNLP");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == exit ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl pre [options] <directory|files...>\n");
    // sentence splitter
    String splitSentences = "successive"; // S, s
    if (cmd.hasOption('s')) {
      splitSentences = "single";
    } else if (cmd.hasOption('S')) {
      splitSentences = null;
    }
    // (GENIA) tokenizer
    final String geniaDir = cmd.getOptionValue('G');
    // output (format)
    final String encoding = Pipeline.outputEncoding(cmd);
    File outputDirectory = Pipeline.outputDirectory(cmd);
    final boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
    if (outputDirectory == null) {
      outputDirectory = new File(System.getProperty("user.dir"));
    }
    try {
      final Pipeline tagger = new Pipeline(4); // tika, splitter, tokenizer, lemmatizer
      tagger.setReader(cmd);
      tagger.configureTika(cmd);
      tagger.set(1, SentenceAnnotator.configure(splitSentences));
      // GENIA or OpenNLP tokenizer
      if (geniaDir == null) {
        tagger.set(2, TokenAnnotator.configure());
        tagger.set(3, BioLemmatizerAnnotator.configure());
      } else {
        tagger.set(2, GeniaTaggerAnnotator.configure().setDirectory(new File(geniaDir)).create());
        // the GENIA Tagger already stems - nothing more to do
        tagger.set(3, NOOPAnnotator.configure());
      }
      tagger.setConsumer(XmiWriter.configure(outputDirectory, encoding, overwriteFiles, false,
          true));
      tagger.run();
    } catch (final UIMAException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    } catch (final IOException e) {
      l.severe(e.toString());
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    System.exit(0); // == EXIT (normally) ==
  }
}
