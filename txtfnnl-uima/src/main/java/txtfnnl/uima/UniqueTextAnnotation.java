package txtfnnl.uima;

import txtfnnl.uima.tcas.TextAnnotation;

/**
 * A utility class to represent a text annotation as a unique instance with respect to a CAS view.
 * 
 * @author Florian Leitner
 */
public final class UniqueTextAnnotation {
  final int begin, end, code;
  final String ns, id, ann;

  public UniqueTextAnnotation(int begin, int end, String namespace, String identifier,
      String annotator) {
    this.begin = begin;
    this.end = end;
    ns = namespace;
    id = identifier;
    ann = annotator;
    code = 17 * (31 + begin) * (31 + end) * (17 + ns.hashCode()) *
        (17 + id.hashCode() * (17 + ann.hashCode()));
  }

  public <T extends TextAnnotation> UniqueTextAnnotation(T annotation) {
    this.begin = annotation.getBegin();
    this.end = annotation.getEnd();
    ns = annotation.getNamespace();
    id = annotation.getIdentifier();
    ann = annotation.getAnnotator();
    code = 17 * (31 + begin) * (31 + end) * (17 + ns.hashCode()) *
        (17 + id.hashCode() * (17 + ann.hashCode()));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof UniqueTextAnnotation)) return false;
    UniqueTextAnnotation o = (UniqueTextAnnotation) other;
    if (begin != o.begin || end != o.end) return false;
    if (ns != o.ns) return false;
    if (id != o.id) return false;
    if (ann != o.ann) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return code;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(ann);
    sb.append('$').append(ns);
    sb.append("::").append(id);
    sb.append("@").append(begin);
    sb.append("..").append(end);
    return sb.toString();
  }
}
