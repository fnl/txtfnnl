package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import opennlp.uima.util.UimaUtil;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.pipeline.SimplePipeline;

import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.opennlp.SentenceLineWriter;

/**
 * A sentence extractor pipeline for (nearly arbitrary) input files.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output is always
 * plain-text, where a single line contains at most a single sentence.
 * 
 * @author Florian Leitner
 */
public class SentenceSplitter implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription sentenceLineWriter;

	/**
	 * Instantiate the
	 * {@link txtfnnl.uima.collection.opennlp.SentenceLineWriter} CAS
	 * consumer.
	 * 
	 * @param outputDir optional output directory to use (otherwise output is
	 *        printed to STDOUT); may be <code>null</code>
	 * @param characterEncoding optional character encoding to use (otherwise
	 *        the platform default is used); may be <code>null</code>
	 * @param elsevier <code>true</code> if the XML files are using Elsevier's
	 *        DTD
	 * @param replaceFiles optional flag indicating that existing files in the
	 *        output directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws IOException
	 * @throws UIMAException
	 */
	private SentenceSplitter(File outputDir, String characterEncoding,
	                         boolean elsevier, boolean replaceFiles,
	                         boolean joinLines, String splitSentences)
	        throws IOException, UIMAException {
		tikaAED = PipelineUtils.getTikaAnnotator(false, characterEncoding,
		    elsevier);

		sentenceLineWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    SentenceLineWriter.class, UimaUtil.SENTENCE_TYPE_PARAMETER,
		    SentenceAnnotator.SENTENCE_TYPE_NAME,
		    SentenceLineWriter.PARAM_ENCODING, characterEncoding,
		    SentenceLineWriter.PARAM_OUTPUT_DIRECTORY, (outputDir == null)
		            ? null
		            : outputDir.getCanonicalPath(),
		    SentenceLineWriter.PARAM_OVERWRITE_FILES, Boolean
		        .valueOf(replaceFiles), SentenceLineWriter.PARAM_JOIN_LINES,
		    Boolean.valueOf(joinLines));
		sentenceAED = AnalysisEngineFactory.createAnalysisEngineDescription(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, splitSentences == null ? "" : splitSentences);
	}

	/**
	 * Instantiate the SentenceExtractor pipeline components.
	 * 
	 * @param inputDirectory from which all files should be read in as SOFAs
	 * @param mimeType to assume for all input files - may be
	 *        <code>null</code>, in which case the types are auto-detected
	 * @param recurseDirectory using input files in all sub-directories of the
	 *        input directory
	 * @param outputDirectory to use (otherwise output is printed to STDOUT);
	 *        optional - may be <code>null</code>
	 * @param characterEncoding to use (otherwise the platform default is
	 *        used); optional - may be <code>null</code>
	 * @param elsevier <code>true</code> if the XML files are using Elsevier's
	 *        DTD
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceSplitter(File inputDirectory, String mimeType,
	                        boolean recurseDirectory, File outputDirectory,
	                        String characterEncoding, boolean elsevier,
	                        boolean replaceFiles, boolean joinLines,
	                        String splitSentences) throws IOException,
	        UIMAException {
		this(outputDirectory, characterEncoding, elsevier, replaceFiles,
		    joinLines, splitSentences);
		assert inputDirectory.isDirectory() && inputDirectory.canRead() : inputDirectory
		    .getAbsolutePath() + " is not a (readable) directory";
		collectionReader = PipelineUtils.getCollectionReader(inputDirectory,
		    mimeType, recurseDirectory);
	}

	/**
	 * Instantiate the SentenceExtractor pipeline components.
	 * 
	 * @param inputFiles array to read in as SOFAs
	 * @param mimeType to assume for all input files - may be
	 *        <code>null</code>, in which case the types are auto-detected
	 * @param outputDirectory to use (otherwise output is printed to STDOUT);
	 *        optional - may be <code>null</code>
	 * @param characterEncoding to use (otherwise the platform default is
	 *        used); optional - may be <code>null</code>
	 * @param elsevier <code>true</code> if the XML files are using Elsevier's
	 *        DTD
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceSplitter(String[] inputFiles, String mimeType,
	                        File outputDirectory, String characterEncoding,
	                        boolean elsevier, boolean replaceFiles,
	                        boolean joinLines, String splitSentences)
	        throws IOException, UIMAException {
		this(outputDirectory, characterEncoding, elsevier, replaceFiles,
		    joinLines, splitSentences);
		collectionReader = PipelineUtils.getCollectionReader(inputFiles,
		    mimeType);
	}

	/**
	 * Run the configured pipeline.
	 * 
	 * @throws UIMAException
	 * @throws IOException
	 */
	public void run() throws UIMAException, IOException {
		SimplePipeline.runPipeline(collectionReader, tikaAED, sentenceAED,
		    sentenceLineWriter);
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
		File outputDirectory = null;
		File inputDirectory = null;
		Options opts = new Options();
		Pipeline extractor;

		opts.addOption("o", "output-directory", true,
		    "output directory for writing files [STDOUT]");
		opts.addOption("j", "join-lines", false,
		    "replace single newlines in sentences with spaces [false]");
		opts.addOption("d", "split-double-lines", false,
		    "split sentences on double newlines [false]");
		opts.addOption("s", "split-lines", false,
		    "split sentences on single newlines [false]");
		opts.addOption("e", "elsevier", false,
		    "assume XML files follow Elsevier's DTD [false]");

		PipelineUtils.addLogAndHelpOptions(opts);

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		Logger l = PipelineUtils.loggingSetup(
		    SentenceSplitter.class.getName(), cmd, opts,
		    "txtfnnl ss [options] <directory|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		boolean elsevier = cmd.hasOption('e');
		boolean replace = cmd.hasOption('X');
		boolean joinLines = cmd.hasOption('j');
		String splitSentences = null;

		if (cmd.hasOption('o')) {
			outputDirectory = new File(cmd.getOptionValue('o'));

			if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
				System.err.println("cannot write to directory '" +
				                   outputDirectory.getPath() + "'");
				System.exit(1); // == exit ==
			}
		}

		if (cmd.hasOption('s')) {
			splitSentences = "single";
		} else if (cmd.hasOption('d')) {
			splitSentences = "double";
		}

		if (inputFiles.length > 0) {
			if (inputFiles.length == 1) {
				inputDirectory = new File(inputFiles[0]);

				if (inputDirectory.isFile() && inputDirectory.canRead())
					inputDirectory = null;
			} else {
				for (String fn : inputFiles) {
					File tmp = new File(fn);

					if (!tmp.canRead() || !tmp.isFile()) {
						System.err.println("path '" + fn +
						                   "' not a (readable) file");
						System.exit(1); // == exit ==
					}
				}
			}
		}

		try {
			if (inputDirectory == null)
				extractor = new SentenceSplitter(inputFiles, mimeType,
				    outputDirectory, encoding, elsevier, replace, joinLines,
				    splitSentences);
			else
				extractor = new SentenceSplitter(inputDirectory, mimeType,
				    recursive, outputDirectory, encoding, elsevier, replace,
				    joinLines, splitSentences);

			extractor.run();
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
