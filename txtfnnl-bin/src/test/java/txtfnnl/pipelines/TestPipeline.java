package txtfnnl.pipelines;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import org.easymock.EasyMock;
import org.uimafit.component.CasCollectionReader_ImplBase;
import org.uimafit.factory.CollectionReaderFactory;

import txtfnnl.pipelines.Pipeline.XmlHandler;
import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.tika.uima.TikaExtractor;
import txtfnnl.uima.collection.DirectoryReader;
import txtfnnl.uima.collection.FileReader;
import txtfnnl.uima.collection.TextWriter;

public class TestPipeline {
  @Before
  public void setUp() throws Exception {}

  @Test
  public final void testAddLogAndHelpOptions() {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    for (final String o : new String[] { "h", "help", "i", "info", "q", "quiet", "v", "verbose",
        "R", "recursive", "M", "mime-type" }) {
      Assert.assertNotNull(o, opts.getOption(o));
    }
    Assert.assertNull(opts.getOption("dummy"));
  }

  @Test
  public final void testAddTikaOptions() {
    final Options opts = new Options();
    Pipeline.addTikaOptions(opts);
    for (final String o : new String[] { "e", "input-encoding", "x", "xml-handler", "g",
        "normalize-greek", }) {
      Assert.assertNotNull(o, opts.getOption(o));
    }
    Assert.assertNull(opts.getOption("dummy"));
  }

  @Test
  public final void testGetHandler() throws ParseException {
    final Options opts = new Options();
    Pipeline.addTikaOptions(opts);
    for (final String o : new String[] { "clean", "elsevier", "default" }) {
      final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-x", o });
      Assert.assertNotNull(Pipeline.getTikaXmlHandler(cmd));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testGetHandler_IllegalHandler() throws ParseException, UnsupportedEncodingException {
    final Options opts = new Options();
    Pipeline.addTikaOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-x", "illegal" });
    final ByteArrayOutputStream tmperr = new ByteArrayOutputStream();
    final PrintStream errout = System.err;
    System.setErr(new PrintStream(tmperr, true, "UTF-8")); // redirect STDOUT
    try {
      Pipeline.getTikaXmlHandler(cmd);
    } finally {
      System.setErr(errout);
      String capture = tmperr.toString("UTF-8"); // capture STDOUT
      Assert.assertEquals("no such XML handler: illegal\n", capture);
    }
  }

  @Test
  public final void testQuietLoggingSetup() throws ParseException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-q" });
    final Logger l = Pipeline.loggingSetup(cmd, opts, "USAGE");
    Assert.assertTrue(l.isLoggable(Level.SEVERE));
    Assert.assertFalse(l.isLoggable(Level.WARNING));
    Assert.assertFalse(l.isLoggable(Level.FINE));
    Assert.assertEquals(Pipeline.class.getName(), l.getName());
  }

  @Test
  public final void testVerboseLoggingSetup() throws ParseException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-v" });
    final Logger l = Pipeline.loggingSetup(cmd, opts, "USAGE");
    Assert.assertTrue(l.isLoggable(Level.SEVERE));
    Assert.assertTrue(l.isLoggable(Level.WARNING));
    Assert.assertTrue(l.isLoggable(Level.FINE));
    Assert.assertEquals(Pipeline.class.getName(), l.getName());
  }

  public static class DummyReader extends CasCollectionReader_ImplBase {
    public void getNext(CAS aCAS) throws IOException, CollectionException {}

    public boolean hasNext() throws IOException, CollectionException {
      return false;
    }

    public Progress[] getProgress() {
      return null;
    }

    public static CollectionReaderDescription create() throws ResourceInitializationException {
      return CollectionReaderFactory.createDescription(DummyReader.class);
    }
  }

  @Test
  public final void testPipeline_ReaderInt() throws ResourceInitializationException {
    final CollectionReaderDescription crd = DummyReader.create();
    final CollectionReader cr = CollectionReaderFactory.createCollectionReader(crd);
    final Pipeline p = new Pipeline(0, crd);
    Assert.assertEquals(cr.getClass(), p.getReader().getClass());
    Assert.assertEquals(0, p.size());
  }

