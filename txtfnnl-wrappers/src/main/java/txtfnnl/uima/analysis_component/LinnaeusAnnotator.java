package txtfnnl.uima.analysis_component;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import martin.common.ArgParser;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.resource.LineBasedStringMapResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

import uk.ac.man.entitytagger.EntityTagger;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;

/**
 * A wrapper for Linnaeus requiring a Linnaeus properties file for configuration. As no settings
 * are preconfigured, the wrapper can be used to run Linnaeus with any dictionary. The only other
 * required configuration is the namespace to use on the semantic annotations that will be made. As
 * Linnaeus is mainly made for species annotations, a taxonomy namespace is preconfigured as the
 * default namespace.
 * 
 * @author Florian Leitner
 */
public class LinnaeusAnnotator extends JCasAnnotator_ImplBase {
  public static final String URI = LinnaeusAnnotator.class.getName();
  /** The default namespace used by this annotator if no other is given. */
  public static final String DEFAULT_NAMESPACE = "http://www.uniprot.org/taxonomy/";
  public static final String PARAM_ANNOTATION_NAMESPACE = "AnnotationNamespace";
  @ConfigurationParameter(name = PARAM_ANNOTATION_NAMESPACE,
      description = "The namespace to use for the annotations.",
      defaultValue = DEFAULT_NAMESPACE)
  private String namespace;
  public static final String PARAM_CONFIG_FILE_PATH = "ConfigFilePath";
  @ConfigurationParameter(name = PARAM_CONFIG_FILE_PATH,
      description = "A property file path with Linnaeus configuration data.")
  private String configFilePath;
  /**
   * A mapping of the detected IDs to some other ID.
   * <p>
   * This can be used to change IDs in cases where necessary, e.g., to map strain IDs back to their
   * species IDs.
   */
  public static final String MODEL_KEY_ID_MAPPING_RESOURCE = "IdMappingResource";
  @ExternalResource(key = MODEL_KEY_ID_MAPPING_RESOURCE, mandatory = false)
  private LineBasedStringMapResource<String> idMapping;
  private Logger logger;
  private Matcher linnaeus;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass, File configFile) {
      super(klass);
      setConfigurationFilePath(configFile);
    }

    /** Create a new builder with a particular properties file. */
    public Builder(File configFile) {
      this(LinnaeusAnnotator.class, configFile);
    }

    /** Set a different properties file. */
    public Builder setConfigurationFilePath(File configFile) {
      setRequiredParameter(PARAM_CONFIG_FILE_PATH, configFile.getAbsolutePath());
      return this;
    }

    /** Set the namespace to use for the semantic annotations. */
    public Builder setAnnotationNamespace(String namespace) {
      setRequiredParameter(PARAM_ANNOTATION_NAMESPACE, namespace);
      return this;
    }

    /**
     * Supply a {@link LineBasedStringMapResource} that maps Linnaeus IDs to another.
     * <p>
     * If set, all matching keys for an ID reported by Linnaeus will instead be annotated with the
     * mapped target ID. Particularly useful to map IDs such as strain IDs to their species IDs.
     */
    public Builder setIdMappingResource(ExternalResourceDescription idMappingResource) {
      setOptionalParameter(MODEL_KEY_ID_MAPPING_RESOURCE, idMappingResource);
      return this;
    }
  }

  public static Builder configure(File configFile) {
    return new Builder(configFile);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.CONFIG, "reading configuration file for {0} annotations at {1}",
        new String[] { namespace, configFilePath });
    ArgParser ap = new ArgParser(new String[] { "--properties", configFilePath });
    java.util.logging.Logger l = java.util.logging.Logger.getLogger("Linnaeus");
    l.setLevel(java.util.logging.Level.WARNING);
    linnaeus = EntityTagger.getMatcher(ap, l);
    if (idMapping != null)
      logger.log(Level.CONFIG, "loaded {0} ID mappings", idMapping.size());
    else
      logger.log(Level.CONFIG, "no ID mapping configured");
  }

  @Override
  public void process(JCas cas) throws AnalysisEngineProcessException {
    int countMentions = 0;
    Set<String> idSet = new HashSet<String>();
    for (Mention mention : linnaeus.match(cas.getDocumentText())) {
      ++countMentions;
      String[] ids = mention.getIds();
      Double[] probs = mention.getProbabilities();
      for (int i = ids.length - 1; i > -1; --i) {
        idSet.add(ids[i]);
        // Linnaeus sets p to NULL in some cases, so:
        if (probs[i] == null) probs[i] = 1.0 / ((double) probs.length);
        if (idMapping != null && idMapping.containsKey(ids[i])) {
          logger.log(Level.FINE, "mapping taxon {0} to {1}",
              new String[] { ids[i], idMapping.get(ids[i]) });
          annotate(cas, mention, idMapping.get(ids[i]), probs[i]);
        } else {
          annotate(cas, mention, ids[i], probs[i]);
        }
      }
    }
    logger.log(Level.FINE, "tagged {0} {1} mentions with {2} IDs", new Object[] { countMentions,
        namespace, idSet.size() });
  }

  private void annotate(JCas cas, Mention mention, String id, Double prob) {
    SemanticAnnotation ann = new SemanticAnnotation(cas, mention.getStart(), mention.getEnd());
    ann.setAnnotator(URI);
    ann.setConfidence(prob);
    ann.setIdentifier(id);
    ann.setNamespace(namespace);
    ann.addToIndexes();
  }

  @Override
  public void destroy() {
    super.destroy();
    linnaeus = null;
  }
}
