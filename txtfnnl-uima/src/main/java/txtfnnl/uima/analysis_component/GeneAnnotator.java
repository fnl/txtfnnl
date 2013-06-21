package txtfnnl.uima.analysis_component;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;

import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.GnamedGazetteerResource;
import txtfnnl.uima.resource.LineBasedStringMapResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.TextAnnotation;
import txtfnnl.utils.Offset;

/**
 * A system to create normalized (mapped) annotations of gene names. This AE requires a
 * {@link GnamedGazetteerResource} with gene <i>names</i> mapped to their <i>gene <b>and</b> taxon
 * IDs</i>.
 * <p>
 * The taxon ID is set as a property with the name defined by {@link GeneAnnotator#TAX_ID_PROPERTY}
 * . Furthermore, all Greek letters are replaced with their Latin equivalents to ensure alternative
 * spellings are matched, too. Other than that, this annotator works just as any other
 * {@link GazetteerAnnotator}.
 * 
 * @see GazetteerAnnotator
 * @author Florian Leitner
 */
public class GeneAnnotator extends GazetteerAnnotator {
  /** The URI of this Annotator (namespace and ID are defined dynamically). */
  @SuppressWarnings("hiding")
  public static final String URI = GeneAnnotator.class.getName();
  public static final String PARAM_TAXA_ANNOTATOR_URI = "TaxaAnnotatorUri";
  @ConfigurationParameter(name = PARAM_TAXA_ANNOTATOR_URI,
      description = "The annotator URI that made the taxon ID annotations.")
  private String taxaAnnotatorUri;
  public static final String PARAM_TAXA_NAMESPACE = "TaxaNamespace";
  @ConfigurationParameter(name = PARAM_TAXA_NAMESPACE,
      description = "The NS in which the taxon ID annotations were made.")
  private String taxaNamespace;
  /** A mapping of taxonmic IDs to another. */
  public static final String MODEL_KEY_TAX_ID_MAPPING_RESOURCE = "TaxIdMappingResource";
  @ExternalResource(key = MODEL_KEY_TAX_ID_MAPPING_RESOURCE, mandatory = false)
  LineBasedStringMapResource<String> taxIdMapping;
  /** The name of the property used to set the taxon ID of the matched gene name. */
  public static final String TAX_ID_PROPERTY = "taxon";

  public static class Builder extends GazetteerAnnotator.Builder {
    Builder(String entityNamespace, ExternalResourceDescription geneGazetteerResourceDescription) {
      super(GeneAnnotator.class, entityNamespace, geneGazetteerResourceDescription);
    }

