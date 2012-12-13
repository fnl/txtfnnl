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
 * A CAS consumer that writes all {@link TokenAnnotation tokens} in a special "markup" format,
 * adding line separators between {@link SentenceAnnotation sentences}.
 * <p>
 * The tokens found in each sentence are followed by their PoS tag and stem, separated by
 * underscores from the token itself. Chunks are grouped using curly braces followed by the phrase
 * tag. For example, the sentence "The dog barks repeatedly." would be represented as the following
 * single line:
 * 
 * <pre>
 * { NP The_DT_the dog_NN_dog } { VP barks_VB_bark } { ADJP repeatedly_ADJ_repeat } ._._.
 * </pre>
 * 
 * Due to this format, sentences are always written to a single line. As the tokens' text, PoS tag,
 * and stem are separated by underscores, any underscores in the text, tag, or stem themselves are
 * replaced with U+2423 ("␣", OPEN BOX), a symbol similar to the underscore.
 * 
 * @see TextWriter
 * @author Florian Leitner
 */
public final class TaggedSentenceLineWriter extends TextWriter {
    /**
     * Configure a TaggedSentenceLineWriter descriptor.
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
            final String encoding, final boolean printToStdout, final boolean overwriteFiles)
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
     * Configure a default TaggedSentenceLineWriter descriptor. This consumer writes to STDOUT
     * (only), using the system default encoding.
     * 
     * @throws IOException
     * @throws UIMAException
     */
    public static AnalysisEngineDescription configure() throws UIMAException, IOException {
        return TaggedSentenceLineWriter.configure(null, null, true, false);
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
        } catch (final CASException e) {
            throw new AnalysisEngineProcessException(e);
        } catch (final IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
        final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
        final AnnotationIndex<Annotation> tokenIdx =
            textJCas.getAnnotationIndex(TokenAnnotation.type);
        while (sentenceIt.hasNext()) {
            final Annotation sentence = sentenceIt.next();
            final FSIterator<Annotation> tokenIt = tokenIdx.subiterator(sentence, true, true);
            boolean chunkOpen = false; // remember if a chunk span is
                                       // currently "open"
            while (tokenIt.hasNext()) {
                final TokenAnnotation token = (TokenAnnotation) tokenIt.next();
                final String text = token.getCoveredText();
                final String posTag = token.getPos();
                final String chunkTag = token.getChunk();
                String stem = token.getStem();
                // fallback: use the text itself if no stem is given
                if (stem == null) {
                    stem = text;
                }
                try {
                    if (chunkTag != null && !token.getInChunk()) {
                        if (chunkOpen) {
                            write("} ");
                        }
                        write("{ ");
                        write(chunkTag);
                        write(' ');
                        chunkOpen = true;
                    } else if (chunkTag == null) {
                        if (chunkOpen) {
                            write("} ");
                        }
                        chunkOpen = false;
                    }
                    write(escape(text));
                    write('_');
                    write(escape(posTag));
                    write('_');
                    write(escape(stem));
                    if (token.getEnd() != sentence.getEnd()) {
                        write(' ');
                    }
                } catch (final IOException e) {
                    throw new AnalysisEngineProcessException(e);
                }
            }
            try {
                if (chunkOpen) {
                    write(" }");
                }
                write(System.getProperty("line.separator"));
            } catch (final IOException e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
        try {
            unsetStream();
        } catch (final IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    /** Replace the token-PoS-stem separator character inside text. */
    private String escape(String text) {
        return text.replace('_', '\u2423');
    }
}
