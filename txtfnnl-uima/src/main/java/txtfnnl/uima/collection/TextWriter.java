package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.Views;
import txtfnnl.utils.IOUtils;

/**
 * A CAS consumer that writes the document text (only).
 * <p>
 * Note that on <b>Apple OSX</b> the default encoding would be <i>MacRoman</i>; however, this
 * consumer uses either the encoding defined by the <code>LANG</code> environment variable or
 * otherwise defaults to <b>UTF-8</b> as a far more sensible encoding instead. The output can be
 * written to individual files or STDOUT.
 * 
 * @author Florian Leitner
 */
public class TextWriter extends OutputWriter {
  /** The name of the raw view to expect. */
  public static final String PARAM_RAW_VIEW = "RawViewName";
  @ConfigurationParameter(name = PARAM_RAW_VIEW, mandatory = false)
  protected String rawView = null;
  /** The name of the text view to expect. */
  public static final String PARAM_TEXT_VIEW = "TextViewName";
  @ConfigurationParameter(name = PARAM_TEXT_VIEW, mandatory = false)
  protected String textView = null;
  protected int counter; // to create "new" output file names if necessary
  protected Logger logger;
  protected Writer outputWriter;

  public static class Builder extends OutputWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
      setOptionalParameter(PARAM_PRINT_TO_STDOUT, Boolean.TRUE);
    }

    public Builder() {
      this(TextWriter.class);
    }
    
    /**
     * (Re-) Set ("force") printing to STDOUT.
     * <p>
     * Only relevant if an output directory was set and the writer should still write to STDOUT,
     * too.
     */
    public Builder printToStdout() {
      setOptionalParameter(PARAM_PRINT_TO_STDOUT, Boolean.TRUE);
      return this;
    }

    public Builder setRawView(String name) {
      setOptionalParameter(PARAM_RAW_VIEW, name);
      return this;
    }

    public Builder setTextView(String name) {
      setOptionalParameter(PARAM_TEXT_VIEW, name);
      return this;
    }
  }

  /** Configure a default builder for writing the plain-text view of a SOFA. */
  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    if (rawView == null) rawView = Views.CONTENT_RAW.toString();
    if (textView == null) textView = Views.CONTENT_TEXT.toString();
    if (outputDirectory != null && (!outputDirectory.isDirectory() || !outputDirectory.canWrite()))
      throw new ResourceInitializationException(new IOException("'" +
          outputDirectory.getAbsolutePath() + "' not a writeable directory"));
    if (!printToStdout && outputDirectory == null)
      throw new ResourceInitializationException(new AssertionError(
          "no output defined (neither directory or STDOUT specified)"));
    if (printToStdout) {
      if (encoding == null && IOUtils.isMacOSX()) {
        // fix broken Mac JDK that uses MacRoman instead of the LANG
        // setting as default encoding; if LANG is not set, use UTF-8.
        encoding = IOUtils.getLocaleEncoding();
        if (encoding == null) {
          encoding = "UTF-8";
        }
        try {
          IOUtils.setOutputEncoding(encoding);
        } catch (final UnsupportedEncodingException e) {
          throw new ResourceInitializationException(e);
        }
      } else if (encoding != null) {
        try {
          IOUtils.setOutputEncoding(encoding);
        } catch (final UnsupportedEncodingException e) {
          throw new ResourceInitializationException(e);
        }
      }
      if (encoding != null) {
        logger.log(Level.INFO, "writing to STDOUT with '" + encoding + "' encoding");
      } else {
        logger.log(Level.INFO, "writing to STDOUT using the default encoding");
      }
    } else {
      logger.log(Level.INFO, "writing to '" + outputDirectory.getAbsolutePath() + "'");
    }
    logger.log(Level.INFO, "initialized {0}", this.getClass().getName());
    counter = 0;
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas textJCas;
    CAS rawCas;
    try {
      textJCas = cas.getView(textView).getJCas();
      rawCas = cas.getView(rawView);
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    try {
      setStream(rawCas);
      if (outputDirectory != null) {
        try {
          outputWriter.write(textJCas.getDocumentText());
        } catch (final IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
      }
      if (printToStdout) {
        System.out.print(textJCas.getDocumentText());
      }
      unsetStream();
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

  /**
   * Sets the handlers for this CAS used by the call to {@link #write(String)} according to the
   * initial setup parameter choices.
   * 
   * @param doc the current CAS being processed
   * @throws CASException
   * @throws IOException
   */
  void setStream(CAS doc) throws IOException {
    if (outputDirectory != null) {
      String inputName = (new File(doc.getSofaDataURI())).getName();
      if (inputName == null || inputName.length() == 0) {
        inputName = String.format("doc-%06d", ++counter);
      }
      File outputFile = new File(outputDirectory, inputName + ".txt");
      if (!overwriteFiles && outputFile.exists()) {
        int idx = 2;
        while (outputFile.exists()) {
          outputFile = new File(outputDirectory, inputName + "." + idx++ + ".txt");
        }
      }
      if (encoding == null) {
        logger.log(
            Level.INFO,
            String.format("writing to '%s' using '%s' encoding", outputFile,
                System.getProperty("file.encoding")));
        outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile));
      } else {
        logger.log(Level.INFO,
            String.format("writing to '%s' using '%s' encoding", outputFile, encoding));
        outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile), encoding);
      }
    }
  }

  /**
   * Close the currently open output file handle (if any).
   * 
   * @throws IOException
   */
  void unsetStream() throws IOException {
    if (outputDirectory != null) {
      outputWriter.close();
    }
  }

  /**
   * Write a string to the output stream(s).
   * 
   * @param ch to write
   * @throws IOException
   */
  void write(String text) throws IOException {
    if (outputDirectory != null) {
      outputWriter.write(text);
    }
    if (printToStdout) {
      System.out.print(text);
    }
  }

  /**
   * Write a single character to the output stream(s).
   * 
   * @param ch to write
   * @throws IOException
   */
  void write(char ch) throws IOException {
    if (outputDirectory != null) {
      outputWriter.append(ch);
    }
    if (printToStdout) {
      System.out.append(ch);
    }
  }
}
