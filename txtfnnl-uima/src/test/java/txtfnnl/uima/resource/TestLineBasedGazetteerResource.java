package txtfnnl.uima.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
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

  private GazetteerResource newGazetteer(String... names) throws IOException,
      ResourceInitializationException, ResourceAccessException {
    String[] ids = new String[names.length];
    for (int i = 0; i < names.length; ++i)
      ids[i] = Integer.toString(i + 1);
    return newGazetteer(ids, names);
  }

  @Test
  public void testConfiguration() throws ResourceInitializationException {
    builder.idMatching().caseMatching();
    final String config = builder.create().toString();
    assertTrue(config.contains(url));
  }

  @Test
  public void testDefaultSetup() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer("some dummy name");
    for (String id : gr)
      assertEquals("1", id);
    assertTrue(gr.containsKey("1"));
    assertEquals(1, gr.size());
    assertArrayEquals(new String[] { "some dummy name" }, gr.get("1").toArray(new String[1]));
  }

  @Test
  public void testMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer("name");
    Map<Offset, Set<String>> matches = gr.match("a name b");
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      assertEquals(new Offset(2, 2 + "name".length()), o);
      assertArrayEquals(new String[] { "1" }, matches.get(o).toArray(new String[1]));
    }
  }

  @Test
  public void testAmbigousNames() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id1", "id2" }, new String[] { "name",
        "name" });
    Map<Offset, Set<String>> matches = gr.match("name");
    for (Set<String> ids : matches.values()) {
      assertEquals(2, ids.size());
      for (String id : ids)
        if (!("id1".equals(id) || "id2".equals(id))) fail(id);
    }
    assertEquals(1, matches.size());
  }

  @Test
  public void testCaseInsensitiveMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer("name");
    Map<Offset, Set<String>> matches = gr.match("NAME");
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      assertEquals(new Offset(0, "NAME".length()), o);
      assertArrayEquals(new String[] { "1" }, matches.get(o).toArray(new String[1]));
    }
  }

  @Test
  public void testCaseSensitiveMatching() throws UIMAException, IOException {
    builder.caseMatching();
    GazetteerResource gr = newGazetteer("name");
    assertEquals(0, gr.match("NAME").size());
    assertEquals(1, gr.match("name").size());
  }

  @Test
  public void testIdMatching() throws UIMAException, IOException {
    builder.idMatching();
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    assertEquals(1, gr.match("id").size());
    assertEquals(1, gr.match("name").size());
  }

  @Test
  public void testBoundaryMatch() throws UIMAException, IOException {
    builder.boundaryMatch();
    GazetteerResource gr = newGazetteer("naMe");
    Map<Offset, Set<String>> matches = gr.match("xName1");
    assertEquals(1, matches.size());
    for (Offset o : matches.keySet()) {
      assertEquals(new Offset(1, 1 + "name".length()), o);
      assertArrayEquals(new String[] { "1" }, matches.get(o).toArray(new String[1]));
    }
    matches = gr.match("xname1");
    assertEquals(0, matches.size());
    matches = gr.match("naMex");
    assertEquals(0, matches.size());
    matches = gr.match("name");
    assertEquals(1, matches.size());
  }

  @Test
  public void testNoIdMatching() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer(new String[] { "id" }, new String[] { "name" });
    assertEquals(0, gr.match("id").size());
    assertEquals(1, gr.match("name").size());
  }

  @Test
  public void testSeparatorNormalization() throws UIMAException, IOException {
    GazetteerResource gr = newGazetteer("ABC 123");
    assertEquals(1, gr.match("abc-\n123").size());
  }
}
