/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.impl.ChildUimaContext_impl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.testing.util.DisableLogging;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;

/**
 * @author Florian Leitner
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ GeniaTaggerAnnotator.class, GeniaTagger.class, ChildUimaContext_impl.class,
    AnalysisEngineFactory.class })
public class TestGeniaTaggerAnnotator {
  @Test
  public void testConfigure() throws Exception {
    DisableLogging.enableLogging(java.util.logging.Level.SEVERE);
    PowerMock.mockStaticPartial(AnalysisEngineFactory.class, "createPrimitiveDescription");
    AnalysisEngineDescription mock = PowerMock.createMock(AnalysisEngineDescription.class);
    EasyMock.expect(AnalysisEngineFactory.createPrimitiveDescription(GeniaTaggerAnnotator.class))
        .andReturn(mock);
    PowerMock.replay(AnalysisEngineFactory.class);
    Assert.assertEquals(mock, GeniaTaggerAnnotator.configure());
    PowerMock.verify(AnalysisEngineFactory.class);
  }

  @Test
  public void testConfigureWithPath() throws Exception {
    DisableLogging.enableLogging(java.util.logging.Level.SEVERE);
    File dummy = File.createTempFile("dummy", "file");
    PowerMock.mockStaticPartial(AnalysisEngineFactory.class, "createPrimitiveDescription");
    AnalysisEngineDescription mock = PowerMock.createMock(AnalysisEngineDescription.class);
    EasyMock.expect(
        AnalysisEngineFactory.createPrimitiveDescription(GeniaTaggerAnnotator.class,
            GeniaTaggerAnnotator.PARAM_DICTIONARIES_PATH, dummy.getCanonicalPath())).andReturn(
        mock);
    PowerMock.replay(AnalysisEngineFactory.class);
    Assert.assertEquals(mock, GeniaTaggerAnnotator.configure(dummy));
    PowerMock.verify(AnalysisEngineFactory.class);
  }

  @Test
  public void testIntialize() throws Exception {
    DisableLogging.enableLogging(java.util.logging.Level.SEVERE);
    File dictionariesPath = File.createTempFile("geniatagger.", "dir").getParentFile();
    Logger logger = PowerMock.createMock(Logger.class);
    GeniaTagger tagger = PowerMock.createMock(GeniaTagger.class);
    mockTagger(tagger, dictionariesPath, logger);
    PowerMock.replay(tagger, GeniaTagger.class);
    PowerMock.replay(logger, Logger.class);
    AnalysisEngineDescription description = GeniaTaggerAnnotator.configure(dictionariesPath);
    AnalysisEngineFactory.createAnalysisEngine(description, Views.CONTENT_TEXT.toString());
    PowerMock.verify(tagger, GeniaTagger.class);
    PowerMock.verify(logger, Logger.class);
  }

  @Test
  public void testProcess() throws Exception {
    DisableLogging.enableLogging(java.util.logging.Level.SEVERE);
    String sentence = "dummy phrase.";
    File dictionariesPath = File.createTempFile("geniatagger.", "dir").getParentFile();
    Logger logger = PowerMock.createMock(Logger.class);
    GeniaTagger tagger = PowerMock.createMock(GeniaTagger.class);
    List<Token> result = new ArrayList<Token>();
    result.add(new Token("dummy\tSTEM\tpos1\tB-chunk\tner1"));
    result.add(new Token("phrase\tstem\tpos2\tI-chunk\tner2"));
    result.add(new Token(".\tStem\tpos3\tO\tner3"));
    mockTagger(tagger, dictionariesPath, logger);
    mockProcess(sentence, result, tagger, logger);
    // replay!
    PowerMock.replay(tagger, GeniaTagger.class);
    PowerMock.replay(logger, Logger.class);
    AnalysisEngineDescription description = GeniaTaggerAnnotator.configure(dictionariesPath);
    AnalysisEngine geniaAE = AnalysisEngineFactory.createAnalysisEngine(description,
        Views.CONTENT_TEXT.toString());
    AnalysisEngine sentenceAE = AnalysisEngineFactory.createPrimitive(SentenceAnnotator
        .configure());
    JCas baseJCas = sentenceAE.newJCas();
    JCas textJCas = baseJCas.createView(Views.CONTENT_TEXT.toString());
    textJCas.setDocumentText(sentence);
    sentenceAE.process(baseJCas);
    geniaAE.process(baseJCas);
    PowerMock.verify(tagger, GeniaTagger.class);
    PowerMock.verify(logger, Logger.class);
  }

  private void mockTagger(GeniaTagger tagger, File dictionariesPath, Logger logger)
      throws Exception, IOException {
    PowerMock.stub(PowerMock.method(ChildUimaContext_impl.class, "getLogger")).toReturn(logger);
    PowerMock.expectNew(GeniaTagger.class, dictionariesPath.getCanonicalPath(), logger).andReturn(
        tagger);
    logger.logrb(Level.CONFIG,
        "org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl", "initialize",
        "org.apache.uima.impl.log_messages", "UIMA_analysis_engine_init_begin__CONFIG",
        "txtfnnl.uima.analysis_component.GeniaTaggerAnnotator");
    logger.log(Level.INFO, "initialized GENIA tagger");
    logger.logrb(Level.CONFIG,
        "org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl", "initialize",
        "org.apache.uima.impl.log_messages", "UIMA_analysis_engine_init_successful__CONFIG",
        "txtfnnl.uima.analysis_component.GeniaTaggerAnnotator");
  }

  private void mockProcess(String sentence, List<Token> result, GeniaTagger tagger, Logger logger)
      throws IOException {
    logger.logrb(Level.FINE, "org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl",
        "process", "org.apache.uima.impl.log_messages",
        "UIMA_analysis_engine_process_begin__FINE",
        "txtfnnl.uima.analysis_component.GeniaTaggerAnnotator");
    EasyMock.expect(tagger.process(sentence)).andReturn(result);
    logger.log(Level.FINE, "annotated {0} tokens", result.size());
    logger.logrb(Level.FINE, "org.apache.uima.analysis_engine.impl.PrimitiveAnalysisEngine_impl",
        "process", "org.apache.uima.impl.log_messages", "UIMA_analysis_engine_process_end__FINE",
        "txtfnnl.uima.analysis_component.GeniaTaggerAnnotator");
  }
}
