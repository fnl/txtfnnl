/* First created by JCasGen Wed Jun 06 13:10:16 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

/**
 * Annotations on an entire SOFA. Updated by JCasGen Tue Nov 27 14:20:54 CET 2012 XML source:
 * /Users
 * /fleitner/Workspace/txtfnnl/txtfnnl-uima/src/main/resources/txtfnnl/uima/typeSystemDescriptor
 * .xml
 * 
 * @generated
 */
public class DocumentAnnotation extends SofaAnnotation {
    /**
     * @generated
     * @ordered
     */
    @SuppressWarnings("hiding")
    public final static int typeIndexID = JCasRegistry.register(DocumentAnnotation.class);
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
    protected DocumentAnnotation() {/* intentionally empty block */}

    /**
     * Internal - constructor used by generator
     * 
     * @generated
     */
    public DocumentAnnotation(int addr, TOP_Type type) {
        super(addr, type);
        readObject();
    }

    /** @generated */
    public DocumentAnnotation(JCas jcas) {
        super(jcas);
        readObject();
    }

    /**
     * <!-- begin-user-doc --> Write your own initialization here <!-- end-user-doc -->
     * 
     * @generated modifiable
     */
    private void readObject() {/*default - does nothing empty block */}
}