    /** Set the annotator URI of taxa annotations to use for filtering annotations. */
    public Builder setTaxaAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_TAXA_ANNOTATOR_URI, uri);
      return this;
    }

    /** Set the namespace of taxa annotations to use for filtering annotations. */
    public Builder setTaxaNamespace(String ns) {
      setOptionalParameter(PARAM_TAXA_NAMESPACE, ns);
      return this;
    }

    /**
     * Supply a {@link LineBasedStringMapResource} that maps taxonomic IDs to another.
     * <p>
     * If set, all Tax IDs with a matching key in this resource will instead be annotated with the
     * mapped target Tax ID.
     */
    public Builder setTaxIdMappingResource(ExternalResourceDescription desc) {
      setOptionalParameter(MODEL_KEY_TAX_ID_MAPPING_RESOURCE, desc);
      return this;
    }
  }

  /**
   * Create a new gazetteer configuration builder with a pre-configured gazetteer resource.
   * 
   * @param entityNamespace to use for the {@link SemanticAnnotation SemanticAnnotations} of the
   *        entity DB IDs
   * @param geneGazetteerResourceDescription a pre-configured {@link GnamedGazetteerResource}
   *        description.
   */
  public static Builder configure(String entityNamespace,
      ExternalResourceDescription geneGazetteerResourceDescription) {
    return new Builder(entityNamespace, geneGazetteerResourceDescription);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    if (taxIdMapping != null && taxIdMapping.size() > 0)
      logger.log(Level.CONFIG, "{0} TaxID mappings provided to {1} Gazetteer", new Object[] {
          taxIdMapping.size(), entityNamespace });
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<SemanticAnnotation> buffer = new LinkedList<SemanticAnnotation>();
    Set<String> annotatedTaxa = getAnnotatedTaxa(jcas);
    if (textNamespace == null && textIdentifier == null) {
      String docText = jcas.getDocumentText();
      Map<Offset, List<String>> matches = gazetteer.match(docText);
      for (Offset offset : matches.keySet()) {
        String match = docText.substring(offset.start(), offset.end());
        if (filter.process(match))
          taxonFilter(jcas, buffer, match, offset, matches.get(offset), annotatedTaxa);
      }
    } else {
      FSMatchConstraint cons = TextAnnotation.makeConstraint(jcas, null, textNamespace,
          textIdentifier);
      FSIterator<Annotation> it = TextAnnotation.getIterator(jcas);
      it = jcas.createFilteredIterator(it, cons);
      while (it.hasNext()) {
        // findEntities -> annotateEntities
        Annotation ann = it.next();
        String text = ann.getCoveredText();
        Map<Offset, List<String>> matches = gazetteer.match(text);
        int annBegin = ann.getBegin();
        for (Offset offset : matches.keySet()) {
          String match = text.substring(offset.start(), offset.end());
          if (filter.process(match))
            taxonFilter(jcas, buffer, match, new Offset(annBegin + offset.start(), annBegin +
                offset.end()), matches.get(offset), annotatedTaxa);
        }
      }
    }
    for (SemanticAnnotation ann : buffer)
      ann.addToIndexes();
  }

  /** Annotate the match if the taxon matches or if there is no taxon filter in use. */
  private void taxonFilter(JCas jcas, List<SemanticAnnotation> buffer, String match,
      Offset offset, List<String> ids, Set<String> annotatedTaxa) {
    for (String id : ids) {
      if (annotatedTaxa == null || annotatedTaxa.contains(getTaxId(id))) {
        SemanticAnnotation ann = makeAnnotation(jcas, match, id, offset);
        if (ann != null) buffer.add(ann);
      }
    }
  }

  /** Expands the parent method, adding a taxon ID property to the annotation. */
  @Override
  protected SemanticAnnotation annotate(String id, JCas jcas, Offset offset, double confidence) {
    SemanticAnnotation entity = super.annotate(id, jcas, offset, confidence);
    entity.setAnnotator(URI); // update with static URI
    Property taxId = new Property(jcas);
    taxId.setName(TAX_ID_PROPERTY);
    taxId.setValue(getTaxId(id));
    FSArray a = new FSArray(jcas, 1);
    a.set(0, taxId);
    entity.setProperties(a);
    return entity;
  }

  /** Get all annotated taxa on this SOFA. */
  private Set<String> getAnnotatedTaxa(JCas jcas) {
    Set<String> annotatedTaxa = null;
    if (taxaAnnotatorUri != null || taxaNamespace != null) {
      annotatedTaxa = new HashSet<String>();
      FSIterator<Annotation> iter = jcas.createFilteredIterator(
          SemanticAnnotation.getIterator(jcas),
          SemanticAnnotation.makeConstraint(jcas, taxaAnnotatorUri, taxaNamespace));
      while (iter.hasNext())
        annotatedTaxa.add(((SemanticAnnotation) iter.next()).getIdentifier());
      for (String taxId : annotatedTaxa)
        logger.log(Level.FINER, "(taxon-filtering) detected taxId={0}", taxId);
      if (annotatedTaxa.size() == 0) annotatedTaxa = null;
    }
    return annotatedTaxa;
  }

  /** Fetches the taxon from the {@link GnamedGazetteerResource gnamed gazetteer}. */
  private String getTaxId(String id) {
    String tid = ((GnamedGazetteerResource) gazetteer).getTaxId(id);
    if (taxIdMapping != null && taxIdMapping.containsKey(tid)) tid = taxIdMapping.get(tid);
    return tid;
  }
}
