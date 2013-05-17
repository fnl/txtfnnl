/* Created on May 7, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.collection;

import java.io.File;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.CasConsumer_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.tcas.DocumentAnnotation;

/**
 * OutputWriter
 * 
 * @author Florian Leitner
 */
public abstract class OutputWriter extends CasConsumer_ImplBase {
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
  protected int counter; // to create unique output file names if necessary

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
      setOptionalParameter(PARAM_PRINT_TO_STDOUT, Boolean.TRUE);
    }

    /** Set an output directory instead of writing to STDOUT. */
    public Builder setOutputDirectory(File outputDirectory) {
      setOptionalParameter(PARAM_OUTPUT_DIRECTORY, outputDirectory);
      if (outputDirectory == null) setOptionalParameter(PARAM_PRINT_TO_STDOUT, Boolean.TRUE);
      else setOptionalParameter(PARAM_PRINT_TO_STDOUT, Boolean.FALSE);
      return this;
    }

    /** Use a different output encoding than the system's default. */
    public Builder setEncoding(String encoding) {
      setOptionalParameter(PARAM_ENCODING, encoding);
      return this;
    }

    /** Overwrite existing files. */
    public Builder overwriteFiles() {
      setOptionalParameter(PARAM_OVERWRITE_FILES, Boolean.TRUE);
      return this;
    }
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    counter = 0;
  }
  
  protected File openOutputFile(JCas jcas, String ext) {
    String resourceName = null;
    if (jcas.getSofaDataURI() != null) {
      resourceName = (new File(jcas.getSofaDataURI())).getName();
    } else {
      for (final DocumentAnnotation ann : JCasUtil.select(jcas, DocumentAnnotation.class))
        if ("resourceName".equals(ann.getNamespace())) resourceName = ann.getIdentifier();
    }
    if (resourceName == null || resourceName.length() == 0) {
      resourceName = String.format("doc-%06d", ++counter);
    } else if (resourceName.lastIndexOf('.') > 0) {
      resourceName = resourceName.substring(0, resourceName.lastIndexOf('.'));
    }
    File outputFile = new File(outputDirectory, resourceName + "." + ext);
    if (!overwriteFiles && outputFile.exists()) {
      int idx = 2;
      while (outputFile.exists())
        outputFile = new File(outputDirectory, resourceName + "." + idx++ + "." + ext);
    }
    return outputFile;
  }
}
