package txtfnnl.uima.collection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;

import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.utils.IOUtils;

public class TestXmiWriter {
  private static final String OUTPUT_DIR = "src/test/resources/output-test";
  private final File testDir = new File(OUTPUT_DIR);
  private final File outputFile = new File(testDir, "raw.xmi");
  private AnalysisEngine fileSystemXmiConsumer;
  private CAS cas;

  @Before
  public void setUp() throws Exception {
    // clean up possible left-overs
    if (outputFile.exists()) {
      outputFile.delete();
    }
    if (testDir.exists()) {
      testDir.delete();
    }
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .create());
    cas = fileSystemXmiConsumer.newCAS();
    if (outputFile.exists())
      throw new AssertionError("file " + outputFile.getAbsolutePath() + " exists");
  }

  @After
  public void tearDown() throws Exception {
    testDir.deleteOnExit();
  }

  @Test
  public void testProcess() throws AnalysisEngineProcessException {
    cas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
    fileSystemXmiConsumer.process(cas);
    Assert.assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
        outputFile.exists());
    outputFile.delete();
  }

  @Test
  public void testMissingRawSofaURI() throws AnalysisEngineProcessException {
    cas.setDocumentText("test");
    fileSystemXmiConsumer.process(cas);
    File altFile = new File(testDir, "doc-000001.xmi");
    Assert.assertTrue("file " + altFile.getAbsolutePath() + " does not exist", altFile.exists());
    Assert.assertTrue(altFile.delete());
  }

  @Test
  public void testFormatXmi() throws UnsupportedEncodingException, IOException, UIMAException {
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .doNotFormatXML().create());
    cas = fileSystemXmiConsumer.newCAS();
    cas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
    fileSystemXmiConsumer.process(cas);
    Assert.assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
        outputFile.exists());
    final String xml = IOUtils.read(new FileInputStream(outputFile), "UTF-8");
    Assert.assertTrue(xml, xml.indexOf("<?xml version=\"1.1\"") == 0);
    Assert.assertTrue(xml.indexOf("encoding=\"UTF-8\"") > -1);
    Assert.assertTrue(xml.indexOf("\n") == -1);
    outputFile.delete();
  }

  @Test
  public void testUseOldXml() throws UnsupportedEncodingException, IOException, UIMAException {
    String enc = System.getProperty("file.encoding");
    if (IOUtils.isMacOSX()) enc = "UTF-8";
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .writeOldXml().create());
    cas.setDocumentText("test");
    File altFile = new File(testDir, "doc-000001.xmi");
    Assert.assertFalse("file " + altFile.getAbsolutePath() + " exists", altFile.exists());
    fileSystemXmiConsumer.process(cas);
    final String xml = IOUtils.read(new FileInputStream(altFile), enc);
    Assert.assertTrue(xml, xml.indexOf("<?xml version=\"1.0\"") == 0);
    final Pattern regex = Pattern.compile("encoding *= *\"(.*?)\"");
    final Matcher match = regex.matcher(xml);
    Assert.assertTrue(xml, match.find());
    Assert.assertTrue(enc + " != " + match.group(1), xml.indexOf("encoding=\"" + enc + "\"") > -1);
    Assert.assertTrue(altFile.delete());
  }

  @Test
  public void testOverwriteFiles() throws IOException, UIMAException {
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .overwriteFiles().create());
    cas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
    Assert.assertTrue(outputFile.createNewFile());
    fileSystemXmiConsumer.process(cas);
    fileSystemXmiConsumer.process(cas);
    Assert.assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
        outputFile.exists());
    final File altFile = new File(testDir, "raw.2.xmi");
    Assert.assertFalse("file " + altFile.getAbsolutePath() + " exists", altFile.exists());
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .create());
    fileSystemXmiConsumer.process(cas);
    Assert.assertTrue("file " + outputFile.getAbsolutePath() + " does not exist",
        outputFile.exists());
    Assert.assertTrue("file " + altFile.getAbsolutePath() + " does not exist", altFile.exists());
    outputFile.delete();
    Assert.assertTrue(altFile.delete());
  }

  @Test
  public void testEncoding() throws UnsupportedEncodingException, IOException, UIMAException {
    fileSystemXmiConsumer = AnalysisEngineFactory.createPrimitive(XmiWriter.configure(testDir)
        .setEncoding("UTF-16").create());
    cas.setSofaDataURI("file:/tmp/raw.ext", "text/plain");
    fileSystemXmiConsumer.process(cas);
    final String xml = IOUtils.read(new FileInputStream(outputFile), "UTF-16");
    Assert.assertTrue(xml, xml.indexOf("<?xml version=\"1.1\" encoding=\"UTF-16\"") == 0);
    outputFile.delete();
  }
}
