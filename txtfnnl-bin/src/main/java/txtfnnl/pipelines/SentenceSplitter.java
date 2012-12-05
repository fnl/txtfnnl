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
 * A sentence extractor pipeline for (nearly arbitrary) input files.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to a directory or to STDOUT. Output is always plain-text,
 * where a single line contains (at most) a single sentence.
 * 
 * @author Florian Leitner
 */
public class SentenceSplitter {

	private SentenceSplitter() {
		throw new AssertionError("n/a");
	}

	public static void main(String[] arguments) {
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		Options opts = new Options();

		Pipeline.addLogHelpAndInputOptions(opts);
		Pipeline.addTikaOptions(opts);
		Pipeline.addOutputOptions(opts);

		// sentence splitter options
		opts.addOption("d", "split-double-lines", false, "split sentences on double newlines");
		opts.addOption("s", "split-lines", false, "split sentences on single newlines");

		// output format options setup
		opts.addOption("j", "join-lines", false,
		    "replace single newlines in sentences with spaces");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = Pipeline.loggingSetup(cmd, opts, "txtfnnl ss [options] <directory|files...>\n");

		// sentence splitter
		String splitSentences = null; // d, s

		if (cmd.hasOption('s'))
			splitSentences = "single";
		else if (cmd.hasOption('d'))
			splitSentences = "double";

		// output (format)
		String encoding = Pipeline.outputEncoding(cmd);
		File outputDirectory = Pipeline.outputDirectory(cmd);
		boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
		boolean joinLines = cmd.hasOption('j');

		try {
			Pipeline splitter = new Pipeline(2); // tika and the splitter
			splitter.setReader(cmd);
			splitter.configureTika(cmd);
			splitter.set(1, SentenceAnnotator.configure(splitSentences));
			splitter.setConsumer(SentenceLineWriter.configure(outputDirectory, encoding,
			    outputDirectory == null, overwriteFiles, joinLines));
			splitter.run();
		} catch (UIMAException e) {
			l.severe("UIMAException: " + e.getMessage());
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		} catch (IOException e) {
			l.severe("IOException: " + e.getMessage());
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		System.exit(0);
	}
}
