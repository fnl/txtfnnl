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
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.UIMAUtils;
import txtfnnl.uima.Views;
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

  /**
   * Configure a {@link SemanticAnnotationWriter} description. Note that if the
   * {@link #outputDirectory} is <code>null</code> and {@link #printToStdout} is <code>false</code>
   * , a {@link ResourceInitializationException} will occur when creating the AE.
   * 
   * @param outputDirectory path to the output directory (or null)
   * @param encoding encoding to use for the text (or null)
   * @param printToStdout whether to print to STDOUT or not
   * @param overwriteFiles whether to overwrite existing files or not
   * @param replaceNewlines whether to replace line-breaks in annotations with white-spaces or not
   * @param fieldSeparator to use between the output fields
   * @return a configured AE description
   * @throws IOException
   * @throws UIMAException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final File outputDirectory,
      final String encoding, final boolean printToStdout, final boolean overwriteFiles,
      final boolean replaceNewlines, final String fieldSeparator) throws UIMAException,
      IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(SemanticAnnotationWriter.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_OUTPUT_DIRECTORY, outputDirectory);
            put(PARAM_ENCODING, encoding);
            put(PARAM_PRINT_TO_STDOUT, printToStdout);
            put(PARAM_OVERWRITE_FILES, overwriteFiles);
            put(PARAM_REPLACE_NEWLINES, replaceNewlines);
            put(PARAM_FIELD_SEPARATOR, fieldSeparator);
          }
        }));
  }

  /**
   * Configure a {@link SemanticAnnotationWriter} description using all the defaults:
   * <ul>
   * <li>outputDirectory=<code>null</code> (instead, print to STDOUT)</li>
   * <li>encoding=<code>null</code> (i.e., use system default)</li>
   * <li>printToStdout=<code>true</code></li>
   * <li>overwriteFiles=<code>false</code></li>
   * <li>fieldSeparator=<code>TAB</code></li>
   * <li>replaceNewlines=<code>true</code></li>
   * </ul>
   * 
   * @see #configure(File, String, boolean, boolean, boolean)
   * @return a configured AE description
   * @throws IOException
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return SemanticAnnotationWriter.configure(null, null, true, false, true, "\t");
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
