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
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.analysis_component.BioLemmatizerAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.SentenceFilter;
import txtfnnl.uima.analysis_component.SyntaxPatternAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.OutputWriter;
import txtfnnl.uima.collection.SemanticAnnotationWriter;
import txtfnnl.uima.collection.SentenceLineWriter;
import txtfnnl.uima.collection.XmiWriter;
import txtfnnl.uima.resource.LineBasedStringArrayResource;

/**
 * A plaintext extractor for (nearly arbitrary) input files to extract sentences or phrases based
 * on pattern matching of its tokens and their lemmas, PoS and chunk tags.
 * <p>
 * Input files can be read from a directory or listed explicitly, while output files are written to
 * some directory or to STDOUT. Sentences are written on a single line. Matching patterns are
 * written on a single line, too, as tab-separated values for the annotated namespace, identifier,
 * and offset, followed by the pattern itself (which may contain tabs, but no newlines).
 * 
 * @author Florian Leitner
 */
public class PatternExtractor extends Pipeline {
  private PatternExtractor() {
    throw new AssertionError("n/a");
  }

  public static void main(String[] arguments) throws ResourceInitializationException {
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    CommandLine cmd = null;
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    Pipeline.addSentenceAnnotatorOptions(opts);
    // sentence filter options
    opts.addOption("f", "filter-sentences", true, "retain sentences using a file of regex matches");
    opts.addOption("F", "filter-remove", false, "filter removes sentences with matches");
    // tokenizer options setup
    opts.addOption("G", "genia", true,
        "use GENIA (with the dir containing 'morphdic/') instead of OpenNLP");
    // semantic pattern tagger options
    opts.addOption("p", "patterns", true, "match sentences with semantic patterns");
    // output options
    opts.addOption("c", "complete-sentences", false,
        "print complete sentences, not just matching phrases");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl grep [options] -p <patterns> <directory|files...>\n");
    // sentence filter
    AnalysisEngineDescription sentenceFilter = null;
    if (cmd.hasOption('f')) {
      ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
          "file:" + new File(cmd.getOptionValue('f')).getAbsolutePath()).create();
      SentenceFilter.Builder b = SentenceFilter.configure(patternResource);
      if (cmd.hasOption('F')) b.removeMatches();
      sentenceFilter = b.create();
    } else {
      sentenceFilter = NOOPAnnotator.configure().create();
    }
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
    // output
    final boolean completeSentence = cmd.hasOption('c');
    OutputWriter.Builder writer;
    if (Pipeline.rawXmi(cmd)) {
      writer = Pipeline.configureWriter(cmd,
          XmiWriter.configure(Pipeline.ensureOutputDirectory(cmd)));
    } else if (completeSentence) {
      writer = Pipeline.configureWriter(cmd, SentenceLineWriter.configure());
    } else {
      writer = Pipeline.configureWriter(cmd, SemanticAnnotationWriter.configure());
    }
    try {
      ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
          "file:" + patterns.getCanonicalPath()).create();
      // 0:tika, 1:splitter, 2:filter, 3:tokenizer, 4:lemmatizer, 5:patternMatcher
      final Pipeline grep = new Pipeline(6);
      grep.setReader(cmd);
      grep.configureTika(cmd);
      grep.set(1, Pipeline.textEngine(Pipeline.getSentenceAnnotator(cmd)));
      grep.set(2, Pipeline.textEngine(sentenceFilter));
      if (geniaDir == null) {
        grep.set(3, Pipeline.textEngine(TokenAnnotator.configure().create()));
        grep.set(4, Pipeline.textEngine(BioLemmatizerAnnotator.configure().create()));
      } else {
        grep.set(
            3,
            Pipeline.textEngine(GeniaTaggerAnnotator.configure().setDirectory(new File(geniaDir))
                .create()));
        // the GENIA Tagger already lemmatizes; nothing to do
        grep.set(4, Pipeline.multiviewEngine(NOOPAnnotator.configure().create()));
      }
      SyntaxPatternAnnotator.Builder spab = SyntaxPatternAnnotator.configure(patternResource);
      if (completeSentence) spab.removeUnmatched();
      grep.set(5, Pipeline.textEngine(spab.create()));
      grep.setConsumer(Pipeline.textEngine(writer.create()));
      grep.run();
      grep.destroy();
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
