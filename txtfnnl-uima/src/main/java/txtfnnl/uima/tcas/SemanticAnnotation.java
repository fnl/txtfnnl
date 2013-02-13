/* First created by JCasGen Fri Jun 22 11:12:49 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.utils.Offset;

/**
 * TextAnnotations of semantic elements (PoS, named entities, etc.). Updated by JCasGen Tue Nov 27
 * 14:20:54 CET 2012 XML source: /Users/fleitner/Workspace
 * /txtfnnl/txtfnnl-uima/src/main/resources/txtfnnl/uima /typeSystemDescriptor.xml
 * 
 * @generated
 */
public class SemanticAnnotation extends TextAnnotation {
  /**
   * Return a specialized filter for this annotation type.
   * 
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @param namespaceStr to filter on
   * @param identifierStr to filter on
   * @return a particular semantic annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri, String namespace,
      String identifier) {
    return TextAnnotation.makeConstraint(SemanticAnnotation.class.getName(), jcas, annotatorUri,
        namespace, identifier);
  }

  /**
   * Return a specialized filter for semantic annotations.
   * 
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @param namespace to filter on
   * @return a particular semantic annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri, String namespace) {
    return SemanticAnnotation.makeConstraint(jcas, annotatorUri, namespace, null);
  }

  /**
   * Return a specialized filter for semantic annotations.
   * 
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @return a particular semantic annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri) {
    return SemanticAnnotation.makeConstraint(jcas, annotatorUri, null);
  }

  /**
   * Return an iterator over the index for this annotation type.
   * 
   * @param jcas providing the index
   * @return an annotation feature structure iterator
   */
  public static FSIterator<Annotation> getIterator(JCas jcas) {
    return jcas.getAnnotationIndex(SemanticAnnotation.type).iterator();
  }

  public SemanticAnnotation(JCas jcas, Offset offset) {
    super(jcas, offset);
    readObject();
  }

  /**
   * @generated
   * @ordered
   */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = JCasRegistry.register(SemanticAnnotation.class);
  /**
   * @generated
   * @ordered
   */
  @SuppressWarnings("hiding")
  public final static int type = typeIndexID;

  /** @generated */
  @Override
  public int getTypeIndexID() {
    return typeIndexID;
  }

  /**
   * Never called. Disable default constructor
   * 
   * @generated
   */
  protected SemanticAnnotation() {/* intentionally empty block */}

  /**
   * Internal - constructor used by generator
   * 
   * @generated
   */
  public SemanticAnnotation(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }

  /** @generated */
  public SemanticAnnotation(JCas jcas) {
    super(jcas);
    readObject();
  }

  /** @generated */
  public SemanticAnnotation(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }

  /**
   * <!-- begin-user-doc --> Write your own initialization here <!-- end-user-doc -->
   * 
   * @generated modifiable
   */
  private void readObject() {/* default - does nothing empty block */}
}
