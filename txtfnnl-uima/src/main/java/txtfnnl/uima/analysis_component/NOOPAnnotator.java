package txtfnnl.uima.analysis_component;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;

import org.uimafit.component.CasAnnotator_ImplBase;
import org.uimafit.factory.AnalysisEngineFactory;

/**
 * A dummy annotator that does nothing at all. (NOOP, as in the "no operation" assembler
 * instruction.)
 * 
 * @author Florian Leitner
 */
public class NOOPAnnotator extends CasAnnotator_ImplBase {
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(NOOPAnnotator.class);
  }

  @Override
  public void process(CAS aCAS) throws AnalysisEngineProcessException {}
}
