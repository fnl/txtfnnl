package txtfnnl.pipelines;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.impl.CollectionReaderDescription_impl;

import org.easymock.EasyMock;

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
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		for (String o : new String[] {
		    "h",
		    "help",
		    "i",
		    "info",
		    "q",
		    "quiet",
		    "v",
		    "verbose",
		    "R",
		    "recursive",
		    "M",
		    "mime-type" }) {
			assertNotNull(o, opts.getOption(o));
		}
		assertNull(opts.getOption("dummy"));
	}

	@Test
	public final void testAddTikaOptions() {
		Options opts = new Options();
		Pipeline.addTikaOptions(opts);
		for (String o : new String[] {
		    "e",
		    "input-encoding",
		    "x",
		    "xml-handler",
		    "g",
		    "normalize-greek", }) {
			assertNotNull(o, opts.getOption(o));
		}
		assertNull(opts.getOption("dummy"));
	}

	@Test
	public final void testGetHandler() throws ParseException {
		Options opts = new Options();
		Pipeline.addTikaOptions(opts);

		for (String o : new String[] { "clean", "elsevier", "default" }) {
			CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-x", o });
			assertNotNull(Pipeline.getTikaXmlHandler(cmd));
		}
	}

	@Test(expected = IllegalStateException.class)
	public final void testGetHandler_IllegalHandler() throws ParseException {
		Options opts = new Options();
		Pipeline.addTikaOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-x", "dummy" });
		Pipeline.getTikaXmlHandler(cmd);
	}

	@Test
	public final void testLoggingSetup() throws ParseException {
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "-q" });
		Logger l = Pipeline.loggingSetup(cmd, opts, "USAGE");
		assertTrue(l.isLoggable(Level.SEVERE));
		assertFalse(l.isLoggable(Level.WARNING));
		assertEquals(Pipeline.class.getName(), l.getName());
	}

	@Test
	public final void testPipeline_ReaderInt() {
		CollectionReaderDescription r = new CollectionReaderDescription_impl();
		Pipeline p = new Pipeline(r, 0);
		assertEquals(r, p.getReader());
		assertEquals(0, p.size());
	}

	@Test
	public final void testPipeline_Int() {
		Pipeline p = new Pipeline(0);
		assertEquals(null, p.getReader());
		assertEquals(0, p.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public final void testPipeline_NegativeInt() {
		new Pipeline(-1);
	}

	@Test
	public final void testPipeline() {
		Pipeline p = new Pipeline();
		assertEquals(null, p.getReader());
		assertEquals(1, p.size());
	}

	@Test
	public final void testPipeline_Reader() {
		CollectionReaderDescription r = new CollectionReaderDescription_impl();
		Pipeline p = new Pipeline(r);
		assertEquals(r, p.getReader());
		assertEquals(1, p.size());
	}

	@Test
	public final void testPipeline_ReaderEngines() {
		CollectionReaderDescription r = new CollectionReaderDescription_impl();
		Pipeline p = new Pipeline(r, new AnalysisEngineDescription[3]);
		assertEquals(r, p.getReader());
		assertEquals(2, p.size());
	}

	@Test
	public final void testPipeline_Engines() {
		Pipeline p = new Pipeline(new AnalysisEngineDescription[1]);
		assertEquals(0, p.size());
	}

	@Test
	public final void testGetReader() {
		CollectionReaderDescription r = new CollectionReaderDescription_impl();
		Pipeline p = new Pipeline(r);
		assertEquals(r, p.getReader());
	}

	@Test
	public final void testSetReader_Reader() {
		Pipeline p = new Pipeline();
		CollectionReaderDescription r = new CollectionReaderDescription_impl();
		assertNull(p.setReader(r));
		assertEquals(r, p.setReader(r));
	}

	@Test
	public final void testSetReader_CommandLineDefault() throws ParseException, UIMAException,
	        IOException {
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] {});
		Pipeline p = new Pipeline();
		assertNull(p.setReader(cmd));
		CollectionReaderDescription r = p.getReader();
		assertEquals(DirectoryReader.class.getName(), r.getImplementationName());
	}

	@Test
	public final void testSetReader_CommandLineDir() throws ParseException, UIMAException,
	        IOException {
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts,
		    new String[] { System.getProperty("user.dir") });
		Pipeline p = new Pipeline();
		assertNull(p.setReader(cmd));
		CollectionReaderDescription r = p.getReader();
		assertEquals(DirectoryReader.class.getName(), r.getImplementationName());
	}

	@Test(expected = IOException.class)
	public final void testSetReader_CommandLineDirMissing() throws ParseException, UIMAException,
	        IOException {
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "a" });
		Pipeline p = new Pipeline();
		ByteArrayOutputStream tmperr = new ByteArrayOutputStream();
		PrintStream stderr = System.err;
		System.setErr(new PrintStream(tmperr, true, "UTF-8"));
		try {
			assertNull(p.setReader(cmd));
		} finally {
			System.setErr(stderr);
			assertEquals("path 'a' not a (readable) directory\n", tmperr.toString("UTF-8"));
		}
	}

	@Test
	public final void testSetReader_CommandLineFile() throws ParseException, UIMAException,
	        IOException {
		File file = File.createTempFile("test_", null);
		file.deleteOnExit();
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] { file.getPath() });
		Pipeline p = new Pipeline();
		assertNull(p.setReader(cmd));
		CollectionReaderDescription r = p.getReader();
		assertEquals(FileReader.class.getName(), r.getImplementationName());
	}

	@Test
	public final void testSetReader_CommandLineFiles() throws ParseException, UIMAException,
	        IOException {
		File file = File.createTempFile("test_", null);
		file.deleteOnExit();
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts,
		    new String[] { file.getPath(), file.getPath() });
		Pipeline p = new Pipeline();
		assertNull(p.setReader(cmd));
		CollectionReaderDescription r = p.getReader();
		assertEquals(FileReader.class.getName(), r.getImplementationName());
	}

	@Test(expected = IOException.class)
	public final void testSetReader_CommandLineFileMissing() throws ParseException, UIMAException,
	        IOException {
		Options opts = new Options();
		Pipeline.addLogHelpAndInputOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] { "a", "b" });
		Pipeline p = new Pipeline();
		ByteArrayOutputStream tmperr = new ByteArrayOutputStream();
		PrintStream stderr = System.err;
		System.setErr(new PrintStream(tmperr, true, "UTF-8"));
		try {
			assertNull(p.setReader(cmd));
		} finally {
			System.setErr(stderr);
			assertEquals("path 'a' not a (readable) file\n", tmperr.toString("UTF-8"));
		}
	}

	@Test
	public final void testSetReader_FilesMime() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new String[] { "file" }, "MIME"));
		assertEquals(FileReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetReader_Files() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new String[] { "file" }));
		assertEquals(FileReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetReader_DirMimeRecursive() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new File("dir"), "MIME", true));
		assertEquals(DirectoryReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetReader_DirMime() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new File("dir"), "MIME"));
		assertEquals(DirectoryReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetReader_DirRecursive() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new File("dir"), true));
		assertEquals(DirectoryReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetReader_Dir() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.setReader(new File("dir")));
		assertEquals(DirectoryReader.class.getName(), p.getReader().getImplementationName());
	}

	@Test
	public final void testSetConsumer() {
		Pipeline p = new Pipeline();
		AnalysisEngineDescription aed = EasyMock.createMock(AnalysisEngineDescription.class);
		assertNull(p.setConsumer(aed));
		assertEquals(aed, p.get(p.size()));
	}

	@Test
	public final void testConfigureTika_UsingDefaultValues() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.configureTika(0, true, "encoding", true, XmlHandler.DEFAULT));
		assertEquals(TikaExtractor.class.getName(), p.get(0).getImplementationName());
	}

	@Test
	public final void testConfigureTika_UsingTikaAnnotator() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		assertNull(p.configureTika(0, false, "encoding", true, XmlHandler.DEFAULT));
		assertEquals(TikaAnnotator.class.getName(), p.get(0).getImplementationName());
	}

	@Test
	public final void testConfigureTike_CommandLineDefault() throws ParseException, UIMAException,
	        IOException {
		Options opts = new Options();
		Pipeline.addTikaOptions(opts);
		CommandLine cmd = (new PosixParser()).parse(opts, new String[] {});
		Pipeline p = new Pipeline();
		assertNull(p.configureTika(cmd));
		assertEquals(TikaExtractor.class.getName(), p.get(0).getImplementationName());
	}

	@Test(expected = IllegalStateException.class)
	public final void testConfigureTika_OnTooShortPipeline() throws UIMAException, IOException {
		Pipeline p = new Pipeline(0);
		p.configureTika(0, true, "encoding", true, XmlHandler.DEFAULT);
	}

	@Test(expected = IllegalStateException.class)
	public final void testConfigureTika_AsLastElement() throws UIMAException, IOException {
		Pipeline p = new Pipeline();
		p.configureTika(1, true, "encoding", true, XmlHandler.DEFAULT);
	}

	@Test
	public final void testConfigureTika_InFirstPosUsingDefaultValues() throws UIMAException,
	        IOException {
		Pipeline p = new Pipeline();
		assertNull(p.configureTika(true, "encoding", true, XmlHandler.DEFAULT));
		assertEquals(TikaExtractor.class.getName(), p.get(0).getImplementationName());
	}

	@Test
	public final void testSetFirst() {
		Pipeline p = new Pipeline();
		AnalysisEngineDescription aed = EasyMock.createMock(AnalysisEngineDescription.class);
		assertNull(p.setFirst(aed));
		assertEquals(aed, p.get(0));
	}

	@Test
	public final void testSet() {
		Pipeline p = new Pipeline();
		AnalysisEngineDescription aed = EasyMock.createMock(AnalysisEngineDescription.class);
		assertNull(p.set(0, aed));
		assertEquals(aed, p.get(0));
	}

	@Test
	public final void testGet() {
		AnalysisEngineDescription aed = EasyMock.createMock(AnalysisEngineDescription.class);
		Pipeline p = new Pipeline(new AnalysisEngineDescription[] { aed });
		assertEquals(aed, p.get(0));
	}

	@Test
	public final void testSetEngineArray() {
		Pipeline p = new Pipeline();
		AnalysisEngineDescription aed = EasyMock.createMock(AnalysisEngineDescription.class);
		assertArrayEquals(new AnalysisEngineDescription[2],
		    p.set(new AnalysisEngineDescription[] { aed }));
		assertEquals(aed, p.get(0));
	}

	@Test
	public final void testSize() {
		Pipeline p = new Pipeline(3);
		assertEquals(3, p.size());
		p = new Pipeline();
		assertEquals(1, p.size());
	}

	@Test
	public final void testIsReady() {
		Pipeline p = new Pipeline(EasyMock.createMock(CollectionReaderDescription.class),
		    EasyMock.createMock(AnalysisEngineDescription.class));
		assertTrue(p.isReady());
		p.set(0, null);
		assertFalse(p.isReady());
	}

	@Test
	public final void testRun() throws IOException, UIMAException {
		// make a tmpfile
		File tmp = File.createTempFile("test_", "txt");
		tmp.deleteOnExit();
		BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
		bw.write("this is a test");
		bw.close();

		// setup to capture STDOUT
		ByteArrayOutputStream tmpout = new ByteArrayOutputStream();
		PrintStream stdout = System.out;
		String result = null;

		// super-simple pipeline:
		Pipeline p = new Pipeline();
		p.setReader(new String[] { tmp.getAbsolutePath() });
		p.configureTika();
		p.setConsumer(TextWriter.configure());

		try {
			System.setOut(new PrintStream(tmpout, true, "UTF-8")); // redirect
			                                                       // STDOUT
			p.run();
			result = tmpout.toString("UTF-8"); // capture STDOUT
		} finally {
			System.setOut(stdout);
			assertEquals("this is a test", result);
		}
	}

}
