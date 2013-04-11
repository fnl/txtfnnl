package txtfnnl.uima.collection;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.TextAnnotation;

/**
 * A CAS consumer that writes out a particular annotation type, where each line represents a hit
 * and the found data.
 * 
 * @see TextWriter
 * @author Florian Leitner
 */
public class AnnotationLineWriter extends TextWriter {
  /**
   * If <code>true</code> (the default), line-breaks within covered text will be replaced with
   * white-spaces.
   */
  public static final String PARAM_REPLACE_NEWLINES = "ReplaceNewlines";
  @ConfigurationParameter(name = PARAM_REPLACE_NEWLINES, defaultValue = "true")
  private Boolean replaceNewlines;
  public static final String PARAM_ANNOTATOR_URI = "AnnotatorUri";
  @ConfigurationParameter(name = PARAM_ANNOTATOR_URI)
  private String annotatorUri;
  public static final String PARAM_ANNOTATION_NAMESPACE = "AnnotationNamespace";
  @ConfigurationParameter(name = PARAM_ANNOTATION_NAMESPACE)
  private String annotationNs;
  public static final String PARAM_ANNOTATION_ID = "AnnotationId";
  @ConfigurationParameter(name = PARAM_ANNOTATION_ID)
  private String annotationId;
  static final String LINEBREAK = System.getProperty("line.separator");
  private static NumberFormat decimals = DecimalFormat.getInstance();
  {
    decimals.setMaximumFractionDigits(5);
    decimals.setMinimumFractionDigits(5);
  }

  public static class Builder extends TextWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      super(AnnotationLineWriter.class);
    }

    public Builder maintainNewlines() {
      setOptionalParameter(PARAM_REPLACE_NEWLINES, false);
      return this;
    }

    public Builder setAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_ANNOTATOR_URI, uri);
      return this;
    }

    public Builder setAnnotationNamespace(String ns) {
      setOptionalParameter(PARAM_ANNOTATION_NAMESPACE, ns);
      return this;
    }

    public Builder setAnnotationId(String id) {
      setOptionalParameter(PARAM_ANNOTATION_ID, id);
      return this;
    }
  }

  public static Builder configureTodo() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger.log(Level.INFO, "constraint: '" + annotatorUri + "@" + annotationNs + ":" +
        annotationId + "'");
  }

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
    FSMatchConstraint cons = TextAnnotation.makeConstraint(textJCas, annotatorUri, annotationNs,
        annotationId);
    FSIterator<Annotation> iter = textJCas.createFilteredIterator(
        TextAnnotation.getIterator(textJCas), cons);
    int count = 0;
    while (iter.hasNext()) {
      count++;
      final TextAnnotation ann = (TextAnnotation) iter.next();
      String text = ann.getCoveredText();
      if (replaceNewlines) text = text.replace('\n', ' ');
      try {
        write(text);
        write('\t');
        if (annotatorUri == null) write(ann.getAnnotator());
        if (annotatorUri == null && annotationNs == null) write('@');
        else if (annotatorUri == null) write('\t');
        if (annotationNs == null) write(ann.getNamespace());
        if (annotationNs == null && annotationId == null) write(':');
        else if (annotationNs == null) write('\t');
        if (annotationId == null) {
          write(ann.getIdentifier());
          write('\t');
        }
        FSArray props = ann.getProperties();
        for (int i = 0; i < props.size(); i++) {
          Property p = (Property) props.get(i);
          write(p.getName());
          write('=');
          write(p.getValue());
          write('\t');
        }
        write(decimals.format(ann.getConfidence()));
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
    logger.log(Level.FINE, "wrote {0} annotations", count);
  }
}
