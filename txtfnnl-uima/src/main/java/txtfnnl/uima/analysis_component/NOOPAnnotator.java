package txtfnnl.uima.analysis_component;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.component.CasAnnotator_ImplBase;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.uima.ConfigurationBuilder;

/**
 * A dummy annotator that does nothing at all. (NOOP, as in the "no operation" assembler
 * instruction.)
 * 
 * @author Florian Leitner
 */
public class NOOPAnnotator extends CasAnnotator_ImplBase {
  public static class Builder extends ConfigurationBuilder<AnalysisEngineDescription> {
    public Builder() {
      super();
    }

    @Override
    public AnalysisEngineDescription create() throws ResourceInitializationException {
      return AnalysisEngineFactory.createPrimitiveDescription(NOOPAnnotator.class);
    }
  }

  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void process(CAS aCAS) throws AnalysisEngineProcessException {}
}
