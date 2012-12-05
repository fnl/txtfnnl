package txtfnnl.uima.collection;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * A CAS consumer that writes special markup, adding line separators after
 * {@link SentenceAnnotation}s.
 * 
 * The tokens ({@link TokenAnnotation}) in each sentence are followed by their
 * PoS tag and stem, separated by underscores from the token itself. Chunks
 * are grouped using curly braces followed by the tag.
 * 
 * For example, the sentence "The dog barks repeatedly." would be represented
 * as:
 * 
 * <code>{ NP The_DT_the dog_NN_dog } { VP barks_VB_bark } { ADJP repeatedly_ADJ_repeat } ._._.</code>
 * 
 * Due to this nature, sentences are always joined across newlines and the
 * corresponding parameter in the {@link SentenceLineWriter} is not relevant
 * to this AE.
 * 
 * <b>Note</b> that on <b>Apple OSX</b> the default encoding would be
 * MacRoman; however, this consumer uses either the encoding defined by the
 * <b>LANG</b> environment variable or otherwise defaults to <b>UTF-8</b> as a
 * far more sensible encoding on OSX instead.
 * 
 * @author Florian Leitner
 */
public final class TaggedSentenceLineWriter extends SentenceLineWriter {

	/**
	 * Configure an AE descriptor for a pipeline.
	 * 
	 * @param outputDirectory path to the output directory (or null)
	 * @param encoding encoding to use for writing (or null)
	 * @param printToStdout whether to print to STDOUT or not
	 * @param overwriteFiles whether to overwrite existing files or not
	 * @throws IOException
	 * @throws UIMAException
	 */
	@SuppressWarnings("serial")
	public static AnalysisEngineDescription configure(final File outputDirectory,
	                                                  final String encoding,
	                                                  final boolean printToStdout,
	                                                  final boolean overwriteFiles)
	        throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(TaggedSentenceLineWriter.class,
		    UIMAUtils.makeParameterArray(new HashMap<String, Object>() {

			    {
				    put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
				    put(PARAM_ENCODING, encoding);
				    put(PARAM_PRINT_TO_STDOUT, printToStdout);
				    put(PARAM_OVERWRITE_FILES, overwriteFiles);
			    }
		    }));
	}

	/**
	 * Configure an AE descriptor for a pipeline that does not overwrite
	 * files.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding,
	                                                  boolean printToStdout) throws UIMAException,
	        IOException {
		return configure(outputDirectory, encoding, printToStdout, false);
	}

	/**
	 * Configure an AE descriptor for a pipeline that does not print to
	 * STDOUT.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, String encoding)
	        throws UIMAException, IOException {
		return configure(outputDirectory, encoding, false);
	}

	/**
	 * Configure an AE descriptor for a pipeline that uses the default
	 * encoding.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, boolean printToStdout,
	                                                  boolean overwriteFiles)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, printToStdout, overwriteFiles);
	}

	/**
	 * Configure an AE descriptor for a pipeline that uses the default
	 * encoding and does not overwrite files.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory, boolean printToStdout)
	        throws UIMAException, IOException {
		return configure(outputDirectory, null, printToStdout, false);
	}

	/**
	 * Configure an AE descriptor for a pipeline that uses the default
	 * encoding, does not print to STDOUT, and does not overwrite files.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(File outputDirectory) throws UIMAException,
	        IOException {
		return configure(outputDirectory, null, false, false);
	}

	/**
	 * Configure an AE descriptor for a pipeline that (only) prints to STDOUT.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(String encoding) throws UIMAException,
	        IOException {
		return configure(null, encoding, true, false);
	}

	/**
	 * Configure the default AE descriptor for a pipeline that uses the
	 * default encoding and (only) prints to STDOUT.
	 * 
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return configure(null, null, true, false);
	}

	/**
	 * Detect sentences in the {@link Views.CONTENT_TEXT} view of a CAS.
	 */
	@Override
	public void process(CAS cas) throws AnalysisEngineProcessException {
		JCas textJCas;

		try {
			textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
			setStream(cas.getView(Views.CONTENT_RAW.toString()));
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
		AnnotationIndex<Annotation> tokenIdx = textJCas.getAnnotationIndex(TokenAnnotation.type);

		while (sentenceIt.hasNext()) {
			Annotation sentence = sentenceIt.next();
			FSIterator<Annotation> tokenIt = tokenIdx.subiterator(sentence, true, true);
			boolean chunkOpen = false;

			while (tokenIt.hasNext()) {
				TokenAnnotation token = (TokenAnnotation) tokenIt.next();
				String value = escape(token.getCoveredText());
				String pos = escape(token.getPos());
				String chunk = token.getChunk();
				String stem = token.getStem();

				if (stem == null)
					stem = token.getCoveredText();

				stem = escape(stem);

				try {
					if (chunk != null && !token.getInChunk()) {
						if (chunkOpen)
							write("} ");
						write("{ ");
						write(chunk);
						write(' ');
						chunkOpen = true;
					} else if (chunk == null) {
						if (chunkOpen)
							write("} ");
						chunkOpen = false;
					}

					write(value);
					write('_');
					write(pos);
					write('_');
					write(stem);

					if (token.getEnd() != sentence.getEnd())
						write(' ');
				} catch (IOException e) {
					throw new AnalysisEngineProcessException(e);
				}
			}

			try {
				if (chunkOpen)
					write(" }");
				write(LINEBREAK);
			} catch (IOException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}

		try {
			unsetStream();
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private String escape(String text) {
		return text.replace('_', '\u2423');
	}
}
