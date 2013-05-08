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
    Pipeline.addSentenceAnnotatorOptions(opts);
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
    // (GENIA) tokenizer
    final String geniaDir = cmd.getOptionValue('G');
    // output (format)
    XmiWriter.Builder writer = Pipeline.configureWriter(cmd,
        XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd)));
    try {
      final Pipeline tagger = new Pipeline(4); // tika, splitter, tokenizer, lemmatizer
      tagger.setReader(cmd);
      tagger.configureTika(cmd);
      tagger.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      // GENIA or OpenNLP tokenizer
      if (geniaDir == null) {
        tagger.set(2, Pipeline.textEngine(TokenAnnotator.configure().create()));
        tagger.set(3, Pipeline.textEngine(BioLemmatizerAnnotator.configure().create()));
      } else {
        GeniaTaggerAnnotator.Builder genia = GeniaTaggerAnnotator.configure();
        tagger.set(2, Pipeline.textEngine(genia.setDirectory(new File(geniaDir)).create()));
        // the GENIA Tagger already stems - nothing more to do
        tagger.set(3, Pipeline.multiviewEngine(NOOPAnnotator.configure().create()));
      }
      tagger.setConsumer(Pipeline.textEngine(writer.create()));
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
