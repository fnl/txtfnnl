/**
 * 
 */
package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.collection.FileCollectionReader;
import txtfnnl.uima.collection.FileSystemCollectionReader;
import txtfnnl.uima.collection.FileSystemXmiWriter;
import txtfnnl.utils.IOUtils;

/**
 * 
 * 
 * @author Florian Leitner
 */
final class PipelineUtils {

	private PipelineUtils() {
		throw new AssertionError("non-instantiatable class");
	}

	static void addLogAndHelpOptions(Options opts) {
		opts.addOption("h", "help", false, "show this help document");
		opts.addOption("i", "info", false, "log INFO-level messages [WARN]");
		opts.addOption("q", "quiet", false,
		    "log SEVERE-level messages only [WARN]");
		opts.addOption("v", "verbose", false, "log FINE-level messages [WARN]");
		opts.addOption("R", "recursive", false,
		    "include files in all sub-directories of input directory [false]");
		opts.addOption("E", "encoding", true,
		    "(force the) use (of an) encoding [" +
		            (IOUtils.isMacOSX() ? "UTF-8" : Charset.defaultCharset()) +
		            "]");
		opts.addOption("M", "mime-type", true,
		    "define one MIME type for all input files [Tika.detect]");
		opts.addOption("X", "replace-files", false,
		    "replace files in the output directory if they exist [false]");
	}

	static Logger loggingSetup(String mainClassName, CommandLine cmd,
	                           Options opts, String usage) {
		if (cmd.hasOption('h')) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(usage, opts);
			System.out
			    .println("\n(c) Florian Leitner 2012. All rights reserved.");
			System.exit(cmd.hasOption('h') ? 0 : 1); // == exit ==
		}

		try {
			if (System.getProperty("java.util.logging.config.file") == null)
				LogManager.getLogManager().readConfiguration(
				    Thread.currentThread().getClass()
				        .getResourceAsStream("/logging.properties"));
		} catch (SecurityException ex) {
			System.err.println("SecurityException while configuring logging");
			System.err.println(ex.getMessage());
		} catch (IOException ex) {
			System.err.println("IOException while configuring logging");
			System.err.println(ex.getMessage());
		}

		Logger l = Logger.getLogger(mainClassName + ".main()");
		Logger rootLogger = Logger.getLogger("");

		if (cmd.hasOption('q'))
			rootLogger.setLevel(Level.SEVERE);
		else if (cmd.hasOption('v'))
			rootLogger.setLevel(Level.FINE);
		else if (!cmd.hasOption('i'))
			rootLogger.setLevel(Level.WARNING);

		l.log(Level.FINE, "logging setup complete");
		return l;
	}

	static AnalysisEngineDescription getTikaAnnotator() throws UIMAException,
	        IOException {
		return PipelineUtils.getTikaAnnotator(false);
	}

	static AnalysisEngineDescription getTikaAnnotator(boolean normalizeGreek)
	        throws UIMAException, IOException {
		return PipelineUtils.getTikaAnnotator(normalizeGreek, null);
	}

	static AnalysisEngineDescription getTikaAnnotator(boolean normalizeGreek,
	                                                  String encoding)
	        throws UIMAException, IOException {
		if (encoding == null)
			return AnalysisEngineFactory.createAnalysisEngineDescription(
			    "txtfnnl.uima.simpleTikaAEDescriptor",
			    TikaAnnotator.PARAM_NORMALIZE_GREEK_CHARACTERS,
			    Boolean.valueOf(normalizeGreek));
		else
			return AnalysisEngineFactory.createAnalysisEngineDescription(
			    "txtfnnl.uima.simpleTikaAEDescriptor",
			    TikaAnnotator.PARAM_ENCODING, encoding,
			    TikaAnnotator.PARAM_NORMALIZE_GREEK_CHARACTERS,
			    Boolean.valueOf(normalizeGreek));
	}

	static CollectionReaderDescription
	        getCollectionReader(File inputDirectory)
	                throws ResourceInitializationException, IOException {
		return getCollectionReader(inputDirectory, null);
	}

	static CollectionReaderDescription
	        getCollectionReader(File inputDirectory, String mimeType)
	                throws ResourceInitializationException, IOException {
		return getCollectionReader(inputDirectory, mimeType, false);
	}

	static CollectionReaderDescription
	        getCollectionReader(File inputDirectory, String mimeType,
	                            boolean recursive)
	                throws ResourceInitializationException, IOException {
		assert inputDirectory.isDirectory() && inputDirectory.canRead() : inputDirectory
		    .getAbsolutePath() + " is not a (readable) directory";

		return CollectionReaderFactory.createDescription(
		    FileSystemCollectionReader.class,
		    FileSystemCollectionReader.PARAM_DIRECTORY,
		    inputDirectory.getCanonicalPath(),
		    FileSystemCollectionReader.PARAM_MIME_TYPE, mimeType,
		    FileSystemCollectionReader.PARAM_RECURSIVE,
		    Boolean.valueOf(recursive));
	}

	static CollectionReaderDescription
	        getCollectionReader(String[] inputFiles)
	                throws ResourceInitializationException, IOException {
		return getCollectionReader(inputFiles, null);
	}

	static CollectionReaderDescription
	        getCollectionReader(String[] inputFiles, String mimeType)
	                throws ResourceInitializationException, IOException {
		return CollectionReaderFactory.createDescription(
		    FileCollectionReader.class, FileCollectionReader.PARAM_FILES,
		    inputFiles, FileCollectionReader.PARAM_MIME_TYPE, mimeType);
	}

	static AnalysisEngineDescription getXmiFileWriter(File outputDirectory)
	        throws ResourceInitializationException, IOException {
		return getXmiFileWriter(outputDirectory, null);
	}

	static AnalysisEngineDescription getXmiFileWriter(File outputDirectory,
	                                                  String encoding)
	        throws ResourceInitializationException, IOException {
		return getXmiFileWriter(outputDirectory, encoding, false);
	}

	static AnalysisEngineDescription getXmiFileWriter(File outputDirectory,
	                                                  String encoding,
	                                                  boolean replaceFiles)
	        throws ResourceInitializationException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(
		    FileSystemXmiWriter.class, FileSystemXmiWriter.PARAM_ENCODING,
		    encoding, FileSystemXmiWriter.PARAM_OUTPUT_DIRECTORY,
		    outputDirectory.getCanonicalPath(),
		    FileSystemXmiWriter.PARAM_OVERWRITE_FILES,
		    Boolean.valueOf(replaceFiles));
	}
}
