package txtfnnl.uima.collection;

import java.io.IOException;

import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * A CAS consumer that writes all {@link TokenAnnotation tokens} in a special "markup" format,
 * adding line separators between {@link SentenceAnnotation sentences}.
 * <p>
 * The tokens found in each sentence are followed by their PoS tag and stem, separated by
 * underscores from the token itself. Chunks are grouped using square braces followed by the phrase
 * tag. For example, the sentence "The dog barks repeatedly." would be represented as the following
 * single line:
 * 
 * <pre>
 * [ NP The_DT_the dog_NN_dog ] [ VP barks_VB_bark ] [ ADJP repeatedly_ADJ_repeat ] ._._.
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
  public static class Builder extends TextWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      this(TaggedSentenceLineWriter.class);
    }
  }

  /** Configure a {@link TaggedSentenceLineWriter} description builder. */
  public static Builder configure() {
    return new Builder();
  }

  /**
   * Detect sentences in the {@link Views.CONTENT_TEXT} view of a CAS.
   */
  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas textJCas;
    try {
      textJCas = cas.getJCas();
      setStream(textJCas);
    } catch (CASException e1) {
      throw new AnalysisEngineProcessException(e1);
    } catch (final IOException e2) {
      throw new AnalysisEngineProcessException(e2);
    }
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
    final AnnotationIndex<Annotation> tokenIdx = textJCas.getAnnotationIndex(TokenAnnotation.type);
    while (sentenceIt.hasNext()) {
      final Annotation sentence = sentenceIt.next();
      final FSIterator<Annotation> tokenIt = tokenIdx.subiterator(sentence, true, true);
      while (tokenIt.hasNext()) {
        final TokenAnnotation token = (TokenAnnotation) tokenIt.next();
        final String text = token.getCoveredText();
        final String posTag = token.getPos();
        final String chunkTag = token.getChunk();
        String stem = token.getStem();
        // fallback: use the text itself if no stem is given
        if (stem == null) stem = text.toLowerCase();
        try {
          if (chunkTag != null && token.getChunkBegin()) {
            write("[ ");
            write(escape(chunkTag));
            write(' ');
          }
          write(escape(text));
          write('_');
          write(escape(posTag));
          write('_');
          write(escape(stem));
          if (chunkTag != null && token.getChunkEnd()) write(" ] ");
          else write(' ');
        } catch (final IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
      }
      try {
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
