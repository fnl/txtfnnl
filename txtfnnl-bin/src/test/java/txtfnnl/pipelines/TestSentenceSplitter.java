package txtfnnl.pipelines;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;

import org.apache.uima.UIMAException;

import org.uimafit.testing.util.DisableLogging;

public class TestSentenceSplitter {
    @Test
    public void testRunningThePipeline() throws UIMAException, IOException {
        final File inputFile = new File("src/test/resources/pubmed.xml");
        assert inputFile.exists() : "test file does not exist";
        final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        DisableLogging.enableLogging(Level.WARNING);
        SentenceSplitter.main(new String[] { "-E", "UTF-8", inputFile.getPath() });
        final String content = outContent.toString();
        Assert.assertTrue(content.indexOf("studied.\nAs(4)O(6)") > 0);
        System.setOut(null);
    }
}
