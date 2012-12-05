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
import txtfnnl.uima.collection.TaggedSentenceLineWriter;

/**
 * A tagger for (nearly arbitrary) input files to annotate sentences, tokens,
 * lemmas, PoS tags, and chunks of a given data as formatted plain text.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output is plain text.
 * 
 * Sentences are separated by newlines. Tokens are annotated with their stems
 * and PoS tags and grouped into phrasal chunks.
 * 
 * @author Florian Leitner
 */
public class SentenceTagger extends Pipeline {

	private SentenceTagger() {
		throw new AssertionError("unused");
	}

	/**
	 * Execute a sentence extraction pipeline.
	 * 
	 * @param arguments command line arguments; see --help for more
	 *        information.
	 */
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

		// tokenizer options setup
		opts.addOption("g", "genia", true, "use GENIA (giving its model dir) instead of OpenNLP");

		// output format options setup
		opts.addOption("j", "join-lines", false,
		    "replace single newlines in sentences with spaces");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = Pipeline
		    .loggingSetup(cmd, opts, "txtfnnl tag [options] <directory|files...>\n");

		// (GENIA) tokenizer
		String geniaDir = cmd.getOptionValue('g');

		// sentence splitter
		String splitSentences = null; // d, s
		if (cmd.hasOption('s'))
			splitSentences = "single";
		else if (cmd.hasOption('d'))
			splitSentences = "double";
		else
			splitSentences = "";

		// output (format)
		String encoding = Pipeline.outputEncoding(cmd);
		File outputDirectory = Pipeline.outputDirectory(cmd);
		boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
		boolean joinLines = cmd.hasOption('j');

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
				// the GENIA Tagger already lemmatizes; nothing to do here
				tagger.set(3, NOOPAnnotator.configure());
			}

			tagger.setConsumer(TaggedSentenceLineWriter.configure(outputDirectory, encoding,
			    outputDirectory == null, overwriteFiles, joinLines));
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
