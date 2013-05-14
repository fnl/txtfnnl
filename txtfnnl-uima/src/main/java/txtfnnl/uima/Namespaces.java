package txtfnnl.uima;

/**
 * The namespace used/annotated by the AEs.
 * <p>
 * This enum provides one unique place to collect all <code><b>txtfnnl</b></code> namespace URLs.
 * 
 * @author Florian Leitner
 */
public enum Namespaces {
  NLP("http://nlp2rdf.lod2.eu/schema/doc/sso/");
  public final String URL;

  Namespaces(String url) {
    URL = url;
  }
}
