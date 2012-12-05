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
 * A pipeline for (nearly arbitrary) input files to annotate sentences,
 * tokens, lemmas, PoS tags, and chunks in UIMA XMI format.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output are always UIMA
 * XMI files written to a directory.
 * 
 * @author Florian Leitner
 */
public class Preprocessor extends Pipeline {

	private Preprocessor() {
		throw new AssertionError("unused");
	}

	public static void main(String[] arguments) {
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		Options opts = new Options();

		Pipeline.addLogHelpAndInputOptions(opts);
		Pipeline.addTikaOptions(opts);
		Pipeline.addOutputOptions(opts);

		// fix option output-directory: always write files, never use STDOUT
		opts.addOption("o", "output-directory", true, "output directory for writing files [CWD]");

		// sentence splitter options
		opts.addOption("d", "split-double-lines", false, "split sentences on double newlines");
		opts.addOption("s", "split-lines", false, "split sentences on single newlines");

		// tokenizer options setup
		opts.addOption("g", "genia", true, "use GENIA (giving its model dir) instead of OpenNLP");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = Pipeline.loggingSetup(cmd, opts, "txtfnnl pp [options] <directory|files...>\n");

		// sentence splitter
		String splitSentences = null; // d, s
		if (cmd.hasOption('s'))
			splitSentences = "single";
		else if (cmd.hasOption('d'))
			splitSentences = "double";
		else
			splitSentences = "";

		// (GENIA) tokenizer
		String geniaDir = cmd.getOptionValue('g');

		// output (format)
		String encoding = Pipeline.outputEncoding(cmd);
		File outputDirectory = Pipeline.outputDirectory(cmd);
		boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
		if (outputDirectory == null)
			outputDirectory = new File(System.getProperty("user.dir"));


		try {
			Pipeline tagger = new Pipeline(4); // tika, splitter, tokenizer,
			                                   // lemmatizer
			tagger.setReader(cmd);
			tagger.configureTika(cmd);
			tagger.set(1, SentenceAnnotator.configure(splitSentences));

			if (geniaDir == null) {
				tagger.set(2, TokenAnnotator.configure());
				tagger.set(3, BioLemmatizerAnnotator.configure());
			} else {
				tagger.set(2, GeniaTaggerAnnotator.configure(geniaDir));
				// the GENIA Tagger already stems; nothing to do here
				tagger.set(3, NOOPAnnotator.configure());
			}

			tagger.setConsumer(XmiWriter.configure(outputDirectory, encoding, overwriteFiles));
			tagger.run();

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
