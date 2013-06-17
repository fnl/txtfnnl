package txtfnnl.pipelines;

import org.apache.commons.cli.*;
import org.apache.uima.UIMAException;
import txtfnnl.uima.analysis_component.BioLemmatizerAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Extract text from files using the txtfnnl Tika wrappers.
 * 
 * @author Florian Leitner
 */
public class PlaintextExtractor extends Pipeline {
  private
  PlaintextExtractor() {
    throw new AssertionError("n/a");
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(
        cmd, opts, "txtfnnl extract [options] <directory|files...>\n"
    );
    // output (format)
    OutputWriter.Builder writer;
    if (Pipeline.rawXmi(cmd)) {
      writer = Pipeline.configureWriter(cmd,
                                        XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd)));
    } else {
      writer = Pipeline.configureWriter(cmd, TextWriter.configure());
    }
    try {
      final Pipeline splitter = new Pipeline(1); // tika only
      splitter.setReader(cmd);
      splitter.configureTika(cmd);
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
