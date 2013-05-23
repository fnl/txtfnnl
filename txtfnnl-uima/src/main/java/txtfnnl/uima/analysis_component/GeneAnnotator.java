package txtfnnl.uima.analysis_component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.FSIterator;
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
import txtfnnl.utils.Offset;
import txtfnnl.utils.StringUtils;
import txtfnnl.utils.stringsim.LeitnerLevenshtein;

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
  private LineBasedStringMapResource<String> taxIdMapping;
  /** The name of the property used to set the taxon ID of the matched gene name. */
  public static final String TAX_ID_PROPERTY = "taxId";
  private static final int FIRST_GREEK = LeitnerLevenshtein.GREEK_LOWER[0];
  private static final int LAST_GREEK = LeitnerLevenshtein.GREEK_UPPER[LeitnerLevenshtein.GREEK_UPPER.length - 1];

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
   * @param geneGazetteerResourceDescription a pre-configured {@link GeneGazetteerResource}
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
  protected Map<Offset, Set<String>> matchText(JCas jcas, String text) {
    Map<Offset, Set<String>> matches = super.matchText(jcas, text);
    text = replaceGreekLetters(text);
    if (text != null) {
      Map<Offset, Set<String>> more = super.matchText(jcas, text);
      for (Offset off : more.keySet()) {
        if (matches.containsKey(off)) matches.get(off).addAll(more.get(off));
        else matches.put(off, more.get(off));
      }
    }
    if (taxaAnnotatorUri != null || taxaNamespace != null) {
      Set<String> annotatedTaxa = new HashSet<String>();
      FSIterator<Annotation> iter = jcas.createFilteredIterator(
          SemanticAnnotation.getIterator(jcas),
          SemanticAnnotation.makeConstraint(jcas, taxaAnnotatorUri, taxaNamespace));
      while (iter.hasNext())
        annotatedTaxa.add(((SemanticAnnotation) iter.next()).getIdentifier());
      if (annotatedTaxa.size() > 0) {
        // filter taxa if any have been annotated
        Iterator<Offset> offIter = matches.keySet().iterator();
        while (offIter.hasNext()) {
          Offset off = offIter.next();
          Iterator<String> idIter = matches.get(off).iterator();
          while (idIter.hasNext())
            if (!annotatedTaxa.contains(getTaxId(idIter.next()))) idIter.remove();
          if (matches.get(off).size() == 0) offIter.remove();
        }
      }
    }
    return matches;
  }

  /**
   * Replace the string with all Greek letter replaced with Latin characters.
   * 
   * @param text to replace characters in
   * @return the replaced version or <code>null</code> if no Greek letter was found
   */
  private String replaceGreekLetters(String text) {
    if (text.length() > 0) {
      boolean found = false;
      int[] result = StringUtils.toCodePointArray(text);
      for (int idx = result.length - 1; idx > -1; --idx) {
        if (result[idx] >= FIRST_GREEK && result[idx] <= LAST_GREEK) {
          found = replace(LeitnerLevenshtein.GREEK_LOWER, LeitnerLevenshtein.LATIN_LOWER, result,
              idx) || found;
          found = replace(LeitnerLevenshtein.GREEK_UPPER, LeitnerLevenshtein.LATIN_UPPER, result,
              idx) || found;
        }
      }
      if (found) {
        StringBuilder sb = new StringBuilder();
        for (int cp : result)
          sb.append(Character.toChars(cp));
        return sb.toString();
      }
    }
    return null;
  }

  /**
   * If a char in <code>greek</code> matches the char at <code>target[idx]</code>, replace that
   * char in <code>target</code> with the <code>latin</code> char at the same position as the
   * matching <code>greek</code> char.
   */
  private boolean replace(final int[] greek, final int[] latin, int[] target, int idx) {
    int pos = Arrays.binarySearch(greek, target[idx]);
    if (pos > -1) {
      target[idx] = latin[pos];
      return true;
    } else {
      return false;
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

  private String getTaxId(String id) {
    String tid = ((GnamedGazetteerResource) gazetteer).getTaxId(id);
    if (taxIdMapping != null && taxIdMapping.containsKey(tid)) tid = taxIdMapping.get(tid);
    return tid;
  }
}
