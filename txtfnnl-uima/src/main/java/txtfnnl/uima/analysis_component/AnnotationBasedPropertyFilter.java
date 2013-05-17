/* Created on May 17, 2013 by Florian Leitner.
 * Copyright 2013. All rights reserved. */
package txtfnnl.uima.analysis_component;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.AnalysisComponentBuilder;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.TextAnnotation;

/**
 * AnnotationBasedPropertyFilter
 * 
 * @author Florian Leitner
 */
public class AnnotationBasedPropertyFilter extends JCasAnnotator_ImplBase {
  // public configuration
  public static final String PARAM_SOURCE_ANNOTATOR_URI = "SourceAnnotatorUri";
  @ConfigurationParameter(name = PARAM_SOURCE_ANNOTATOR_URI,
      description = "The source URI to collect IDs from.")
  private String sourceAnnotatorUri;
  public static final String PARAM_SOURCE_NAMESPACE = "SourceNamespace";
  @ConfigurationParameter(name = PARAM_SOURCE_NAMESPACE,
      description = "The source NS to collect IDs from.")
  private String sourceNamespace;
  public static final String PARAM_TARGET_ANNOTATOR_URI = "TargetAnnotatorUri";
  @ConfigurationParameter(name = PARAM_TARGET_ANNOTATOR_URI,
      description = "The target URI to filter or select based on thier property value.")
  private String targetAnnotatorUri;
  public static final String PARAM_TARGET_NAMESPACE = "TargetNamespace";
  @ConfigurationParameter(name = PARAM_TARGET_NAMESPACE,
      description = "The target NS to filter or select based on thier property value.")
  private String targetNamespace;
  public static final String PARAM_TARGET_PROPERTY = "TargetProperty";
  @ConfigurationParameter(name = PARAM_TARGET_PROPERTY,
      description = "The the propery to filter or select in relation to the found source IDs.")
  private String targetProperty;
  public static final String PARAM_FILTER_ANNOTATIONS = "SelectAnnotations";
  @ConfigurationParameter(name = PARAM_FILTER_ANNOTATIONS,
      description = "Filter (by setting this parameter to true) or select (default) annotations "
          + "based on the property value matches.",
      defaultValue = "false")
  private boolean filterAnnotations;
  protected Logger logger;

  public static class Builder extends AnalysisComponentBuilder {
    protected Builder(Class<? extends AnalysisComponent> klass, String propertyName) {
      super(klass);
      setTargetProperty(propertyName);
    }

    public Builder(String propertyName) {
      this(AnnotationBasedPropertyFilter.class, propertyName);
    }

    /** Limit the annotator URI of the source annotations to collect IDs. */
    public Builder setSourceAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_SOURCE_ANNOTATOR_URI, uri);
      return this;
    }

    /** Limit the namespace of the source annotations to collect IDs. */
    public Builder setSourceNamespace(String ns) {
      setOptionalParameter(PARAM_SOURCE_NAMESPACE, ns);
      return this;
    }

    /** Limit the annotator URI of the target annotations to check for source ID matches. */
    public Builder setTargetAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_TARGET_ANNOTATOR_URI, uri);
      return this;
    }

    /** Limit the namespace of the target annotations to check for source ID matches. */
    public Builder setTargetNamespace(String ns) {
      setOptionalParameter(PARAM_TARGET_NAMESPACE, ns);
      return this;
    }

    /** Set the name of the target property field to check for source ID matches. */
    public Builder setTargetProperty(String propertyName) {
      setRequiredParameter(PARAM_TARGET_PROPERTY, propertyName);
      return this;
    }

    /**
     * Instead of selecting (maintaining) matches, filter (remove) all text annotations where the
     * property value has no match to one of the source IDs.
     */
    public Builder blacklist() {
      setOptionalParameter(PARAM_FILTER_ANNOTATIONS, true);
      return this;
    }
  }
  
  public static Builder configure(String propertyName) {
    return new Builder(propertyName);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, sourceAnnotatorUri,
        sourceNamespace);
    FSIterator<Annotation> iter = jcas.createFilteredIterator(TextAnnotation.getIterator(jcas),
        cons);
    Set<String> ids = new HashSet<String>();
    while (iter.hasNext())
      ids.add(((TextAnnotation) iter.next()).getIdentifier());
    if (ids.size() > 0) {
      List<TextAnnotation> removalBuffer = new LinkedList<TextAnnotation>();
      cons = TextAnnotation.makeConstraint(jcas, targetAnnotatorUri, targetNamespace);
      iter = jcas.createFilteredIterator(TextAnnotation.getIterator(jcas), cons);
      while (iter.hasNext()) {
        TextAnnotation ann = (TextAnnotation) iter.next();
        boolean filter = !filterAnnotations;
        for (int i = ann.getProperties().size() - 1; i > -1; --i) {
          Property prop = ann.getProperties(i);
          if (targetProperty.equals(prop.getName())) {
            if (ids.contains(prop.getValue())) filter = !filter;
            break;
          }
        }
        if (filter) removalBuffer.add(ann);
      }
      for (TextAnnotation ann : removalBuffer)
        ann.removeFromIndexes();
    }
  }
}
