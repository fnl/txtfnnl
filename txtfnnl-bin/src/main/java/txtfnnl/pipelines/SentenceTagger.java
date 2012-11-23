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

import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.TaggedSentenceLineWriter;

/**
 * A tagger for (nearly arbitrary) input files to annotate sentences, tokens,
 * lemmas, PoS tags, and chunks of a given data as plain text.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output is always plain
 * text.
 * 
 * Sentences are separated by newlines. Tokens are annotated with their lemmas
 * and PoS tags and grouped into phrasal chunks.
 * 
 * @author Florian Leitner
 */
public class SentenceTagger implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription geniaAED;
	AnalysisEngineDescription tokenAED;
	AnalysisEngineDescription partOfSpeechAED;
	AnalysisEngineDescription chunkAED;
	AnalysisEngineDescription lemmaAED;
	AnalysisEngineDescription textWriter;

	/**
	 * Instantiate the {@link txtfnnl.uima.collection.SentenceLineWriter} CAS
	 * consumer.
	 * 
	 * @param outputDir optional output directory to use (otherwise output is
	 *        printed to STDOUT); may be <code>null</code>
	 * @param characterEncoding optional character encoding to use (otherwise
	 *        the platform default is used); may be <code>null</code>
	 * @param elsevier <code>true</code> if the XML files are using Elsevier's
	 *        DTD
	 * @param geniaDir uses the GeniaTagger instead of the openNLP modules,
	 *        running the <code>geniatagger</code> inside the given directory
	 *        path
	 * @param replaceFiles optional flag indicating that existing files in the
	 *        output directory should be replaced
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws IOException
	 * @throws UIMAException
	 */
	private SentenceTagger(File outputDir, String characterEncoding,
	                       boolean elsevier, String geniaDir,
	                       boolean replaceFiles, String splitSentences)
	        throws IOException, UIMAException {
		tikaAED = PipelineUtils.getTikaAnnotator(false, characterEncoding,
		    elsevier);
		sentenceAED = AnalysisEngineFactory.createAnalysisEngineDescription(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, splitSentences == null
		            ? ""
		            : splitSentences);

		if (geniaDir != null) {
			geniaAED = AnalysisEngineFactory.createPrimitiveDescription(
			    GeniaTaggerAnnotator.class,
			    GeniaTaggerAnnotator.PARAM_DICTIONARIES_PATH, geniaDir);
		} else {
			tokenAED = AnalysisEngineFactory
			    .createAnalysisEngineDescription("txtfnnl.uima.openNLPTokenAEDescriptor");
			partOfSpeechAED = AnalysisEngineFactory
			    .createAnalysisEngineDescription("txtfnnl.uima.openNLPPartOfSpeechAEDescriptor");
			chunkAED = AnalysisEngineFactory
			    .createAnalysisEngineDescription("txtfnnl.uima.openNLPChunkAEDescriptor");
			lemmaAED = AnalysisEngineFactory
			    .createAnalysisEngineDescription("txtfnnl.uima.bioLemmatizerAEDescriptor");
		}

		textWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    TaggedSentenceLineWriter.class, UimaUtil.SENTENCE_TYPE_PARAMETER,
		    SentenceAnnotator.SENTENCE_TYPE_NAME,
		    TaggedSentenceLineWriter.PARAM_ENCODING, characterEncoding,
		    TaggedSentenceLineWriter.PARAM_OUTPUT_DIRECTORY,
		    (outputDir == null) ? null : outputDir.getCanonicalPath(),
		    TaggedSentenceLineWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(replaceFiles));

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
	 * @param geniaDir uses the GeniaTagger instead of the openNLP modules,
	 *        running the <code>geniatagger</code> inside the given directory
	 *        path
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceTagger(File inputDirectory, String mimeType,
	                      boolean recurseDirectory, File outputDirectory,
	                      String characterEncoding, boolean elsevier,
	                      String geniaDir, boolean replaceFiles,
	                      String splitSentences) throws IOException,
	        UIMAException {
		this(outputDirectory, characterEncoding, elsevier, geniaDir,
		    replaceFiles, splitSentences);
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
	 * @param geniaDir uses the GeniaTagger instead of the openNLP modules,
	 *        running the <code>geniatagger</code> inside the given directory
	 *        path
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceTagger(String[] inputFiles, String mimeType,
	                      File outputDirectory, String characterEncoding,
	                      boolean elsevier, String geniaDir,
	                      boolean replaceFiles, String splitSentences)
	        throws IOException, UIMAException {
		this(outputDirectory, characterEncoding, elsevier, geniaDir,
		    replaceFiles, splitSentences);
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
		if (geniaAED == null)
			SimplePipeline.runPipeline(collectionReader, tikaAED, sentenceAED,
			    tokenAED, partOfSpeechAED, lemmaAED, chunkAED, textWriter);
		else
			SimplePipeline.runPipeline(collectionReader, tikaAED, sentenceAED,
			    geniaAED, textWriter);
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
		    "output directory for writing files [CWD]");
		opts.addOption("d", "split-double-lines", false,
		    "split sentences on double newlines [false]");
		opts.addOption("g", "genia", true,
		    "use GENIA instead of OpenNLP using model dir path");
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

		Logger l = PipelineUtils.loggingSetup(SentenceTagger.class.getName(),
		    cmd, opts, "txtfnnl tag [options] <directory|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		boolean elsevier = cmd.hasOption('e');
		String geniaDir = cmd.getOptionValue('g');
		boolean replace = cmd.hasOption('X');
		String splitSentences = null;

		if (cmd.hasOption('o'))
			outputDirectory = new File(cmd.getOptionValue('o'));
		else
			outputDirectory = new File(System.getProperty("user.dir"));

		if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
			System.err.println("cannot write to directory '" +
			                   outputDirectory.getPath() + "'");
			System.exit(1); // == exit ==
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
				extractor = new SentenceTagger(inputFiles, mimeType,
				    outputDirectory, encoding, elsevier, geniaDir, replace,
				    splitSentences);
			else
				extractor = new SentenceTagger(inputDirectory, mimeType,
				    recursive, outputDirectory, encoding, elsevier, geniaDir,
				    replace, splitSentences);

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
