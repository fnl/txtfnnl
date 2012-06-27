package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.SimplePipeline;

import txtfnnl.opennlp.uima.sentdetect.SentenceLineWriter;
import txtfnnl.uima.collection.FileCollectionReader;
import txtfnnl.uima.collection.FileSystemCollectionReader;

/**
 * A sentence extractor pipeline for (nearly arbitrary) input files.
 * 
 * Input files can be read from a directory or listed explicitly, while output
 * files are written to another directory or to STDOUT. Output is always
 * plain-text, where a single line contains at most a single sentence.
 * 
 * @author Florian Leitner
 */
public class SentenceExtractor {

	CollectionReaderDescription collectionReader;
	AnalysisEngineDescription tikaAED;
	AnalysisEngineDescription sentenceAED;
	AnalysisEngineDescription sentenceLineWriter;

	/**
	 * Instantiate the
	 * {@link txtfnnl.opennlp.uima.sentdetect.SentenceLineWriter} CAS
	 * consumer.
	 * 
	 * @param outputDirectory optional output directory to use (otherwise
	 *        output is printed to STDOUT); may be <code>null</code>
	 * @param characterEncoding optional character encoding to use (otherwise
	 *        the platform default is used); may be <code>null</code>
	 * @param replaceFiles optional flag indicating that existing files in the
	 *        output directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @throws IOException
	 * @throws UIMAException
	 */
	private SentenceExtractor(File outputDirectory, String characterEncoding,
	                          boolean replaceFiles, boolean joinLines)
	        throws IOException, UIMAException {
		sentenceLineWriter = AnalysisEngineFactory.createPrimitiveDescription(
		    SentenceLineWriter.class,
		    SentenceLineWriter.PARAM_ENCODING,
		    characterEncoding,
		    SentenceLineWriter.PARAM_OUTPUT_DIRECTORY,
		    (outputDirectory == null) ? null : outputDirectory
		        .getCanonicalPath(), SentenceLineWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(replaceFiles),
		    SentenceLineWriter.PARAM_JOIN_LINES, Boolean.valueOf(joinLines));
		tikaAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.tikaAEDescriptor");
		sentenceAED = AnalysisEngineFactory
		    .createAnalysisEngineDescription("txtfnnl.uima.openNLPSentenceAEDescriptor");
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
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceExtractor(File inputDirectory, String mimeType,
	                         boolean recurseDirectory, File outputDirectory,
	                         String characterEncoding, boolean replaceFiles,
	                         boolean joinLines) throws IOException,
	        UIMAException {
		this(outputDirectory, characterEncoding, replaceFiles, joinLines);
		assert inputDirectory.isDirectory() && inputDirectory.canRead() : inputDirectory
		    .getAbsolutePath() + " is not a (readable) directory";
		collectionReader = CollectionReaderFactory.createDescription(
		    FileSystemCollectionReader.class,
		    FileSystemCollectionReader.PARAM_DIRECTORY,
		    inputDirectory.getCanonicalPath(),
		    FileSystemCollectionReader.PARAM_MIME_TYPE, mimeType,
		    FileSystemCollectionReader.PARAM_RECURSIVE,
		    Boolean.valueOf(recurseDirectory));
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
	 * @param replaceFiles flag indicating that existing files in the output
	 *        directory should be replaced
	 * @param joinLines flag indicating that newline and carriage return
	 *        characters within sentences should be replaced with spaces
	 * @throws ResourceInitializationException
	 * @throws IOException
	 */
	public SentenceExtractor(String[] inputFiles, String mimeType,
	                         File outputDirectory, String characterEncoding,
	                         boolean replaceFiles, boolean joinLines)
	        throws IOException, UIMAException {
		this(outputDirectory, characterEncoding, replaceFiles, joinLines);
		collectionReader = CollectionReaderFactory.createDescription(
		    FileCollectionReader.class, FileCollectionReader.PARAM_FILES,
		    inputFiles, FileCollectionReader.PARAM_MIME_TYPE, mimeType);
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
	 * @param args command line arguments; see --help for more information.
	 */
	public static void main(String[] arguments) {
		Logger l = Logger.getLogger(SentenceExtractor.class.getName() +
		                            ".main()");
		Options opts = new Options();
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		File outputDirectory = null;
		File inputDirectory = null;
		SentenceExtractor extractor;

		opts.addOption("h", "help", false, "show this help document");
		opts.addOption("o", "output-directory", true,
		    "output directory for writing files [STDOUT]");
		opts.addOption("e", "encoding", true,
		    "set an encoding for output files [" + Charset.defaultCharset() +
		            "]");
		opts.addOption("m", "mime-type", true,
		    "define one MIME type for all input files [Tika.detect]");
		opts.addOption("r", "recursive", false,
		    "include files in all sub-directories of input directory [false]");
		opts.addOption("x", "replace-files", false,
		    "replace files in the output directory if they exist [false]");
		opts.addOption("j", "join-lines", false,
		    "replace newline chars within sentences with spaces [false]");
		opts.addOption("i", "info", false, "log INFO-level messages [false]");
		opts.addOption("s", "silent", false,
		    "log SEVERE-level messages only [false]");

		try {
			cmd = parser.parse(opts, arguments);
		} catch (ParseException e) {
			// e.printStackTrace();
			System.err.println(e.getMessage());
			System.exit(1); // == exit ==
		}

		String[] inputFiles = cmd.getArgs();
		boolean replace = cmd.hasOption('x');
		boolean recursive = cmd.hasOption('r');
		boolean joinLines = cmd.hasOption('j');
		String mimeType = cmd.getOptionValue('m');
		String encoding = cmd.getOptionValue('e');

		if (cmd.hasOption('h') || inputFiles.length == 0) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("txtfnnl ss [options] <directory|files...>\n",
			    opts);
			System.out
			    .println("\n(c) Florian Leitner 2012. All rights reserved.");
			System.exit(cmd.hasOption('h') ? 0 : 1); // == exit ==
		}

		if (!cmd.hasOption('i'))
			Logger.getLogger("").setLevel(Level.WARNING);

		if (cmd.hasOption('s'))
			Logger.getLogger("").setLevel(Level.SEVERE);

		if (cmd.hasOption('o')) {
			outputDirectory = new File(cmd.getOptionValue('o'));

			if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
				System.err.println("cannot write to directory '" +
				                   outputDirectory.getPath() + "'");
				System.exit(1); // == exit ==
			}
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
				extractor = new SentenceExtractor(inputFiles, mimeType,
				    outputDirectory, encoding, replace, joinLines);
			else
				extractor = new SentenceExtractor(inputDirectory, mimeType,
				    recursive, outputDirectory, encoding, replace, joinLines);

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
