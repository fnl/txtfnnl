/* First created by JCasGen Fri Jun 22 11:12:49 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.uima.utils.Offset;

/**
 * TextAnnotations of text structure (sections, paragraphs, headings, typeface, sub- and
 * superscript, etc.) Updated by JCasGen Fri Jun 22 11:12:49 CEST 2012
 * 
 * @generated
 */
public class StructureAnnotation extends TextAnnotation {
    /**
     * Return a specialized filter for this annotation type.
     * 
     * @param jcas to create the constraint for
     * @param annoatorUri to filter on
     * @param namespaceStr to filter on
     * @param identifierStr to filter on
     * @return a particular sentence annotation constraint
     */
    public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri,
            String namespace, String identifier) {
        return TextAnnotation.makeConstraint(StructureAnnotation.class.getName(), jcas,
            annotatorUri, namespace, identifier);
    }

    /**
     * Return a specialized filter for sentence annotations.
     * 
     * @param jcas to create the constraint for
     * @param annoatorUri to filter on
     * @param namespace to filter on
     * @return a particular sentence annotation constraint
     */
    public static FSMatchConstraint
            makeConstraint(JCas jcas, String annotatorUri, String namespace) {
        return StructureAnnotation.makeConstraint(jcas, annotatorUri, namespace, null);
    }

    /**
     * Return a specialized filter for sentence annotations.
     * 
     * @param jcas to create the constraint for
     * @param namespace to filter on
     * @return a particular sentence annotation constraint
     */
    public static FSMatchConstraint makeConstraint(JCas jcas, String namespace) {
        return StructureAnnotation.makeConstraint(jcas, null, namespace);
    }

    /**
     * Return an iterator over the index for this annotation type.
     * 
     * @param jcas providing the index
     * @return
     */
    public static FSIterator<Annotation> getIterator(JCas jcas) {
        return jcas.getAnnotationIndex(StructureAnnotation.type).iterator();
    }

    public StructureAnnotation(JCas jcas, Offset offset) {
        super(jcas, offset);
        readObject();
    }

    /**
     * @generated
     * @ordered
     */
    @SuppressWarnings("hiding")
    public final static int typeIndexID = JCasRegistry.register(StructureAnnotation.class);
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
    protected StructureAnnotation() {/* intentionally empty block */}

    /**
     * Internal - constructor used by generator
     * 
     * @generated
     */
    public StructureAnnotation(int addr, TOP_Type type) {
        super(addr, type);
        readObject();
    }

    /** @generated */
    public StructureAnnotation(JCas jcas) {
        super(jcas);
        readObject();
    }

    /** @generated */
    public StructureAnnotation(JCas jcas, int begin, int end) {
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
