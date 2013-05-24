package txtfnnl.uima.collection;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
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
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.TokenSurrounding;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import txtfnnl.utils.Offset;

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
  /**
   * If <code>true</code> (the default), tabulators within covered text will be replaced with
   * white-spaces.
   */
  public static final String PARAM_REPLACE_TABS = "ReplaceTabulators";
  @ConfigurationParameter(name = PARAM_REPLACE_TABS, defaultValue = "true")
  private Boolean replaceTabs;
  /**
   * If <code>true</code> the token before and after the annotation are shown, as well as any
   * prefix and suffix, as word-before TAB prefix TAB annotation TAB suffix TAB word-after instead
   * of just the annotation.
   */
  public static final String PARAM_PRINT_SURROUNDINGS = "PrintSurroundings";
  @ConfigurationParameter(name = PARAM_PRINT_SURROUNDINGS, defaultValue = "false")
  private Boolean printSurroundings;
  /**
   * If <code>true</code> the matching token's PoS tag of the annotation is added as a row after
   * the annotation itself.
   */
  public static final String PARAM_PRINT_POS_TAG = "PrintPosTag";
  @ConfigurationParameter(name = PARAM_PRINT_POS_TAG, defaultValue = "false")
  private Boolean printPosTag;
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

    public Builder maintainTabulators() {
      setOptionalParameter(PARAM_REPLACE_TABS, false);
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

    public Builder printSurroundings() {
      setOptionalParameter(PARAM_PRINT_SURROUNDINGS, true);
      return this;
    }

    public Builder printPosTag() {
      setOptionalParameter(PARAM_PRINT_POS_TAG, true);
      return this;
    }
  }

  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger.log(Level.CONFIG, "constraint: '" + annotatorUri + "@" + annotationNs + ":" +
        annotationId + "'");
  }

  private static final int BEFORE = 0;
  private static final int PREFIX = 1;
  private static final int SUFFIX = 3;
  private static final int AFTER = 4;

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
    FSMatchConstraint cons = TextAnnotation.makeConstraint(textJCas, annotatorUri, annotationNs,
        annotationId);
    FSIterator<Annotation> annotationIter = textJCas.createFilteredIterator(
        TextAnnotation.getIterator(textJCas), cons);
    Map<TextAnnotation, Collection<TokenAnnotation>> coveredTokens = null;
    Map<TextAnnotation, Collection<TokenAnnotation>> innerTokens = null;
    TokenSurrounding tokens = null;
    String[] surrounding = null;
    int annCount = 0;
    Offset last = null;
    String posTag = null;
    if (printSurroundings || printPosTag) {
      coveredTokens = JCasUtil.indexCovered(textJCas, TextAnnotation.class, TokenAnnotation.class);
      innerTokens = JCasUtil.indexCovering(textJCas, TextAnnotation.class, TokenAnnotation.class);
    }
    while (annotationIter.hasNext()) {
      final TextAnnotation ann = (TextAnnotation) annotationIter.next();
      annCount++;
      String text = ann.getCoveredText();
      if (replaceTabs) text = text.replace('\t', ' ');
      if (printSurroundings) {
        if (!ann.getOffset().equals(last)) {
          last = ann.getOffset();
          tokens = new TokenSurrounding(textJCas, ann, coveredTokens, innerTokens);
          surrounding = new String[] { "", "", text, "", "" };
          if (tokens.before != null) surrounding[BEFORE] = tokens.before.getCoveredText();
          if (tokens.prefix != null)
            surrounding[PREFIX] = tokens.prefix.getCoveredText().substring(0,
                ann.getBegin() - tokens.prefix.getBegin());
          if (tokens.suffix != null)
            surrounding[SUFFIX] = tokens.suffix.getCoveredText().substring(
                ann.getEnd() - tokens.suffix.getBegin());
          if (tokens.after != null) surrounding[AFTER] = tokens.after.getCoveredText();
          if (replaceTabs) for (int i = 0; i < surrounding.length; ++i)
            surrounding[i] = surrounding[i].replace('\t', ' ');
          posTag = (tokens.suffix == null) ? null : tokens.suffix.getPos();
        }
        text = StringUtils.join(surrounding, '\t');
      } // end printSurroundings
      if (printPosTag) {
        if (!printSurroundings) {
          if (!ann.getOffset().equals(last)) {
            last = ann.getOffset();
            tokens = new TokenSurrounding(textJCas, ann, coveredTokens, innerTokens);
            posTag = (tokens.suffix == null) ? null : tokens.suffix.getPos();
          }
        }
        if (posTag == null) posTag = "NULL";
        text = String.format("%s\t%s", text, posTag);
      } // end printPosTag
      if (replaceNewlines) text = text.replace('\n', ' ');
      try {
        write(text);
        write('\t');
        write(ann.getOffset().toString());
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
        if (props != null) {
          for (int i = 0; i < props.size(); i++) {
            Property p = (Property) props.get(i);
            if (p.getName().contains(" ")) {
              write('"');
              write(p.getName());
              write('"');
            } else {
              write(p.getName());
            }
            write('=');
            write('"');
            write(replaceNewlines ? p.getValue().replace('\n', ' ') : p.getValue());
            write('"');
            write('\t');
          }
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
    logger.log(Level.FINE, "wrote {0} annotations", annCount);
  }
}
