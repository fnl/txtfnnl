package txtfnnl.uima.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.resource.LineBasedGazetteerResource.Builder;
import txtfnnl.utils.Offset;

public class TestLineBasedGazetteerResource {
  public static class DummyAnnotator extends JCasAnnotator_ImplBase {
    static final String GAZETTEER = "GazetteerResource";
    @ExternalResource(key = GAZETTEER, mandatory = true)
    public GazetteerResource gazetteerResource;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {}
  }

  Builder builder;
  String url;
  File resource;

  @Before
  public void setUp() throws Exception {
    DisableLogging.enableLogging(Level.WARNING);
    resource = File.createTempFile("resource_", null);
    url = "file:" + resource.getCanonicalPath();
    resource.deleteOnExit();
    builder = LineBasedGazetteerResource.configure(url);
  }

  private GazetteerResource newGazetteer(String[] ids, String[] names, String sep)
      throws IOException, ResourceInitializationException, ResourceAccessException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(resource));
    assertEquals(ids.length, names.length);
    for (int idx = 0; idx < ids.length; ++idx) {
      writer.append(ids[idx]);
      writer.append(sep);
      writer.append(names[idx]);
      writer.append('\n');
    }
    writer.close();
    AnalysisEngine ae = AnalysisEngineFactory.createPrimitive(DummyAnnotator.class,
        DummyAnnotator.GAZETTEER, builder.create());
    UimaContext ctx = ae.getUimaContext();
    return (GazetteerResource) ctx.getResourceObject(DummyAnnotator.GAZETTEER);
  }

  private GazetteerResource newGazetteer(String[] ids, String[] names) throws IOException,
      ResourceInitializationException, ResourceAccessException {
    return newGazetteer(ids, names, "\t");
  }

  @Test
  public void testConfiguration() throws ResourceInitializationException {
    builder.idMatching().caseMatching().setSeparators("separatorsDummy");
    final String config = builder.create().toString();
    assertTrue(config.contains(url));
    assertTrue(config.contains("separatorsDummy"));
  }

  @Test
  public void testDefaultSetup() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name1" });
    String expected = "name" + GazetteerResource.SEPARATOR + "1";
    for (String key : gr)
      assertEquals(expected, key);
    assertTrue(gr.containsKey(expected));
    assertEquals(1 * 4, gr.size()); // regular, lc, normal, lc+normal
    assertArrayEquals(new String[] { "id" }, gr.get(expected).toArray(new String[1]));
  }

  @Test
  public void testAmbigousNamesSetup() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id1", "id2" }, new String[] { "name",
        "name" });
    String expected = "name";
    for (String key : gr)
      assertEquals(expected, key);
    assertTrue(gr.containsKey(expected));
    assertEquals(1 * 4, gr.size()); // regular, lc, normal, lc+normal
    String[] results = gr.get(expected).toArray(new String[1]);
    Arrays.sort(results);
    assertArrayEquals(new String[] { "id1", "id2" }, results);
  }

  @Test
  public void testMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    Map<Offset, String> matches = gr.match("name");
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      assertEquals(new Offset(0, "name".length()), o);
      assertEquals("name", matches.get(o));
    }
  }

  @Test
  public void testCaseInsensitiveMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    Map<Offset, String> matches = gr.match("NAME");
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      String key = matches.get(o);
      assertEquals(new Offset(0, "name".length()), o);
      assertTrue(key.endsWith("NAME"));
      assertArrayEquals(new String[] { "name" }, gr.resolve(key).toArray(new String[1]));
    }
  }

  @Test
  public void testCaseSensitiveMatching() throws UIMAException, IOException {
    builder.caseMatching();
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    Map<Offset, String> matches = gr.match("NAME");
    assertEquals(0, matches.size());
  }

  @Test
  public void testIdMatching() throws UIMAException, IOException {
    builder.idMatching();
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    Map<Offset, String> matches = gr.match("id");
    assertEquals(1, matches.size());
  }

  @Test
  public void testNoIdMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    Map<Offset, String> matches = gr.match("id");
    assertEquals(0, matches.size());
  }

  @Test
  public void testSeparatorNormalization() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "ABC123" });
    Map<Offset, String> matches = gr.match("X-abc \u2012 123-X");
    String expected = "abc" + GazetteerResource.SEPARATOR + "123";
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      String key = matches.get(o);
      assertEquals(new Offset(1, 2 + expected.length() + 2), o);
      assertEquals(expected, key);
      assertFalse(gr.containsKey(key));
      assertArrayEquals(new String[] { "ABC" + GazetteerResource.SEPARATOR + "123" },
          gr.resolve(key).toArray(new String[1]));
    }
  }
}
