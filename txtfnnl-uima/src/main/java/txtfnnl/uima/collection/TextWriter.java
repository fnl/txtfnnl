package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.CasConsumer_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.UIMAUtils;
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
public class TextWriter extends CasConsumer_ImplBase {
  /**
   * Optional configuration parameter String that defines the path to a directory where the output
   * files will be written. Note that the directory will be created if it does not exist.
   */
  public static final String PARAM_OUTPUT_DIRECTORY = "OutputDirectory";
  @ConfigurationParameter(name = PARAM_OUTPUT_DIRECTORY)
  protected File outputDirectory;
  /**
   * Force a particular output encoding. By default, the system's default encoding is used.
   */
  public static final String PARAM_ENCODING = "Encoding";
  @ConfigurationParameter(name = PARAM_ENCODING)
  protected String encoding;
  /**
   * Optional flag leading to the overwriting any existing files; defaults to <code>false</code>.
   * <p>
   * By inserting ".<b>n</b>" between the file name and its new suffix, the file name is made
   * unique; otherwise the existing file is replaced (where <b>n</b> is the first positive integer
   * that makes the file name "unique").
   */
  public static final String PARAM_OVERWRITE_FILES = "OverwriteFiles";
  @ConfigurationParameter(name = PARAM_OVERWRITE_FILES, defaultValue = "false")
  protected boolean overwriteFiles;
  /**
   * If <code>true</code>, the output will (also) be written to STDOUT. By default, text is not
   * written to STDOUT.
   */
  public static final String PARAM_PRINT_TO_STDOUT = "PrintToStdout";
  @ConfigurationParameter(name = PARAM_PRINT_TO_STDOUT, defaultValue = "false")
  protected Boolean printToStdout;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
      setOptionalParameter(PARAM_PRINT_TO_STDOUT, true);
    }

    public Builder() {
      super(TextWriter.class);
      setOptionalParameter(PARAM_PRINT_TO_STDOUT, true);
    }

    public Builder setOutputDirectory(File outputDirectory) {
      setOptionalParameter(PARAM_OUTPUT_DIRECTORY, outputDirectory);
      if (outputDirectory == null) setOptionalParameter(PARAM_PRINT_TO_STDOUT, true);
      else setOptionalParameter(PARAM_PRINT_TO_STDOUT, false);
      return this;
    }

    public Builder setEncoding(String encoding) {
      setOptionalParameter(PARAM_ENCODING, encoding);
      return this;
    }

    public Builder overwriteFiles() {
      setOptionalParameter(PARAM_OVERWRITE_FILES, true);
      return this;
    }
  }

  /**
   * Configure a TextWriter descriptor.
   * <p>
   * Note that if the {@link #outputDirectory} is <code>null</code> and {@link #printToStdout} is
   * <code>false</code>, a {@link ResourceInitializationException} will occur when creating the AE.
   * 
   * @param outputDirectory path to the output directory (or null)
   * @param encoding encoding to use for writing (or null)
   * @param printToStdout whether to print to STDOUT or not
   * @param overwriteFiles whether to overwrite existing files or not
   * @return a configured AE description
   * @throws IOException
   * @throws UIMAException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final File outputDirectory,
      final String encoding, final boolean printToStdout, final boolean overwriteFiles)
      throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(TextWriter.class,
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
   * Configure a default TextWriter descriptor for a pipeline. Writes to STDOUT using the system's
   * default encoding.
   * 
   * @return a configured AE description
   * @throws IOException
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return TextWriter.configure(null, null, true, false);
  }

  protected int counter; // to create "new" output file names if necessary
  protected Logger logger;
  protected Writer outputWriter;

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
        logger.log(Level.INFO, "set STDOUT to use '" + encoding + "' encoding");
      }
    }
    logger.log(Level.INFO, "initialized {0}", this.getClass().getName());
    counter = 0;
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas textJCas;
    CAS rawCas;
    try {
      textJCas = cas.getView(Views.CONTENT_TEXT.toString()).getJCas();
      rawCas = cas.getView(Views.CONTENT_RAW.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    try {
      setStream(rawCas);
      final String text = textJCas.getDocumentText();
      if (outputDirectory != null) {
        try {
          outputWriter.write(text);
        } catch (final IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
      }
      if (printToStdout) {
        System.out.print(text);
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
