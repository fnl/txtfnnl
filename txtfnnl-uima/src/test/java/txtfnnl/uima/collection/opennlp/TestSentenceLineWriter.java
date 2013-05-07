package txtfnnl.uima.collection.opennlp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.collection.SentenceLineWriter;
import txtfnnl.uima.collection.TextWriter;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.utils.IOUtils;

public class TestSentenceLineWriter {
  private static final String SENTENCE_1 = "  This is\na sentence.  ";
  private static final String SENTENCE_2 = "  This is\nanother one.  ";
  private static final String SEPARATOR = "sentence-separator";

  @Before
  public void setUp() {
    DisableLogging.enableLogging(Level.WARNING);
  }

  @Test
  public void testInitializeUimaContext() throws UIMAException, IOException {
    final AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter
        .configure().create());
    for (final String p : new String[] { TextWriter.PARAM_OUTPUT_DIRECTORY,
        TextWriter.PARAM_ENCODING }) {
      Assert.assertNull("Parameter " + p + " does not default to null.",
          slw.getConfigParameterValue(p));
    }
    for (final String p : new String[] { TextWriter.PARAM_OVERWRITE_FILES }) {
      Assert.assertFalse("Parameter " + p + " does not default to false.",
          (Boolean) slw.getConfigParameterValue(p));
    }
    for (final String p : new String[] { TextWriter.PARAM_PRINT_TO_STDOUT,
        SentenceLineWriter.PARAM_REPLACE_NEWLINES }) {
      Assert.assertTrue("Parameter " + p + " does not default to true.",
          (Boolean) slw.getConfigParameterValue(p));
    }
  }

  @Test
  public void testProcessJCasToStdout() throws UIMAException, IOException {
    final AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter
        .configure().create());
    final ByteArrayOutputStream outputStream = processHelper(slw);
    final String result = SENTENCE_1.replace('\n', ' ').trim() +
        System.getProperty("line.separator") + SEPARATOR + System.getProperty("line.separator") +
        SENTENCE_2.replace('\n', ' ').trim() + System.getProperty("line.separator");
    Assert.assertEquals(result, outputStream.toString());
  }

  @Test
  public void testProcessJCasDisableJoinLines() throws UIMAException, IOException {
    final AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter
        .configure().maintainNewlines().create());
    final String result = SENTENCE_1.trim() + System.getProperty("line.separator") + "  " +
        SEPARATOR + "  " + System.getProperty("line.separator") + SENTENCE_2.trim() +
        System.getProperty("line.separator");
    final ByteArrayOutputStream outputStream = processHelper(slw);
    Assert.assertEquals(result, outputStream.toString());
  }

  @Test
  public void testProcessJCasOutputDir() throws UIMAException, IOException {
    final File tmpDir = IOUtils.mkTmpDir();
    final File existing = new File(tmpDir, "test.txt.txt");
    Assert.assertTrue(existing.createNewFile());
    final AnalysisEngine slw = AnalysisEngineFactory.createPrimitive(SentenceLineWriter
        .configure().setOutputDirectory(tmpDir).setEncoding("UTF-32").create());
    final String result = SENTENCE_1.replace('\n', ' ').trim() +
        System.getProperty("line.separator") + SEPARATOR + System.getProperty("line.separator") +
        SENTENCE_2.replace('\n', ' ').trim() + System.getProperty("line.separator");
    processHelper(slw);
    final File created = new File(tmpDir, "test.txt.2.txt");
    final FileInputStream fis = new FileInputStream(created);
    Assert.assertTrue(created.exists());
    Assert.assertEquals(result, IOUtils.read(fis, "UTF-32"));
    existing.delete();
    created.delete();
    tmpDir.delete();
  }

  ByteArrayOutputStream processHelper(AnalysisEngine sentenceLineWriter)
      throws ResourceInitializationException, CASException, AnalysisEngineProcessException {
    final JCas baseCas = sentenceLineWriter.newJCas();
    final JCas rawCas = baseCas.createView(Views.CONTENT_RAW.toString());
    final JCas textCas = baseCas.createView(Views.CONTENT_TEXT.toString());
    rawCas.setSofaDataURI("http://example.com/test.txt", "mime/dummy");
    final String text = SENTENCE_1 + SEPARATOR + SENTENCE_2;
    textCas.setDocumentText(text);
    final SentenceAnnotation a1 = new SentenceAnnotation(textCas);
    final SentenceAnnotation a2 = new SentenceAnnotation(textCas);
    int offset = text.indexOf(SENTENCE_1.trim());
    a1.setBegin(offset);
    a1.setEnd(offset + SENTENCE_1.trim().length());
    a1.setNamespace("namespace");
    a1.setIdentifier("sentence");
    a1.addToIndexes();
    offset = text.indexOf(SENTENCE_2.trim(), offset);
    a2.setBegin(offset);
    a2.setEnd(offset + SENTENCE_2.trim().length());
    a2.setNamespace("namespace");
    a2.setIdentifier("sentence");
    a2.addToIndexes();
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream));
    sentenceLineWriter.process(baseCas);
    return outputStream;
  }
}