  @Test
  public final void testPipeline_Int() {
    final Pipeline p = new Pipeline(0);
    Assert.assertEquals(null, p.getReader());
    Assert.assertEquals(0, p.size());
  }

  @Test(expected = IllegalArgumentException.class)
  public final void testPipeline_NegativeInt() {
    new Pipeline(-1);
  }

  @Test
  public final void testPipeline() {
    final Pipeline p = new Pipeline();
    Assert.assertEquals(null, p.getReader());
    Assert.assertEquals(1, p.size());
  }

  @Test
  public final void testPipeline_Reader() throws ResourceInitializationException {
    final CollectionReaderDescription crd = DummyReader.create();
    final CollectionReader cr = CollectionReaderFactory.createCollectionReader(crd);
    final Pipeline p = new Pipeline(cr);
    Assert.assertEquals(cr, p.getReader());
    Assert.assertEquals(1, p.size());
  }

  @Test
  public final void testPipeline_ReaderEngines() throws ResourceInitializationException {
    final CollectionReader r = CollectionReaderFactory
        .createCollectionReader(DummyReader.create());
    final Pipeline p = new Pipeline(r, new AnalysisEngine[3]);
    Assert.assertEquals(r, p.getReader());
    Assert.assertEquals(2, p.size());
  }

  @Test
  public final void testPipeline_Engines() {
    final Pipeline p = new Pipeline(new AnalysisEngine[1]);
    Assert.assertEquals(0, p.size());
  }

  @Test
  public final void testGetReader() throws ResourceInitializationException {
    final CollectionReader r = CollectionReaderFactory
        .createCollectionReader(DummyReader.create());
    final Pipeline p = new Pipeline(r);
    Assert.assertEquals(r, p.getReader());
  }

  @Test
  public final void testSetReader_Reader() throws ResourceInitializationException {
    final Pipeline p = new Pipeline();
    final CollectionReaderDescription r = DummyReader.create();
    final CollectionReader cr = CollectionReaderFactory.createCollectionReader(r);
    Assert.assertNull(p.setReader(r));
    Assert.assertEquals(cr.getClass(), p.setReader(r).getClass());
  }

