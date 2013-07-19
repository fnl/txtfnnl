package txtfnnl.uima.analysis_component;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;

import java.util.LinkedList;
import java.util.List;

public
class RelationshipAnnotator extends JCasAnnotator_ImplBase {
  /** The URI of this Annotator. */
  public static final String URI = RelationshipAnnotator.class.getName();
  /** The Annotator's logger instance. */
  protected Logger logger;
  public static final String PARAM_SOURCE_URI = "SourceAnnotatorUri";
  @ConfigurationParameter(name = PARAM_SOURCE_URI, description = "source annotator URI to detect", mandatory = false)
  private String srcAnnotatorUri = null;
  public static final String PARAM_SOURCE_NAMESPACE = "SourceNamespace";
  @ConfigurationParameter(name = PARAM_SOURCE_NAMESPACE, description = "source namespace to detect", mandatory = false)
  private String srcNamespace = null;
  public static final String PARAM_SOURCE_IDENTIFIER = "SourceIdentifier";
  @ConfigurationParameter(name = PARAM_SOURCE_IDENTIFIER, description = "source identifier to detect", mandatory = false)
  private String srcIdentifier = null;
  public static final String PARAM_TARGET_URI = "TargetAnnotatorUri";
  @ConfigurationParameter(name = PARAM_TARGET_URI, description = "target annotator URI to detect", mandatory = false)
  private String trgtAnnotatorUri = null;
  public static final String PARAM_TARGET_NAMESPACE = "TargetNamespace";
  @ConfigurationParameter(name = PARAM_TARGET_NAMESPACE, description = "target namespace to detect", mandatory = false)
  private String trgtNamespace = null;
  public static final String PARAM_TARGET_IDENTIFIER = "TargetIdentifier";
  @ConfigurationParameter(name = PARAM_TARGET_IDENTIFIER, description = "target identifier to detect", mandatory = false)
  private String trgtIdentifier = null;
  public static final String PARAM_REL_NAMESPACE = "RelationshipNamespace";
  @ConfigurationParameter(name = PARAM_REL_NAMESPACE, description = "relationship namespace to set", mandatory = false, defaultValue = "event")
  private String relNamespace;
  public static final String PARAM_REL_IDENTIFIER = "RelationshipIdentifier";
  @ConfigurationParameter(name = PARAM_REL_IDENTIFIER, description = "relationship identifier to set", mandatory = false, defaultValue = "relationship")
  private String relIdentifier;

  public static
  class Builder extends AnalysisComponentBuilder {
    protected
    Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public
    Builder() {
      this(RelationshipAnnotator.class);
    }

    public
    Builder setSourceAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_SOURCE_URI, uri);
      return this;
    }

    public
    Builder setSourceNamespace(String ns) {
      setOptionalParameter(PARAM_SOURCE_NAMESPACE, ns);
      return this;
    }

    public
    Builder setSourceIdentifier(String id) {
      setOptionalParameter(PARAM_SOURCE_IDENTIFIER, id);
      return this;
    }

    public
    Builder setTargetAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_TARGET_URI, uri);
      return this;
    }

    public
    Builder setTargetNamespace(String ns) {
      setOptionalParameter(PARAM_TARGET_NAMESPACE, ns);
      return this;
    }

    public
    Builder setTargetIdentifier(String id) {
      setOptionalParameter(PARAM_TARGET_IDENTIFIER, id);
      return this;
    }

    /** default: "event" */
    public
    Builder setRelationshipNamespace(String ns) {
      setOptionalParameter(PARAM_REL_NAMESPACE, ns);
      return this;
    }

    /** default: "relationship" */
    public
    Builder setRelationshipIdentifier(String id) {
      setOptionalParameter(PARAM_REL_IDENTIFIER, id);
      return this;
    }
  }

  public static
  Builder configure() {
    return new Builder();
  }

  @Override
  public
  void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    logger.log(Level.CONFIG, "RelationshipAnnotator initialized");
  }

  @Override
  public
  void process(JCas jcas) throws AnalysisEngineProcessException {
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    AnnotationIndex<Annotation> textIdx = jcas.getAnnotationIndex(TextAnnotation.type);

    while (sentenceIt.hasNext()) {
      final Annotation sentence = sentenceIt.next();
      final FSIterator<Annotation> textIt = textIdx.subiterator(sentence);
      List<TextAnnotation> sources = new LinkedList<TextAnnotation>();
      List<TextAnnotation> targets = new LinkedList<TextAnnotation>();
      while (textIt.hasNext()) {
        TextAnnotation ann = (TextAnnotation) textIt.next();
        if (matches(ann, srcAnnotatorUri, srcIdentifier, srcNamespace)) sources.add(ann);
        if (matches(ann, trgtAnnotatorUri, trgtIdentifier, trgtNamespace)) targets.add(ann);
      }
      if (sources.size() > 0 && targets.size() > 0) {
        RelationshipAnnotation rel = new RelationshipAnnotation(jcas);
        final FSArray groups = new FSArray(jcas, sources.size() + targets.size());
        int i = 0;
        for (TextAnnotation src : sources) groups.set(i++, src);
        for (TextAnnotation trgt : targets) groups.set(i++, trgt);
        final FSArray sentenceContainer = new FSArray(jcas, 1);
        sentenceContainer.set(0, sentence);
        rel.setAnnotator(URI);
        rel.setNamespace(relNamespace);
        rel.setIdentifier(relIdentifier);
        rel.setSources(sentenceContainer);
        rel.setTargets(groups);
        rel.addToIndexes(jcas);
      }
    }
  }

  private
  boolean matches(TextAnnotation ann, String uri, String id_, String ns_) {
    return ((uri == null || uri.equals(ann.getAnnotator())) &&
            (id_ == null || id_.equals(ann.getIdentifier())) &&
            (ns_ == null || ns_.equals(ann.getNamespace())));
  }

  @Override
  public
  void destroy() {
    super.destroy();
    logger.log(Level.CONFIG, "RelationshipAnnotator destroyed");
  }
}