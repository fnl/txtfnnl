package txtfnnl.uima.collection;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * A CAS consumer that writes plaintext content, adding line separators after the segments
 * annotated as {@link SentenceAnnotation SentenceAnnotations}. Sentences originally only separated
 * by Unicode spaces will be separated by line separators instead. If newlines in sentences are
 * being replaced ( {@link #PARAM_REPLACE_NEWLINES} ), any consecutive Unicode space characters
 * will be normalized to one single white-space characters, except for the zero-width space
 * characters (U+200B and U+FEFF).
 * 
 * @see TextWriter
 * @author Florian Leitner
 */
public class SentenceLineWriter extends TextWriter {
  /**
   * If <code>true</code> (the default), <i>single</i> (non-consecutive) line-breaks within
   * sentences will be replaced with white-spaces.
   * <p>
   * Detected line-breaks are Windows (CR-LF) and Unix line-breaks (LF only). Note that with this
   * option, all (consecutive) Unicode spaces will be normalized to a single ASCII white-space and
   * the sentences are (space-) trimmed.
   */
  public static final String PARAM_REPLACE_NEWLINES = "ReplaceNewlines";
  @ConfigurationParameter(name = PARAM_REPLACE_NEWLINES, defaultValue = "true")
  private Boolean replaceNewlines;
  /** In addition to the sentences, write out all content between them, too (default: true). */
  public static final String PARAM_INCLUDE_ALL_CONTENT = "IncludeNonSentenceContent";
  @ConfigurationParameter(name = PARAM_INCLUDE_ALL_CONTENT, defaultValue = "true")
  private boolean includeAllContent;
  // all space characters including the Unicode spaces (U+00A0 (&nbs;), and
  // the space chars in
  // U+2000 to U+200A, plus U+202F (narrow no-break space), U+205F (medium
  // math. space), and
  // U+3000 (ideographic CJK space); but not the zero-width spaces U+200B
  // (zero width space)
  // and U+FEFF (zero width no-break space)
  static final Pattern REGEX_SPACES = Pattern
      .compile("[ \\t\\v\\f\\r\\u00a0\\u2000\\u2001\\u2002\\u2003\\u2004\\u2005\\u2006"
          + "\\u2007\\u2008\\u2009\\u200a\\u202f\\u205f\\u3000]+");
  static final Pattern REGEX_LINEBREAK_SPACE = Pattern.compile("(\\r?\\n) ");
  static final Pattern REGEX_SINGLE_LINEBREAK = Pattern.compile("(?<!\\r?\\n)\\r?\\n(?!\\r?\\n)");
  static final String LINEBREAK = System.getProperty("line.separator");

  public static class Builder extends TextWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      this(SentenceLineWriter.class);
    }

    /** Do not replace newline characters within sentences with spaces. */
    public Builder maintainNewlines() {
      setOptionalParameter(PARAM_REPLACE_NEWLINES, Boolean.FALSE);
      return this;
    }

    /**
     * Do not write content between sentences.
     * <p>
     * By default, content between sentences is written on separate lines.
     */
    public Builder excludeOtherContent() {
      setOptionalParameter(PARAM_INCLUDE_ALL_CONTENT, Boolean.FALSE);
      return this;
    }
  }

  /** Configure a {@link SentenceLineWriter} description builder. */
  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas textJCas;
    try {
      textJCas = cas.getView(textView).getJCas();
      setStream(cas.getView(rawView));
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    final String text = textJCas.getDocumentText();
    int offset = 0;
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textJCas);
    int count = 0;
    while (sentenceIt.hasNext()) {
      count++;
      final Annotation ann = sentenceIt.next();
      final String prefix = text.substring(offset, ann.getBegin());
      final String normalPrefix = normalizeSpaces(prefix);
      final String sentence = ann.getCoveredText();
      try {
        if (includeAllContent && normalPrefix.length() > 0 && !" ".equals(normalPrefix)) {
          write(replaceNewlines ? normalPrefix : prefix);
          write(LINEBREAK);
        }
        if (replaceNewlines) {
          write(joinNewlines(sentence));
          write(LINEBREAK);
        } else {
          for (final String line : sentence.split("\\r?\\n")) {
            write(line);
            write(LINEBREAK);
          }
        }
      } catch (final IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
      offset = ann.getEnd();
    }
    try {
      if (includeAllContent && offset != text.length()) {
        write(normalizeSpaces(text.substring(offset)));
      }
      unsetStream();
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    logger.log(Level.FINE, "wrote {0} sentences", count);
  }

  /**
   * Replace and trim multiple spaces with a single ASCII white-space.
   * 
   * @param text to replace spaces in
   * @return the text without consecutive spaces and trimmed
   */
  String normalizeSpaces(String text) {
    return REGEX_SPACES.matcher(text).replaceAll(" ").trim();
  }

  /**
   * Replace single line-breaks with a (normalized) white-space.
   * 
   * @param text with newline characters (if any)
   * @return a trimmed String where the single line-breaks are replaced with a white-space and all
   *         Unicode spaces have been normalized
   */
  String joinNewlines(String text) {
    // normalize spaces to one single space
    text = normalizeSpaces(text);
    // replace linebreak-whitespace sequences with linebreak only
    text = REGEX_LINEBREAK_SPACE.matcher(text).replaceAll("$1");
    // replace single linebreaks with spaces and trim the sentence
    return REGEX_SINGLE_LINEBREAK.matcher(text).replaceAll(" ").trim();
  }
}
