package txtfnnl.uima.collection;

import java.io.IOException;

import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.utils.StringUtils;

/**
 * A CAS consumer that writes CSV content, adding line separators after the segments annotated as
 * {@link SemanticAnnotation SemanticAnnotations}. Semantic annotations will be written as "
 * <code>namespace SEP identifier SEP offset SEP text</code>", where <code>text</code> is the
 * content of the annotation with newlines replaced by white-spaces.
 * 
 * @see TextWriter
 * @author Florian Leitner
 */
public class SemanticAnnotationWriter extends TextWriter {
  /**
   * If <code>true</code> (the default), line-breaks within annotations will be replaced with
   * white-spaces.
   * <p>
   * Detected line-breaks are Windows (CR-LF) and Unix line-breaks (LF only).
   */
  public static final String PARAM_REPLACE_NEWLINES = "ReplaceNewlines";
  @ConfigurationParameter(name = PARAM_REPLACE_NEWLINES, defaultValue = "true")
  private Boolean replaceNewlines;
  /**
   * Separator character to use between namespace, identifier, offset, and text fields (default:
   * TAB).
   */
  public static final String PARAM_FIELD_SEPARATOR = "FieldSeparator";
  @ConfigurationParameter(name = PARAM_FIELD_SEPARATOR, defaultValue = "\t")
  private String fieldSeparator;
  static final String LINEBREAK = System.getProperty("line.separator");

  public static class Builder extends TextWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }
    
    public Builder() {
      this(SemanticAnnotationWriter.class);
    }
    
    public Builder maintainNewlines() {
      setOptionalParameter(PARAM_REPLACE_NEWLINES, Boolean.FALSE);
      return this;
    }
    
    public Builder setFieldSeparator(String sep) {
      setOptionalParameter(PARAM_FIELD_SEPARATOR, sep);
      return this;
      
    }
  }
  
  /** Configure a {@link SemanticAnnotationWriter} description builder. */
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
    final FSIterator<Annotation> annotationIt = SemanticAnnotation.getIterator(textJCas);
    while (annotationIt.hasNext()) {
      final SemanticAnnotation ann = (SemanticAnnotation) annotationIt.next();
      final String text = replaceNewlines ? StringUtils.join(' ',
          ann.getCoveredText().split(LINEBREAK)) : ann.getCoveredText();
      try {
        write(ann.getNamespace());
        write(fieldSeparator);
        write(ann.getIdentifier());
        write(fieldSeparator);
        write(ann.getOffset().toString());
        write(fieldSeparator);
        write(text);
        write(LINEBREAK);
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
}
