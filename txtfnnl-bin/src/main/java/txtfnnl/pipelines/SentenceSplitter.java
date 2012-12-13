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

import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.SentenceLineWriter;

/**
 * A plaintext sentence extractor pipeline for (nearly arbitrary) input files.
 * <p>
 * Input files can be read from a directory or listed explicitly, while output files are written to
 * a directory or to STDOUT. Output is always plain-text, where a single line contains (at most) a
 * single sentence.
 * 
 * @author Florian Leitner
 */
public class SentenceSplitter {
    private SentenceSplitter() {
        throw new AssertionError("n/a");
    }

    public static void main(String[] arguments) {
        final CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;
        final Options opts = new Options();
        Pipeline.addLogHelpAndInputOptions(opts);
        Pipeline.addTikaOptions(opts);
        Pipeline.addOutputOptions(opts);
        // sentence splitter options
        opts.addOption("S", "successive-newlines", false, "split sentences on successive newlines");
        opts.addOption("s", "single-newlines", false, "split sentences on every newline");
        // output format options setup
        opts.addOption("n", "allow-newlines", false,
            "do not replace newline chars within sentences with white-spaces");
        try {
            cmd = parser.parse(opts, arguments);
        } catch (final ParseException e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1); // == EXIT ==
        }
        final Logger l =
            Pipeline.loggingSetup(cmd, opts, "txtfnnl split [options] <directory|files...>\n");
        // sentence splitter
        String splitSentences = null; // S, s
        if (cmd.hasOption('s')) {
            splitSentences = "single";
        } else if (cmd.hasOption('d')) {
            splitSentences = "successive";
        }
        // output (format)
        final String encoding = Pipeline.outputEncoding(cmd);
        final File outputDirectory = Pipeline.outputDirectory(cmd);
        final boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
        final boolean replaceNewlines = (!cmd.hasOption('n'));
        try {
            final Pipeline splitter = new Pipeline(2); // tika and the splitter
            splitter.setReader(cmd);
            splitter.configureTika(cmd);
            splitter.set(1, SentenceAnnotator.configure(splitSentences));
            splitter.setConsumer(SentenceLineWriter.configure(outputDirectory, encoding,
                outputDirectory == null, overwriteFiles, replaceNewlines));
            splitter.run();
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