  @Test
  public final void testSetReader_CommandLineDefault() throws ParseException, UIMAException,
      IOException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] {});
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.setReader(cmd));
    final CollectionReader r = p.getReader();
    Assert.assertEquals(DirectoryReader.class.getName(), r.getClass().getName());
  }

  @Test
  public final void testSetReader_CommandLineDir() throws ParseException, UIMAException,
      IOException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts,
        new String[] { System.getProperty("user.dir") });
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.setReader(cmd));
    final CollectionReader r = p.getReader();
    Assert.assertEquals(DirectoryReader.class.getName(), r.getClass().getName());
  }

  @Test(expected = IOException.class)
  public final void testSetReader_CommandLineDirMissing() throws ParseException, UIMAException,
      IOException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "a" });
    final Pipeline p = new Pipeline();
    final ByteArrayOutputStream tmperr = new ByteArrayOutputStream();
    final PrintStream stderr = System.err;
    System.setErr(new PrintStream(tmperr, true, "UTF-8"));
    try {
      Assert.assertNull(p.setReader(cmd));
    } finally {
      System.setErr(stderr);
      Assert.assertEquals("cannot read 'a'\n", tmperr.toString("UTF-8"));
    }
  }

  @Test
  public final void testSetReader_CommandLineFile() throws ParseException, UIMAException,
      IOException {
    final File file = File.createTempFile("test_", null);
    file.deleteOnExit();
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { file.getPath() });
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.setReader(cmd));
    final CollectionReader r = p.getReader();
    Assert.assertEquals(FileReader.class.getName(), r.getClass().getName());
  }

  @Test
  public final void testSetReader_CommandLineFiles() throws ParseException, UIMAException,
      IOException {
    final File file = File.createTempFile("test_", null);
    file.deleteOnExit();
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts,
        new String[] { file.getPath(), file.getPath() });
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.setReader(cmd));
    final CollectionReader r = p.getReader();
    Assert.assertEquals(FileReader.class.getName(), r.getClass().getName());
  }

  @Test(expected = IOException.class)
  public final void testSetReader_CommandLineFileMissing() throws ParseException, UIMAException,
      IOException {
    final Options opts = new Options();
    Pipeline.addLogHelpAndInputOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "a", "b" });
    final Pipeline p = new Pipeline();
    final ByteArrayOutputStream tmperr = new ByteArrayOutputStream();
    final PrintStream stderr = System.err;
    System.setErr(new PrintStream(tmperr, true, "UTF-8"));
    try {
      Assert.assertNull(p.setReader(cmd));
    } finally {
      System.setErr(stderr);
      Assert.assertEquals("cannot read 'a'\n", tmperr.toString("UTF-8"));
    }
  }

  @Test
  public final void testSetReader_FilesMime() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpfile = File.createTempFile("test", "pipeline");
    Assert.assertTrue(tmpfile.exists() && tmpfile.canRead());
    Assert.assertNull(p.setReader(new String[] { tmpfile.getAbsolutePath() }, "MIME"));
    Assert.assertEquals(FileReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetReader_Files() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpfile = File.createTempFile("test", "pipeline");
    Assert.assertTrue(tmpfile.exists() && tmpfile.canRead());
    Assert.assertNull(p.setReader(new String[] { tmpfile.getAbsolutePath() }));
    Assert.assertEquals(FileReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetReader_DirMimeRecursive() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpdir = File.createTempFile("test", "pipeline").getParentFile();
    Assert.assertTrue(tmpdir.exists() && tmpdir.canRead());
    Assert.assertNull(p.setReader(tmpdir, "MIME", true));
    Assert.assertEquals(DirectoryReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetReader_DirMime() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpdir = File.createTempFile("test", "pipeline").getParentFile();
    Assert.assertTrue(tmpdir.exists() && tmpdir.canRead());
    Assert.assertNull(p.setReader(tmpdir, "MIME"));
    Assert.assertEquals(DirectoryReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetReader_DirRecursive() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpdir = File.createTempFile("test", "pipeline").getParentFile();
    Assert.assertTrue(tmpdir.exists() && tmpdir.canRead());
    Assert.assertNull(p.setReader(tmpdir, true));
    Assert.assertEquals(DirectoryReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetReader_Dir() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    final File tmpdir = File.createTempFile("test", "pipeline").getParentFile();
    Assert.assertTrue(tmpdir.exists() && tmpdir.canRead());
    Assert.assertNull(p.setReader(tmpdir));
    Assert.assertEquals(DirectoryReader.class, p.getReader().getClass());
  }

  @Test
  public final void testSetConsumer() {
    final Pipeline p = new Pipeline();
    final AnalysisEngine ae = EasyMock.createMock(AnalysisEngine.class);
    Assert.assertNull(p.setConsumer(ae));
    Assert.assertEquals(ae, p.get(p.size()));
  }

  @Test
  public final void testConfigureTika_UsingDefaultValues() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.configureTika(0, true, "encoding", true, XmlHandler.DEFAULT));
    Assert.assertEquals(TikaExtractor.class.getName(), p.get(0).getAnalysisEngineMetaData()
        .getName());
  }

  @Test
  public final void testConfigureTika_UsingTikaAnnotator() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.configureTika(0, false, "encoding", true, XmlHandler.DEFAULT));
    Assert.assertEquals(TikaAnnotator.class.getName(), p.get(0).getAnalysisEngineMetaData()
        .getName());
  }

  @Test
  public final void testConfigureTike_CommandLineDefault() throws ParseException, UIMAException,
      IOException {
    final Options opts = new Options();
    Pipeline.addTikaOptions(opts);
    final CommandLine cmd = (new PosixParser()).parse(opts, new String[] {});
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.configureTika(cmd));
    Assert.assertEquals(TikaExtractor.class.getName(), p.get(0).getAnalysisEngineMetaData()
        .getName());
  }

  @Test(expected = IllegalStateException.class)
  public final void testConfigureTika_OnTooShortPipeline() throws UIMAException, IOException {
    final Pipeline p = new Pipeline(0);
    p.configureTika(0, true, "encoding", true, XmlHandler.DEFAULT);
  }

  @Test(expected = IllegalStateException.class)
  public final void testConfigureTika_AsLastElement() throws UIMAException, IOException {
    final Pipeline p = new Pipeline();
    p.configureTika(1, true, "encoding", true, XmlHandler.DEFAULT);
  }

  @Test
  public final void testConfigureTika_InFirstPosUsingDefaultValues() throws UIMAException,
      IOException {
    final Pipeline p = new Pipeline();
    Assert.assertNull(p.configureTika(true, "encoding", true, XmlHandler.DEFAULT));
    Assert.assertEquals(TikaExtractor.class.getName(), p.get(0).getAnalysisEngineMetaData()
        .getName());
  }

  @Test
  public final void testSetFirst() {
    final Pipeline p = new Pipeline();
    final AnalysisEngine ae = EasyMock.createMock(AnalysisEngine.class);
    Assert.assertNull(p.setFirst(ae));
    Assert.assertEquals(ae, p.get(0));
  }

  @Test
  public final void testSet() {
    final Pipeline p = new Pipeline();
    final AnalysisEngine ae = EasyMock.createMock(AnalysisEngine.class);
    Assert.assertNull(p.set(0, ae));
    Assert.assertEquals(ae, p.get(0));
  }

  @Test
  public final void testGet() {
    final AnalysisEngine ae = EasyMock.createMock(AnalysisEngine.class);
    final Pipeline p = new Pipeline(new AnalysisEngine[] { ae });
    Assert.assertEquals(ae, p.get(0));
  }

  @Test
  public final void testSetEngineArray() {
    final Pipeline p = new Pipeline();
    final AnalysisEngine ae = EasyMock.createMock(AnalysisEngine.class);
    Assert.assertArrayEquals(new AnalysisEngine[2], p.set(new AnalysisEngine[] { ae }));
    Assert.assertEquals(ae, p.get(0));
  }

  @Test
  public final void testSize() {
    Pipeline p = new Pipeline(3);
    Assert.assertEquals(3, p.size());
    p = new Pipeline();
    Assert.assertEquals(1, p.size());
  }

  @Test
  public final void testIsReady() {
    final Pipeline p = new Pipeline(EasyMock.createMock(CollectionReader.class),
        EasyMock.createMock(AnalysisEngine.class));
    Assert.assertTrue(p.isReady());
    p.set(0, null);
    Assert.assertFalse(p.isReady());
  }

  @Test
  public final void testRun() throws IOException, UIMAException {
    // make a tmpfile
    final File tmp = File.createTempFile("test_", "txt");
    tmp.deleteOnExit();
    final BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
    bw.write("this is a test");
    bw.close();
    // setup to capture STDOUT
    final ByteArrayOutputStream tmpout = new ByteArrayOutputStream();
    final PrintStream stdout = System.out;
    String result = null;
    // super-simple pipeline:
    final Pipeline p = new Pipeline();
    p.setReader(new String[] { tmp.getAbsolutePath() });
    p.configureTika();
    p.setConsumer(Pipeline.textEngine(TextWriter.configure().printToStdout().create()));
    try {
      System.setOut(new PrintStream(tmpout, true, "UTF-8")); // redirect STDOUT
      p.run();
      result = tmpout.toString("UTF-8"); // capture STDOUT
    } finally {
      System.setOut(stdout);
      Assert.assertEquals("this is a test", result);
    }
  }
}
