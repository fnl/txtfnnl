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
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.pipeline.SimplePipeline;

import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.FileSystemXmiWriter;

/**
 * A pipeline for (nearly arbitrary) input files to annotate sentences,
 * tokens, lemmas, PoS tags, and chunks of a given data as UIMA XMI files.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output is always UIMA
 * XMI.
 * 
 * @author Florian Leitner
 */
public class Preprocessor implements Pipeline {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription tokenAED;
	AnalysisEngineDescription partOfSpeechAED;
	AnalysisEngineDescription chunkAED;
	AnalysisEngineDescription lemmaAED;
	AnalysisEngineDescription xmiWriter;

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
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws IOException
	 * @throws UIMAException
	 */
	private Preprocessor(File outputDir, String characterEncoding,
	                     boolean elsevier, boolean replaceFiles,
	                     String splitSentences) throws IOException,
	        UIMAException {
		tikaAED = PipelineUtils.getTikaAnnotator(false, characterEncoding,
		    elsevier);
		sentenceAED = AnalysisEngineFactory.createAnalysisEngineDescription(
		    "txtfnnl.uima.openNLPSentenceAEDescriptor",
		    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, splitSentences == null
		            ? ""
		            : splitSentences);
		tokenAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.openNLPTokenAEDescriptor");
		partOfSpeechAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.openNLPPartOfSpeechAEDescriptor");
		chunkAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.openNLPChunkAEDescriptor");
		lemmaAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.bioLemmatizerAEDescriptor");

		if (characterEncoding == null)
			xmiWriter = AnalysisEngineFactory.createAnalysisEngineDescription(
			    "txtfnnl.uima.fileSystemXmiWriterDescriptor",
			    FileSystemXmiWriter.PARAM_FORMAT_XMI, true,
			    FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY,
			    outputDir.getCanonicalPath(),
			    FileSystemXmiWriter.PARAM_OVERWRITE_FILES, replaceFiles,
			    FileSystemXmiWriter.PARAM_USE_XML_11, true);
		else
			xmiWriter = AnalysisEngineFactory.createAnalysisEngineDescription(
			    "txtfnnl.uima.fileSystemXmiWriterDescriptor",
			    FileSystemXmiWriter.PARAM_ENCODING, characterEncoding,
			    FileSystemXmiWriter.PARAM_FORMAT_XMI, true,
			    FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY,
			    outputDir.getCanonicalPath(),
			    FileSystemXmiWriter.PARAM_OVERWRITE_FILES, replaceFiles,
			    FileSystemXmiWriter.PARAM_USE_XML_11, true);
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
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public Preprocessor(File inputDirectory, String mimeType,
	                    boolean recurseDirectory, File outputDirectory,
	                    String characterEncoding, boolean elsevier,
	                    boolean replaceFiles, String splitSentences)
	        throws IOException, UIMAException {
		this(outputDirectory, characterEncoding, elsevier, replaceFiles,
		    splitSentences);
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
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default, <code>null</code>), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public Preprocessor(String[] inputFiles, String mimeType,
	                    File outputDirectory, String characterEncoding,
	                    boolean elsevier, boolean replaceFiles,
	                    String splitSentences) throws IOException,
	        UIMAException {
		this(outputDirectory, characterEncoding, elsevier, replaceFiles,
		    splitSentences);
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
		    tokenAED, partOfSpeechAED, lemmaAED, chunkAED, xmiWriter);
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

		Logger l = PipelineUtils.loggingSetup(Preprocessor.class.getName(),
		    cmd, opts, "txtfnnl pp [options] <directory|files...>\n");

		String[] inputFiles = cmd.getArgs();
		boolean recursive = cmd.hasOption('R');
		String encoding = cmd.getOptionValue('E');
		String mimeType = cmd.getOptionValue('M');
		boolean elsevier = cmd.hasOption('e');
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
				extractor = new Preprocessor(inputFiles, mimeType,
				    outputDirectory, encoding, elsevier, replace,
				    splitSentences);
			else
				extractor = new Preprocessor(inputDirectory, mimeType,
				    recursive, outputDirectory, encoding, elsevier, replace,
				    splitSentences);

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
