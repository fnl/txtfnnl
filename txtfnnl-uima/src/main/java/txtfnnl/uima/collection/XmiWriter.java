package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.transform.OutputKeys;

import org.xml.sax.SAXException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.XMLSerializer;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.Views;

/**
 * A CAS consumer that serializes the documents to XMI.
 * 
 * @author Florian Leitner
 */
public class XmiWriter extends OutputWriter {
  /**
   * Optional Boolean parameter that indicates if output should use standard XMI formatting or not.
   * 
   * @see org.apache.uima.util.XMLSerializer
   */
  public static final String PARAM_FORMAT_XMI = "FormatXmi";
  @ConfigurationParameter(name = PARAM_FORMAT_XMI, defaultValue = "true")
  private boolean formatXmi;
  /**
   * Serialize to XML 1.1 (all Unicode characters allowed); defaults to <code>true</code>.
   */
  public static final String PARAM_USE_XML_11 = "UseXml11";
  @ConfigurationParameter(name = PARAM_USE_XML_11, defaultValue = "true")
  private boolean useXml11;
  private int counter = 0; // to create "new" output file names if necessary

  public static class Builder extends OutputWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass, File outputDirectory) {
      super(klass);
      setRequiredParameter(PARAM_OUTPUT_DIRECTORY, outputDirectory);
    }

    public Builder(File outputDirectory) {
      this(XmiWriter.class, outputDirectory);
    }
    
    /** Set an output directory instead of writing to STDOUT. */
    @Override
    public Builder setOutputDirectory(File outputDirectory) {
      setRequiredParameter(PARAM_OUTPUT_DIRECTORY, outputDirectory);
      return this;
    }

    /** Do not format the XML output. */
    public Builder doNotFormatXML() {
      setOptionalParameter(PARAM_FORMAT_XMI, Boolean.FALSE);
      return this;
    }

    /** Use (outdated, old) XML 1.0 (instead of 1.1). */
    public Builder useOldXml() {
      setOptionalParameter(PARAM_USE_XML_11, Boolean.FALSE);
      return this;
    }
  }

  /** Configure a default builder for writing the XMI of the text CASes to a directory. */
  public static Builder configure(File outputDirectory) {
    return new Builder(outputDirectory);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs();
    }
    if (!(outputDirectory.isDirectory() && outputDirectory.canWrite()))
      throw new ResourceInitializationException(new IOException(PARAM_OUTPUT_DIRECTORY + "='" +
          outputDirectory + "' not a writeable directory"));
  }

  /**
   * Consume a CAS to produce a file in the XML Metadata Interchange format. This consumer expects
   * the CAS to have both a raw and a text view. The file URI is fetched from the raw view, while
   * the XMI content is created from the text view.
   * 
   * @param cas CAS to serialize
   */
  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    final String uri = cas.getView(Views.CONTENT_RAW.toString()).getSofaDataURI();
    String outFileBaseName;
    File outFile;
    try {
      final File inFile = new File(new URI(uri));
      outFileBaseName = inFile.getName();
    } catch (final URISyntaxException e) {
      outFileBaseName = String.format("doc-%06d", ++counter);
    } catch (final NullPointerException e) {
      outFileBaseName = String.format("doc-%06d", ++counter);
    }
    outFile = new File(outputDirectory, outFileBaseName + ".xmi");
    if (!overwriteFiles && outFile.exists()) {
      int idx = 2;
      while (outFile.exists()) {
        outFile = new File(outputDirectory, outFileBaseName + "." + idx++ + ".xmi");
      }
    }
    try {
      writeXmi(cas, outFile);
    } catch (final SAXException e) {
      throw new AnalysisEngineProcessException(e);
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

  /**
   * Serialize a CAS to a file in XMI format.
   * 
   * @param cas CAS to serialize
   * @param file output file
   * @throws SAXException
   * @throws IOException
   */
  private void writeXmi(CAS cas, File file) throws IOException, SAXException {
    FileOutputStream out = null;
    try {
      out = new FileOutputStream(file);
      final XmiCasSerializer xmi = new XmiCasSerializer(cas.getTypeSystem());
      final XMLSerializer xml = new XMLSerializer(out, formatXmi);
      if (useXml11) {
        xml.setOutputProperty(OutputKeys.VERSION, "1.1");
      }
      if (encoding != null) {
        xml.setOutputProperty(OutputKeys.ENCODING, encoding);
      }
      xmi.serialize(cas, xml.getContentHandler());
    } finally {
      if (out != null) {
        out.close();
      }
    }
  }
}
