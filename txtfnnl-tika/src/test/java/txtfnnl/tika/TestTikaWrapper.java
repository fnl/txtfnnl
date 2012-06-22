package txtfnnl.tika;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

import txtfnnl.utils.IOUtils;

/**
 * @author Florian Leitner
 */
public class TestTikaWrapper {

	/**
	 * Ensure that the current Tika setup works at all.
	 * 
	 * @throws IOException if the TempFile cannot be read.
	 * @throws TikaException if parsing the TempFile fails.
	 */
	@Test
	public void ensureTikaWorks() throws IOException, TikaException {
		File tempFile = File.createTempFile("tika_text", ".txt");
		BufferedWriter out = new BufferedWriter(new FileWriter(tempFile));
		out.write("content");
		out.close();
		String content = new Tika().parseToString(tempFile);
		tempFile.delete();
		Assert.assertEquals("content\n", content);
	}

	/**
	 * Ensure that the test resource files ("test.*") can be read.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 */
	@Test
	public void ensureResourcesCanBeRead() throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(new File(
		    "src/test/resources/test.txt")));
		Assert.assertEquals("content", in.readLine());

		for (String suffix : new String[] { "html", "txt", "xml" }) {
			String fn = "src/test/resources/test." + suffix;
			Assert.assertTrue(fn, new File(fn).canRead());
		}
	}

	/**
	 * Ensure that Tika (ie., the Façade, not the Wrapper) can process the
	 * HTML test resource file.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void ensureTikaHTMLFileParsing() throws IOException, TikaException {
		String content = new Tika().parseToString(new File(
		    "src/test/resources/test.html"));
		Assert.assertEquals("\n    Heading  " + "\n" + "\n    " + "\n    	A"
		                    + "\n    	hyperlink-containing"
		                    + "\n    	paragraph." + "\n    " + "\n" + "\n    "
		                    + "\n    	Yet 	another" + "\n    	"
		                    + "\n    	X X X" + "\n    	italic"
		                    + "\n    	paragraph." + "\n    " + "\n" + "\n    "
		                    + "\n  ", content);
	}

	/**
	 * Test structured HTML file extraction by the Wrapper.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void testCleanHTMLExtraction() throws IOException, TikaException {
		File file = new File("src/test/resources/test.html");
		String content = new TikaWrapper().parseToString(file);
		/* Notice how this is much cleaner compared to the regular output and
		 * even contains additional content: the alt attributes of the images
		 * that the default Tika extractor misses! */
		Assert.assertEquals(
		    "Heading\n\n" + "A hyperlink -containing paragraph.\n\n"
		            + "Yet another secret Γ Xγ X γX italic paragraph.\n\n"
		            + "Mapping hidden alpha", content);
	}

	/**
	 * Ensure that the metadata for the HTML file extraction gets set.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void testCleanHTMLMetadata() throws IOException, TikaException {
		File file = new File("src/test/resources/test.html");
		Metadata metadata = new Metadata();
		InputStream stream = TikaInputStream.get(file, metadata);
		new TikaWrapper().parse(stream, metadata);
		Assert.assertEquals("Title", metadata.get(Metadata.TITLE));
		Assert.assertEquals(String.valueOf(file.length()),
		    metadata.get(Metadata.CONTENT_LENGTH));
		Assert.assertEquals("ISO-8859-1",
		    metadata.get(Metadata.CONTENT_ENCODING));
		Assert.assertEquals("text/html", metadata.get(Metadata.CONTENT_TYPE));
		Assert.assertEquals("test.html",
		    metadata.get(Metadata.RESOURCE_NAME_KEY));
		Assert.assertEquals(5, metadata.size());
	}

	/**
	 * Test structured XML file extraction by the Wrapper.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void testXMLExtraction() throws IOException, TikaException {
		File file = new File("src/test/resources/test.xml");
		String content = new TikaWrapper().parseToString(file);
		Assert.assertEquals(
		    "\n" + "\t\t\t12602495\n" + "\t\t\t\t2003\n" + "\t\t\t\t02\n"
		            + "\t\t\t\t26\n" + "\t\t\n"
		            + "\t\t\t\t\tDrug development and industrial pharmacy\n"
		            + "\t\t\t\n"
		            + "\t\t\t\tEffects of manufacturing process ... \n"
		            + "\t\t\t\t\t  cut \t \n" + "\t\t\t\n"
		            + "\t\t\t\t\t\tHuang\n" + "\t\t\t\t\t\tYe\n"
		            + "\t\t\t\t\t\tY\n" + "\t\t\n" + "\t\t\t\t\t0\n"
		            + "\t\t\t\t\tDelayed-Action Preparations\n" + "\t\t\n"
		            + "\t\t\t\t\tChemistry, Pharmaceutical\n" + "\t\t\t\n"
		            + "\t\t\t\t\tData Interpretation, Statistical\n" + "\t\n"
		            + "\t\t\tppublish\n" + "\t\t\t\t12602495\n"
		            + "\t\t\t\t10.1081/DDC-120016686\n", content);
	}

	/**
	 * Test HTML extraction by the Wrapper has the correct spacing.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void testHTMLTagSpacingExtraction() throws IOException,
	        TikaException {
		File file = new File("src/test/resources/spacing.html");
		String content = new TikaWrapper().parseToString(file);
		FileInputStream fis = new FileInputStream(
		    "src/test/resources/spacing.txt");
		String expected = IOUtils.read(fis, "UTF-8");
		Assert.assertEquals(expected, content + "\n");
	}

	/**
	 * Test real HTML file extraction by the Wrapper.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	 */
	@Test
	public void testHTMLExtraction() throws IOException, TikaException {
		for (String name : new String[] {
		    "20422012",
		    "21106062",
		    "21668996",
		    "21811562" }) {
			File file = new File("src/test/resources/" + name + ".html");
			String content = new TikaWrapper().parseToString(file);
			FileInputStream fis = new FileInputStream("src/test/resources/" +
			                                          name + ".txt");
			String expected = IOUtils.read(fis, "UTF-8");
			Assert.assertEquals(expected, content + "\n");
		}
	}

	/*
	 * Ensure real HTML file extraction by the Wrapper is not more than thrice
	 * as slow as Tika's default mechanism.
	 * 
	 * @throws IOException if the test resource file cannot be read.
	 * @throws TikaException if parsing the file fails.
	@Test
	public void testHTMLExtractionTime() throws IOException, TikaException {
		int numSamples = 10;
		TikaWrapper wrapper = new TikaWrapper();
		Tika tika = new Tika();
		long start = System.nanoTime() / 1000L / 1000L;
		for (int i = 0; i < numSamples; ++i)
			wrapper
			    .parseToString(new File("src/test/resources/21811562.html"));

		long wrapperTime = System.nanoTime() / 1000L / 1000L - start;
		start = System.nanoTime() / 1000L / 1000L;

		for (int i = 0; i < numSamples; ++i)
			tika.parseToString(new File("src/test/resources/21811562.html"));

		long tikaTime = System.nanoTime() / 1000L / 1000L - start;
		Assert.assertTrue("Time Tika: " + tikaTime + " ms" +
		                  "; Time Wrapper: " + wrapperTime + " ms",
		    (float) wrapperTime / (float) tikaTime < 3.0);
	}
     */


}
