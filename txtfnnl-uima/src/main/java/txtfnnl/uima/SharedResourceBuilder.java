package txtfnnl.uima;

import java.io.IOException;

import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

import org.uimafit.factory.ExternalResourceFactory;

/** A builder for {@link SharedResourceObject SharedResourceObjects}. */
public class SharedResourceBuilder extends
    ConfigurationBuilder<ExternalResourceDescription> {
  private final Class<? extends SharedResourceObject> resourceClass;
  private final String resourceUrl;

  protected SharedResourceBuilder(Class<? extends SharedResourceObject> klass, String url) {
    super();
    if (klass == null) throw new IllegalArgumentException("object class undefined");
    if (url == null) throw new IllegalArgumentException("resource URL undefined");
    resourceClass = klass;
    resourceUrl = url;
  }

  @Override
  public ExternalResourceDescription create() throws ResourceInitializationException {
    try {
      return ExternalResourceFactory.createExternalResourceDescription(resourceClass, resourceUrl,
          makeParameterArray());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }
}
