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
import txtfnnl.uima.analysis_component.GeneAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.AnnotationLineWriter;
import txtfnnl.uima.collection.OutputWriter;
import txtfnnl.uima.collection.TaggedSentenceLineWriter;
import txtfnnl.uima.collection.XmiWriter;

/**
 * A plaintext tagger for (nearly arbitrary) input files to annotate entities.
 *
 * @author Florian Leitner
 */
public class EntityTagger extends Pipeline {
  private EntityTagger() {
    throw new AssertionError("n/a");
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    CommandLine cmd = null;
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    Pipeline.addSentenceAnnotatorOptions(opts);
    // tokenizer options setup
    opts.addOption("G", "genia", true,
                   "use GENIA (with the dir containing 'morphdic/') instead of OpenNLP");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
                                           "txtfnnl tag [options] <directory|files...>\n");
    // (GENIA) tokenizer
    final String geniaDir = cmd.getOptionValue('G');
    // output (format)
    OutputWriter.Builder writer;
    if (Pipeline.rawXmi(cmd)) {
      writer = Pipeline.configureWriter(cmd,
                                        XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd)));
    } else {
      // TODO: namespace used by alternate, non-GENIA entity tagger
      String namespace = cmd.hasOption('G') ? GeniaTaggerAnnotator.ENTITY_NAMESPACE : null;
      writer = Pipeline.configureWriter(cmd, AnnotationLineWriter.configure())
                       .setAnnotatorUri(GeniaTaggerAnnotator.URI)
                       .setAnnotationNamespace(namespace);
    }
    try {
      final Pipeline tagger = new Pipeline(4); // tika, splitter, tokenizer, entity tagger
      tagger.setReader(cmd);
      tagger.configureTika(cmd);
      tagger.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      if (geniaDir == null) {
        tagger.set(2, Pipeline.textEngine(TokenAnnotator.configure().create()));
        // TODO: initialize an alternate, non-GENIA entity tagger
        throw new RuntimeException("TODO: add another entity tagger apart from GENIA");
      } else {
        GeniaTaggerAnnotator.Builder genia = GeniaTaggerAnnotator.configure();
        tagger.set(2, Pipeline.textEngine(genia.setDirectory(new File(geniaDir)).create()));
        // the GENIA Tagger already tags entities - nothing more to do
        tagger.set(3, Pipeline.multiviewEngine(NOOPAnnotator.configure().create()));
      }
      tagger.setConsumer(Pipeline.textEngine(writer.create()));
      tagger.run();
      tagger.destroy();
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
