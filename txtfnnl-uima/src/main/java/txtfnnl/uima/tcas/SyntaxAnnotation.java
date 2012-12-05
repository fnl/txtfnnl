

/* First created by JCasGen Fri Jun 22 11:12:49 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.uima.utils.Offset;



/** TextAnnotations of syntactic elements (text segements such as sentences, tokens, etc.).
 * Updated by JCasGen Tue Nov 27 14:20:54 CET 2012
 * XML source: /Users/fleitner/Workspace/txtfnnl/txtfnnl-uima/src/main/resources/txtfnnl/uima/typeSystemDescriptor.xml
 * @generated */
public class SyntaxAnnotation extends TextAnnotation {
	
	/**
	 * Return a specialized filter for this annotation type.
	 * 
	 * @param jcas to create the constraint for
	 * @param annoatorUri to filter on
	 * @param namespaceStr to filter on
	 * @param identifierStr to filter on
	 * @return a particular sentence annotation constraint
	 */
	public static FSMatchConstraint makeConstraint(JCas jcas,
	                                               String annotatorUri,
	                                               String namespace,
	                                               String identifier) {
		return TextAnnotation.makeConstraint(SyntaxAnnotation.class.getName(),
		    jcas, annotatorUri, namespace, identifier);
	}

	/**
	 * Return a specialized filter for sentence annotations.
	 * 
	 * @param jcas to create the constraint for
	 * @param annoatorUri to filter on
	 * @param namespace to filter on
	 * @return a particular sentence annotation constraint
	 */
	public static FSMatchConstraint makeConstraint(JCas jcas,
	                                               String annotatorUri,
	                                               String namespace) {
		return makeConstraint(jcas, annotatorUri, namespace, null);
	}

	/**
	 * Return a specialized filter for sentence annotations.
	 * 
	 * @param jcas to create the constraint for
	 * @param namespace to filter on
	 * @return a particular sentence annotation constraint
	 */
	public static FSMatchConstraint makeConstraint(JCas jcas,
	                                               String namespace) {
		return makeConstraint(jcas, null, namespace);
	}

	/**
	 * Return an iterator over the index for this annotation type.
	 * 
	 * @param jcas providing the index
	 * @return
	 */
	public static FSIterator<Annotation> getIterator(JCas jcas) {
		return jcas.getAnnotationIndex(SyntaxAnnotation.type).iterator();
	}

	public SyntaxAnnotation(JCas jcas, Offset offset) {
		super(jcas, offset);
		readObject();
	}


  /** @generated
   * @ordered 
   */
  @SuppressWarnings ("hiding")
  public final static int typeIndexID = JCasRegistry.register(SyntaxAnnotation.class);
  /** @generated
   * @ordered 
   */
  @SuppressWarnings ("hiding")
  public final static int type = typeIndexID;
  /** @generated  */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected SyntaxAnnotation() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated */
  public SyntaxAnnotation(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public SyntaxAnnotation(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public SyntaxAnnotation(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** <!-- begin-user-doc -->
    * Write your own initialization here
    * <!-- end-user-doc -->
  @generated modifiable */
  private void readObject() {/*default - does nothing empty block */}
     
}

    