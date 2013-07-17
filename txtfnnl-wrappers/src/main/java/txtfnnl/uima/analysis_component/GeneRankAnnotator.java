package txtfnnl.uima.analysis_component;

import ciir.umass.edu.learning.DataPoint;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.util.Level;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.StringMapResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.utils.Offset;

import java.util.*;

/**
 * Created with IntelliJ IDEA. User: fleitner Date: 27/06/2013 Time: 19:05 To change this template
 * use File | Settings | File Templates.
 */
public
class GeneRankAnnotator extends RankedListAnnotator {

  public static final String MODEL_KEY_GENE_SYMBOL_CNT_RESOURCE = "GeneSymbolCountsResource";
  @ExternalResource(key = MODEL_KEY_GENE_SYMBOL_CNT_RESOURCE, mandatory = true)
  private StringMapResource<Map<String, Integer>> geneSymbolCounts;

  public static final String MODEL_KEY_SYMBOL_CNT_RESOURCE = "SymbolCountsResource";
  @ExternalResource(key = MODEL_KEY_SYMBOL_CNT_RESOURCE, mandatory = true)
  private StringMapResource<Integer> symbolCounts;

  public static final String MODEL_KEY_GENE_LINK_CNT_RESOURCE = "GeneLinkCountsResource";
  @ExternalResource(key = MODEL_KEY_GENE_LINK_CNT_RESOURCE, mandatory = true)
  private StringMapResource<Integer> geneLinkCounts;

  public static final String PARAM_TAXA_ANNOTATOR_URI = "TaxaAnnotatorUri";
  @ConfigurationParameter(name = PARAM_TAXA_ANNOTATOR_URI,
                          description = "The annotator URI that made the taxon ID annotations.")
  private String taxaAnnotatorUri;

  public static final String PARAM_TAXA_NAMESPACE = "TaxaNamespace";
  @ConfigurationParameter(name = PARAM_TAXA_NAMESPACE,
                          description = "The NS in which the taxon ID annotations were made.",
                          defaultValue = LinnaeusAnnotator.DEFAULT_NAMESPACE)
  private String taxaNamespace;

  public static final String PARAM_GENE_ANNOTATOR_URI = "GeneAnnotatorUri";
  @ConfigurationParameter(name = PARAM_GENE_ANNOTATOR_URI,
                          description = "The annotator URI that made the gene NER annotations.")
  private String geneAnnotatorUri;

  public static final String PARAM_GENE_NAMESPACE = "GeneNamespace";
  @ConfigurationParameter(name = PARAM_GENE_NAMESPACE,
                          description = "The NS in which the gene NER annotations were made.",
                          defaultValue = GeniaTaggerAnnotator.ENTITY_NAMESPACE)
  private String geneNamespace;

  public static
  class Builder extends RankedListAnnotator.Builder {
    protected
    Builder(Class<? extends AnalysisComponent> klass,
            ExternalResourceDescription rankerResourceDescription) {
      super(klass, rankerResourceDescription);
    }

    /**
     * Create a new configuration builder; Note that the counter descriptors are mandatory values
     * and should be added before building the configuration.
     */
    public
    Builder(ExternalResourceDescription rankerResourceDescription) {
      this(GeneRankAnnotator.class, rankerResourceDescription);
    }

    /**
     * Add the required .tsv file containing three columns: a gene ID, its symbol, and the count for
     * that particular pair.
     */
    public
    Builder setGeneSymbolCounts(ExternalResourceDescription res) {
      setRequiredParameter(MODEL_KEY_GENE_SYMBOL_CNT_RESOURCE, res);
      return this;
    }

    /** Add the required .tsv file containing two columns: a gene symbol and its count. */
    public
    Builder setSymbolCounts(ExternalResourceDescription res) {
      setRequiredParameter(MODEL_KEY_SYMBOL_CNT_RESOURCE, res);
      return this;
    }

    /**
     * Add the required .tsv file containing two columns:a  gene ID and its count of associated
     * reference links.
     */
    public
    Builder setGeneLinkCounts(ExternalResourceDescription res) {
      setRequiredParameter(MODEL_KEY_GENE_LINK_CNT_RESOURCE, res);
      return this;
    }

