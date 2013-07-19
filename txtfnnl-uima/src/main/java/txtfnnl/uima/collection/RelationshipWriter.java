package txtfnnl.uima.collection;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import org.uimafit.descriptor.ConfigurationParameter;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.RelationshipAnnotation;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * A CAS consumer that writes CSV content based on {@link RelationshipAnnotation relationship
 * annotations} with their source {@link SentenceAnnotation sentence} and target
 * {@link SemanticAnnotation entities}. Annotations will be written as "
 * <code>namespace:identifier#confidence</code>" triplets, where the first triplet is created from
 * the relationship annotation itself, while all other triplets are created from the linked
 * entities. If the {@link #PARAM_EVIDENCE_SENTENCES evidence sentences} are requested, instead,
 * the ( {@link #PARAM_REPLACE_NEWLINES newline-free}) sentence(s) where the entities in the
 * relationship are found are extracted in XML format, using ns:id tags from the relationship to
 * enclose the sentence(s) and similar ns:id tags for the annotated entities. If
 * {@link #PARAM_NORMALIZED_ENTITIES normalized entities} are requested, instead of using the
 * directly linked entities to the relationship, any {@link SemanticAnnotation semantic
 * annotations} within the linked entities' spans are used as the relationship entities, possibly
 * delimited by a regular expression that has to match the namespace of these "inner", normalized
 * entities.
 * 
 * @see TextWriter
 * @author Florian Leitner
 */
public class RelationshipWriter extends TextWriter {
  /**
   * If <code>true</code> (the default), line-breaks within evidence sentences will be replaced
   * with white-spaces.
   * <p>
   * Detected line-breaks are Windows (CR-LF) and Unix line-breaks (LF only).
   */
  public static final String PARAM_REPLACE_NEWLINES = "ReplaceNewlines";
  @ConfigurationParameter(name = PARAM_REPLACE_NEWLINES, defaultValue = "true")
  private Boolean replaceNewlines;
  /** Separator to use between namespace, identifier, offset, and text fields (default: TAB). */
  public static final String PARAM_FIELD_SEPARATOR = "FieldSeparator";
  @ConfigurationParameter(name = PARAM_FIELD_SEPARATOR, defaultValue = "\t")
  private String fieldSeparator;
  static final String LINEBREAK = System.getProperty("line.separator");
  /** Extract the evidence sentences for the relationships, too. */
  public static final String PARAM_EVIDENCE_SENTENCES = "EvidenceSentences";
  @ConfigurationParameter(name = PARAM_EVIDENCE_SENTENCES, defaultValue = "false")
  private boolean extractEvidenceSentences;
  /**
   * Extract (normalized) semantic entities <b>within</b> the linked relationship entities instead
   * of the relationship entities themselves.
   */
  public static final String PARAM_NORMALIZED_ENTITIES = "NormalizedEntities";
  @ConfigurationParameter(name = PARAM_NORMALIZED_ENTITIES, defaultValue = "false")
  private boolean normalizedEntities;
  private NumberFormat decimals = null;
  private String spaces;

  public static class Builder extends TextWriter.Builder {
    protected Builder(Class<? extends AnalysisComponent> klass) {
      super(klass);
    }

    public Builder() {
      this(RelationshipWriter.class);
    }

    public Builder maintainNewlines() {
      setOptionalParameter(PARAM_REPLACE_NEWLINES, Boolean.FALSE);
      return this;
    }

    public Builder setFieldSeparator(String sep) {
      setOptionalParameter(PARAM_FIELD_SEPARATOR, sep);
      return this;
    }

    public Builder extractEvidenceSentences() {
      setOptionalParameter(PARAM_EVIDENCE_SENTENCES, Boolean.TRUE);
      return this;
    }

    public Builder extractNormalizedEntities() {
      setOptionalParameter(PARAM_NORMALIZED_ENTITIES, Boolean.TRUE);
      return this;
    }
  }

  /** Configure a {@link RelationshipWriter} description builder. */
  public static Builder configure() {
    return new Builder();
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    decimals = DecimalFormat.getInstance();
    decimals.setMaximumFractionDigits(5);
    decimals.setMinimumFractionDigits(5);
    spaces = (LINEBREAK.length() == 1) ? " " : "  ";
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    JCas textJCas;
    try {
      textJCas = cas.getJCas();
      setStream(textJCas);
    } catch (CASException e1) {
      throw new AnalysisEngineProcessException(e1);
    } catch (final IOException e2) {
      throw new AnalysisEngineProcessException(e2);
    }
    // TODO: allow constraining the RA type
    // final FSMatchConstraint relCons = RelationshipAnnotation.makeConstraint(jcas,
    // relationshipAnnotator, relationshipNamespace, relationshipIdentifier);
    // FSIterator<TOP> relIt = textJCas.createFilteredIterator(textJCas.getJFSIndexRepository()
    // .getAllIndexedFS(RelationshipAnnotation.type), relCons);
    FSIterator<TOP> relIt = textJCas.getJFSIndexRepository().getAllIndexedFS(
        RelationshipAnnotation.type);
    AnnotationIndex<Annotation> idx = textJCas.getAnnotationIndex(SemanticAnnotation.type);
    while (relIt.hasNext()) {
      RelationshipAnnotation rel = (RelationshipAnnotation) relIt.next();
      SentenceAnnotation sentence = (SentenceAnnotation) rel.getSources(0);
      logger.log(Level.FINE, "{0} :: ''{1}''", new Object[] { rel, sentence.getCoveredText() });
      Set<SemanticAnnotation> entities = collectEntities(idx, rel.getTargets());
      try {
        if (extractEvidenceSentences) {
          String evidence = annotateEvidence(sentence, idx, entities);
          write(String.format("<%s:_%s c=\"%s\">", rel.getNamespace(), rel.getIdentifier(),
              decimals.format(rel.getConfidence())));
          write(evidence); // separated "evidence" to first write logs, then the evidence
          write(String.format("</%s:_%s>", rel.getNamespace(), rel.getIdentifier()));
          write(fieldSeparator);
        } else {
          write(String.format("%s:%s#%s", rel.getNamespace(), rel.getIdentifier(),
              decimals.format(rel.getConfidence())));
          writeEntities(entities);
        }
        write(LINEBREAK);
      } catch (final IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    try {
      unsetStream();
    } catch (final IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    logger.log(Level.INFO, "dumped results for {0}", cas.getView(Views.CONTENT_RAW.toString())
        .getSofaDataURI());
  }

  private String annotateEvidence(SentenceAnnotation sentence, AnnotationIndex<Annotation> idx,
      Set<SemanticAnnotation> entities) {
    FSIterator<Annotation> annIt = idx.subiterator(sentence, true, true);
    StringBuilder result = new StringBuilder();
    int offset = sentence.getBegin();
    final int base = sentence.getBegin();
    String s = sentence.getCoveredText();
    if (replaceNewlines) s = s.replace(LINEBREAK, spaces);
    Map<Integer, StringBuilder> closeTags = new HashMap<Integer, StringBuilder>();
    while (annIt.hasNext()) {
      SemanticAnnotation ann = (SemanticAnnotation) annIt.next();
      if (entities.contains(ann))
        offset = annotateEntity(result, ann, s, offset, base, closeTags);
    }
    int last = sentence.getEnd();
    offset = expandResult(result, s, offset, base, last, closeTags);
    return result.toString();
  }

  private int annotateEntity(StringBuilder result, SemanticAnnotation ann, String s, int offset,
      int base, Map<Integer, StringBuilder> closeTags) {
    Integer end = ann.getEnd();
    if (ann.getBegin() > offset)
      offset = expandResult(result, s, offset, base, ann.getBegin(), closeTags);
    result.append(String.format("<%s:_%s c=\"%s\">", ann.getNamespace(), ann.getIdentifier(),
        decimals.format(ann.getConfidence())));
    if (!closeTags.containsKey(end)) closeTags.put(end, new StringBuilder());
    closeTags.get(end).insert(0,
        String.format("</%s:_%s>", ann.getNamespace(), ann.getIdentifier()));
    return offset;
  }

  private int expandResult(StringBuilder result, String s, int offset, final int base, int last,
      Map<Integer, StringBuilder> closeTags) {
    if (closeTags.size() > 0) {
      for (; offset < last; ++offset) {
        if (closeTags.containsKey(offset)) {
          result.append(closeTags.get(offset));
          closeTags.remove(offset);
          if (closeTags.size() == 0) break;
        }
        result.append(s.charAt(offset - base));
      }
    }
    if (offset < last) {
      result.append(s.substring(offset - base, last - base));
      offset = last;
    }
    return offset;
  }

  private void writeEntities(Set<SemanticAnnotation> entities) throws IOException {
    for (SemanticAnnotation ann : entities) {
      write(fieldSeparator);
      write(String.format("%s:%s#%s", ann.getNamespace(), ann.getIdentifier(),
          decimals.format(ann.getConfidence())));
      FSArray props = ann.getProperties();
      if (props != null) {
        for (int i = 0; i < props.size(); i++) {
          write('&');
          Property p = (Property) props.get(i);
          String text = p.getName();
          if (text.contains(" ") || text.contains("\"")) {
            write('"');
            write(text.replace("\"", "\\\""));
            write('"');
          } else {
            write(text);
          }
          write('=');
          write('"');
          text = p.getValue().replace("\"", "\\\"");
          if (replaceNewlines) text = text.replace('\n', ' ');
          write(text);
          write('"');
        }
      }
    }
  }

  private Set<SemanticAnnotation> collectEntities(AnnotationIndex<Annotation> idx,
      FSArray entityArray) {
    Set<SemanticAnnotation> entities = new HashSet<SemanticAnnotation>(entityArray.size());
    for (int i = entityArray.size() - 1; i >= 0; i--) {
      SemanticAnnotation ann = (SemanticAnnotation) entityArray.get(i);
      if (normalizedEntities) {
        logger.log(Level.FINE, "collecting normalizations in ''{0}''", ann.getCoveredText());
        FSIterator<Annotation> subIt = idx.subiterator(ann, true, true);
        String ns = (ann.getNamespace() == null) ? "" : ann.getNamespace();
        while (subIt.hasNext()) {
          SemanticAnnotation inner = (SemanticAnnotation) subIt.next();
          if (ns.equals(inner.getNamespace())) continue;
          // TODO: configure constraints for these inner semantic annotations
          // if (namespacePattern.matcher(ann.getNamespace()).matches()) entities.add(ann);
          entities.add(inner);
        }
      } else {
        entities.add(ann);
      }
    }
    return entities;
  }
}
