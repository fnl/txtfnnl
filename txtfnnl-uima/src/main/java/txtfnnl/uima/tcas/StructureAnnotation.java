
/* First created by JCasGen Fri Jun 22 11:12:49 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import txtfnnl.uima.Offset;

/**
 * TextAnnotations of text structure (sections, paragraphs, headings,
 * typeface, sub- and superscript, etc.) Updated by JCasGen Fri Jun 22
 * 11:12:49 CEST 2012
 * 
 * @generated
 */
public class StructureAnnotation extends TextAnnotation {

	public StructureAnnotation(JCas jcas, Offset offset) {
		super(jcas, offset);
	}

	/**
	 * @generated
	 * @ordered
	 */
	@SuppressWarnings("hiding")
	public final static int typeIndexID = JCasRegistry
	    .register(StructureAnnotation.class);
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
	 * <!-- begin-user-doc --> Write your own initialization here <!--
	 * end-user-doc -->
	 * 
	 * @generated modifiable
	 */
	private void readObject() {/* default - does nothing empty block */}

}
