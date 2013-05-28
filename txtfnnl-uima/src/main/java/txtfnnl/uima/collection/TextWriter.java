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
  }

  /** Configure a default builder for writing the plain-text view of a SOFA. */
  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    if (outputDirectory != null && (!outputDirectory.isDirectory() || !outputDirectory.canWrite()))
      throw new ResourceInitializationException(new IOException("'" +
          outputDirectory.getAbsolutePath() + "' not a writeable directory"));
    if (!printToStdout && outputDirectory == null)
      throw new ResourceInitializationException(new AssertionError(
          "no output defined (neither directory or STDOUT specified)"));
    if (encoding == null && IOUtils.isMacOSX()) {
      // fix for the Mac JDK that uses MacRoman instead of the LANG as default encoding:
      // use the encoding defined in LANG:
      encoding = IOUtils.getLocaleEncoding();
      // or, if LANG is not set, use UTF-8:
      if (encoding == null) encoding = "UTF-8";
      logger.log(Level.CONFIG, "using (fixed) ''{0}'' encoding", encoding);
    } else if (encoding != null) {
      logger.log(Level.CONFIG, "using ''{0}'' encoding", encoding);
    } else {
      logger.log(Level.CONFIG, "using default encoding ({0})",
          System.getProperty("file.encoding"));
    }
    if (printToStdout) {
      if (encoding != null) {
        try {
          IOUtils.setOutputEncoding(encoding);
        } catch (final UnsupportedEncodingException e) {
          throw new ResourceInitializationException(e);
        }
      }
      logger.log(Level.CONFIG, "writing to STDOUT");
    } else {
      logger.log(Level.CONFIG, "writing to directory ''{0}''", outputDirectory.getAbsolutePath());
    }
    if (encoding == null) encoding = System.getProperty("file.encoding");
    if (encoding == null) encoding = "UTF-8"; // safety/last resort: use UTF-8
    logger.log(Level.CONFIG, "initialized {0}", this.getClass().getName());
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas jcas;
    try {
      jcas = cas.getJCas();
    } catch (CASException e1) {
      throw new AnalysisEngineProcessException(e1);
    }
    try {
      setStream(jcas);
      if (outputDirectory != null) {
        try {
          outputWriter.write(jcas.getDocumentText());
        } catch (final IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
      }
      if (printToStdout) {
        System.out.print(jcas.getDocumentText());
      }
      unsetStream();
    } catch (final IOException e2) {
      throw new AnalysisEngineProcessException(e2);
    }
  }

  /**
   * Sets the handlers for this CAS used by the call to {@link #write(String)} according to the
   * initial setup parameter choices.
   * 
   * @param jcas the current CAS being processed
   * @throws CASException
   * @throws IOException
   */
  void setStream(JCas jcas) throws IOException {
    if (outputDirectory != null) {
      File outputFile = openOutputFile(jcas, "txt");
      logger.log(Level.INFO, "writing ''{0}''", outputFile);
      outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile), encoding);
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
