package txtfnnl.pipelines;

import org.apache.commons.cli.*;
import org.apache.uima.UIMAException;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.AnnotationLineWriter;
import txtfnnl.uima.collection.OutputWriter;
import txtfnnl.uima.collection.SentenceLineWriter;
import txtfnnl.uima.collection.XmiWriter;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A plaintext sentence extractor pipeline for (nearly arbitrary) input files.
 * <p/>
 * Input files can be read from a directory or listed explicitly, while output files are written to
 * a directory or to STDOUT. Output is always plain-text, where a single line contains (at most) a
 * single sentence.
 *
 * @author Florian Leitner
 */
public
class SentenceSplitter {
  private
  SentenceSplitter() {
    throw new AssertionError("n/a");
  }

  public static
  void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    Pipeline.addSentenceAnnotatorOptions(opts);
    // output format options setup
    opts.addOption(
        "n", "maintain-newlines", false,
        "do not replace newline chars within sentences with white-spaces"
    );
    opts.addOption("a", "all-content", false, "include other (non-sentence) content in the output");
    opts.addOption("O", "offsets", false, "write offsets of annotated sentences");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(
        cmd, opts, "txtfnnl split [options] <directory|files...>\n"
    );
    // output (format)
    OutputWriter.Builder writer;
    if (Pipeline.rawXmi(cmd)) {
      writer = Pipeline.configureWriter(
          cmd, XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd))
      );
    } else if (cmd.hasOption('O')) {
      writer = Pipeline.configureWriter(cmd, AnnotationLineWriter.configure())
                       .setAnnotatorUri(SentenceAnnotator.URI)
                       .setAnnotationNamespace(SentenceAnnotator.NAMESPACE);
    } else {
      SentenceLineWriter.Builder slwb = Pipeline.configureWriter(
          cmd, SentenceLineWriter.configure()
      );
      if (cmd.hasOption('n')) slwb.maintainNewlines();
      if (!cmd.hasOption('a')) slwb.excludeOtherContent();
      writer = slwb;
    }
    try {
      final Pipeline splitter = new Pipeline(2); // tika and the splitter
      splitter.setReader(cmd);
      splitter.configureTika(cmd);
      splitter.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      splitter.setConsumer(Pipeline.textEngine(writer.create()));
      splitter.run();
      splitter.destroy();
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
