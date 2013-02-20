package txtfnnl.uima;

import java.io.IOException;

import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import org.uimafit.factory.CollectionReaderFactory;

/** A builder for {@link CollectionReader CollectionReaders}. */
public class CollectionReaderBuilder extends ConfigurationBuilder<CollectionReaderDescription> {
  private final Class<? extends CollectionReader> readerClass;

  protected CollectionReaderBuilder(Class<? extends CollectionReader> klass) {
    super();
    if (klass == null) throw new IllegalArgumentException("object class undefined");
    readerClass = klass;
  }

  @Override
  public CollectionReaderDescription create() throws ResourceInitializationException {
    try {
      return CollectionReaderFactory.createDescription(readerClass, makeParameterArray());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }
}