    /** Set the annotator URI of taxon ID annotations. */
    public
    Builder setTaxaAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_TAXA_ANNOTATOR_URI, uri);
      return this;
    }

    /** Set the namespace of taxon ID annotations. */
    public
    Builder setTaxaNamespace(String ns) {
      setOptionalParameter(PARAM_TAXA_NAMESPACE, ns);
      return this;
    }
    /** Set the annotator URI of gene NER annotations. */
    public
    Builder setGeneAnnotatorUri(String uri) {
      setOptionalParameter(PARAM_GENE_ANNOTATOR_URI, uri);
      return this;
    }

    /** Set the namespace of gene NER annotations. */
    public
    Builder setGeneNamespace(String ns) {
      setOptionalParameter(PARAM_GENE_NAMESPACE, ns);
      return this;
    }
  }

  public static
  Builder configure(ExternalResourceDescription rankerResourceDescription) {
    return new Builder(rankerResourceDescription);
  }

  @Override
  protected
  void process(JCas jcas, List<DataPoint> data) {
    Map<String, Map<String, String>> ranks = new HashMap<String, Map<String, String>>();
    for (DataPoint dp : data) {
      String geneIdName = dp.getDescription().substring(2);
      String geneId = geneIdName.substring(0, geneIdName.indexOf(':'));
      String name = geneIdName.substring(geneIdName.indexOf(':') + 1);
      logger.log(Level.FINER, "ranked geneId=''{0}'' name=''{1}''", new String[] {geneId, name});
      if (!ranks.containsKey(geneId)) {
        Map<String, String> nameRanks = new HashMap<String, String>();
        nameRanks.put(name, String.format("%f", dp.getLabel()));
        ranks.put(geneId, nameRanks);
      } else {
        ranks.get(geneId).put(name, String.format("%f", dp.getLabel()));
      }
    }
    FSIterator<Annotation> it = getAnnotationIterator(jcas);
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      String geneId = ann.getIdentifier();
      String name = ann.getCoveredText();
      Property rank = new Property(jcas);
      rank.setName(RANK_PROPERTY);
      logger.log(Level.FINER, "annotated geneId=''{0}'' name=''{1}''", new String[] {geneId, name});
      rank.setValue(ranks.get(geneId).get(name));
      ann.addProperty(jcas, rank);
    }
  }

  @Override
  protected
  List<DataPoint> getDataList(JCas jcas, String qid) {
    FSIterator<Annotation> it = getAnnotationIterator(jcas);
    List<DataPoint> data = new LinkedList<DataPoint>();
    Map<String, Integer> geneIds = new HashMap<String, Integer>();
    Map<String, Integer> names = new HashMap<String, Integer>();
    Map<String, Integer> geneIdNamePairs = new HashMap<String, Integer>();
    Map<String, Integer> links = new HashMap<String, Integer>();
    Map<String, Integer> symbols = new HashMap<String, Integer>();
    Map<String, Integer> geneIdSymbolPairs = new HashMap<String, Integer>();
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      String geneId = ann.getIdentifier();
      String name = ann.getCoveredText();
      String geneIdName = String.format("%s:%s", geneId, name);
      String symbol = null;
      FSArray props = ann.getProperties();
      for (int i = 0; i < props.size(); ++i) {
        Property p = (Property) props.get(i);
        if (p.getName().equals("name")) symbol = p.getValue();
      }
      String geneIdSymbol = String.format("%s:%s", geneId, symbol);
      if (geneIds.containsKey(geneId)) {
        geneIds.put(geneId, geneIds.get(geneId) + 1);
      } else {
        geneIds.put(geneId, 1);
      }
      if (names.containsKey(name)) {
        names.put(name, names.get(name) + 1);
      } else {
        names.put(name, 1);
      }
      if (geneIdNamePairs.containsKey(geneIdName)) {
        geneIdNamePairs.put(geneIdName, geneIdNamePairs.get(geneIdName) + 1);
      } else {
        geneIdNamePairs.put(geneIdName, 1);
      }
      if (geneIds.containsKey(geneId)) {
        geneIds.put(geneId, geneIds.get(geneId) + 1);
      } else {
        geneIds.put(geneId, 1);
      }
      if (!links.containsKey(geneId)) {
        if (geneLinkCounts.containsKey(geneId)) {
          links.put(geneId, geneLinkCounts.get(geneId));
        } else {
          links.put(geneId, 0);
        }
      }
      if (!symbols.containsKey(symbol)) {
        if (symbolCounts.containsKey(symbol)) {
          symbols.put(symbol, symbolCounts.get(symbol));
        } else {
          symbols.put(symbol, 0);
        }
      }
      if (!geneIdSymbolPairs.containsKey(geneIdSymbol)) {
        if (geneSymbolCounts.containsKey(geneId) &&
            geneSymbolCounts.get(geneId).containsKey(symbol)) {
          geneIdSymbolPairs.put(geneIdSymbol, geneSymbolCounts.get(geneId).get(symbol));
        } else {
          geneIdSymbolPairs.put(geneIdSymbol, 0);
        }
      }
    }
    Map<String, List<Offset>> taxIds = new HashMap<String, List<Offset>>();
    FSIterator<Annotation> taxIt = jcas.createFilteredIterator(
        SemanticAnnotation.getIterator(jcas),
        SemanticAnnotation.makeConstraint(jcas, taxaAnnotatorUri, taxaNamespace)
    );
    while (taxIt.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) taxIt.next();
      String taxId = ann.getIdentifier();
      List<Offset> offsets;
      if (taxIds.containsKey(taxId)) {
        offsets = taxIds.get(taxId);
      } else {
        offsets = new LinkedList<Offset>();
        taxIds.put(taxId, offsets);
      }
      offsets.add(ann.getOffset());
    }
    Map<Offset, String> geneNers = new HashMap<Offset, String>();
    FSIterator<Annotation> geneIt = jcas.createFilteredIterator(
        SemanticAnnotation.getIterator(jcas),
        SemanticAnnotation.makeConstraint(jcas, geneAnnotatorUri, geneNamespace)
    );
    while (geneIt.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) geneIt.next();
      geneNers.put(ann.getOffset(), ann.getIdentifier());
    }
    double normGeneIds = getNorm(geneIds);
    double normNames = getNorm(names);
    double normGeneIdNamePairs = getNorm(geneIdNamePairs);
    double normLinks = getNorm(links);
    double normSymbols = getNorm(symbols);
    double normGeneIdSymbolPairs = getNorm(geneIdSymbolPairs);
    double normTaxIds = getListNorm(taxIds);
    it.moveToFirst();
    while (it.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) it.next();
      String geneId = ann.getIdentifier();
      String name = ann.getCoveredText();
      Offset offset = ann.getOffset();
      String geneIdName = String.format("%s:%s", geneId, name);
      String symbol = null;
      String taxId = null;
      FSArray props = ann.getProperties();
      for (int i = 0; i < props.size(); ++i) {
        Property p = (Property) props.get(i);
        if (p.getName().equals("name")) symbol = p.getValue();
        if (p.getName().equals(GeneAnnotator.TAX_ID_PROPERTY)) taxId = p.getValue();
      }
      int distance = Integer.MAX_VALUE;
      for (Offset taxOff : taxIds.get(taxId)) {
        distance = Math.min(distance, Math.abs(taxOff.start() - offset.end()));
        distance = Math.min(distance, Math.abs(offset.start() - taxOff.end()));
      }
      String geneIdSymbol = String.format("%s:%s", geneId, symbol);
      String entity_type = geneNers.get(offset);
      double[] features = new double[] {
          // 1 no NER
          entity_type == null ? 1.0 : 0.0,
          // 2 cell_line
          "cell_line".equals(entity_type) ? 1.0 : 0.0,
          // 3 cell_type
          "cell_type".equals(entity_type) ? 1.0 : 0.0,
          // 4 DNA
          "DNA".equals(entity_type) ? 1.0 : 0.0,
          // 5 protein
          "protein".equals(entity_type) ? 1.0 : 0.0,
          // 6 RNA
          "RNA".equals(entity_type) ? 1.0 : 0.0,
          // 7 string similarity
          ann.getConfidence(),
          // 8 taxa distance
          1.0 / distance,
          // 9 gene ID count
          geneIds.get(geneId) / normGeneIds,
          // 10 actual name count
          names.get(name) / normNames,
          // 11 actual name, gene ID pair count
          geneIdNamePairs.get(geneIdName) / normGeneIdNamePairs,
          // 12 gene link count
          links.get(geneId) / normLinks,
          // 13 symbol count
          symbols.get(symbol) / normSymbols,
          // 14 gene symbol counts
          geneIdSymbolPairs.get(geneIdSymbol) / normGeneIdSymbolPairs,
          // 15 tax ID counts
          taxIds.get(taxId).size() / normTaxIds
      };
      data.add(makeDataPoint(qid, geneIdName, features));
    }
    return data;
  }

  private
  double getNorm(Map<String, Integer> counts) {
    int norm = 0;
    for (int i : counts.values())
      norm = Math.max(norm, i);
    return (double) norm;
  }

  private <T>
  double getListNorm(Map<String, List<T>> counts) {
    int norm = 0;
    for (List<T> i : counts.values())
      norm = Math.max(norm, i.size());
    return (double) norm;
  }
}
