package txtfnnl.uima;

import java.io.IOException;

import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.AnalysisEngineFactory;

/** A builder for {@link AnalysisComponent AnalysisComponents}. */
public class AnalysisComponentBuilder extends ConfigurationBuilder<AnalysisEngineDescription> {
  private final Class<? extends AnalysisComponent> analysisClass;

  protected AnalysisComponentBuilder(Class<? extends AnalysisComponent> klass) {
    super();
    if (klass == null) throw new IllegalArgumentException("object class undefined");
    analysisClass = klass;
  }

  @Override
  public AnalysisEngineDescription create() throws ResourceInitializationException {
    try {
      return AnalysisEngineFactory.createPrimitiveDescription(analysisClass, makeParameterArray());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }
}
