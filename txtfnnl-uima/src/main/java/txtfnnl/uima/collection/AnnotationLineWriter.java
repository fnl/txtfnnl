package txtfnnl.uima.collection;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

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

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

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
    TokenAnnotation[] tokens = new TokenAnnotation[5];
    List<TokenAnnotation> allTokens = new ArrayList<TokenAnnotation>();
    if (printSurroundings || printPosTag) {
      FSIterator<Annotation> it = TokenAnnotation.getIterator(textJCas);
      while (it.hasNext())
        allTokens.add((TokenAnnotation) it.next());
    }
    int allTokensIdx = 0;
    tokens[2] = null;
    int before = 0;
    int prefix = 1;
    int suffix = 3;
    int after = 4;
    int count = 0;
    while (iter.hasNext()) {
      count++;
      final TextAnnotation ann = (TextAnnotation) iter.next();
      String text = ann.getCoveredText();
      String posTag = null;
      if (printSurroundings) {
        String[] surround = new String[] { "", "", text, "", "" };
        TokenAnnotation begin = null;
        int idx = allTokensIdx;
        while (idx < allTokens.size()) {
          TokenAnnotation tok = allTokens.get(idx++);
          if (isBefore(tok, ann)) {
            begin = tok;
          } else {
            if (begin != null) {
              surround[before] = begin.getCoveredText();
              allTokensIdx = idx - 2;
            }
            begin = null;
            String txt = tok.getCoveredText();
            if (isSurrounding(tok, ann)) {
              surround[prefix] = txt.substring(0, ann.getBegin() - tok.getBegin());
              surround[suffix] = txt.substring(ann.getEnd() - tok.getBegin());
              posTag = tok.getPos();
            } else if (isAtBegin(tok, ann)) {
              surround[prefix] = txt.substring(0, ann.getBegin() - tok.getBegin());
              posTag = tok.getPos();
            } else if (isAtEnd(tok, ann)) {
              surround[suffix] = txt.substring(ann.getEnd() - tok.getBegin());
            } else if (isAfter(tok, ann)) {
              surround[after] = txt;
              break;
            } else if (isEnclosed(tok, ann)) {
              // do nothing
            } else {
              this.logger.log(Level.WARNING,
                  "token position %s undetermined relative to annotation %s", new String[] {
                      tok.getOffset().toString(), ann.getOffset().toString() });
              break;
            }
          }
        }
        text = StringUtils.join(surround, '\t');
      }
      if (printPosTag) {
        if (posTag == null) {
          while (allTokensIdx < allTokens.size()) {
            TokenAnnotation tok = allTokens.get(allTokensIdx);
            if (isSurrounding(tok, ann))
              posTag = tok.getPos();
            else if (isAtBegin(tok, ann))
              posTag = tok.getPos();
            else if (isBefore(tok, ann))
              ; // do nothing
            else
              break;
            ++allTokensIdx;
          }
        }
        if (posTag == null) posTag = "NULL";
        text = String.format("%s\t%s", text, posTag);
      }
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

  private static boolean isBefore(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getEnd() <= ann.getBegin();
  }

  private static boolean isSurrounding(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getBegin() < ann.getBegin() && tok.getEnd() > ann.getEnd();
  }

  private static boolean isEnclosed(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getBegin() >= ann.getBegin() && tok.getEnd() <= ann.getEnd();
  }

  private static boolean isAtBegin(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getEnd() > ann.getBegin() && tok.getBegin() < ann.getBegin();
  }

  private static boolean isAtEnd(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getBegin() < ann.getEnd() && tok.getEnd() > ann.getEnd();
  }

  private static boolean isAfter(TokenAnnotation tok, TextAnnotation ann) {
    return tok.getBegin() >= ann.getEnd();
  }
}
