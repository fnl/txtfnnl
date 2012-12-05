
/* First created by JCasGen Tue Nov 27 14:20:55 CET 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.uima.utils.Offset;

/**
 * A subtype for tokens with additional features for the PoS and chunk tag and
 * the token's stem. Updated by JCasGen Tue Nov 27 14:20:55 CET 2012 XML
 * source:
 * /Users/fleitner/Workspace/txtfnnl/txtfnnl-uima/src/main/resources/txtfnnl
 * /uima/typeSystemDescriptor.xml
 * 
 * @generated
 */
public class TokenAnnotation extends SyntaxAnnotation {

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
		return TextAnnotation.makeConstraint(TokenAnnotation.class.getName(),
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
		return jcas.getAnnotationIndex(TokenAnnotation.type).iterator();
	}

	public TokenAnnotation(JCas jcas, Offset offset) {
		super(jcas, offset);
		readObject();
	}

	/**
	 * @generated
	 * @ordered
	 */
	@SuppressWarnings("hiding")
	public final static int typeIndexID = JCasRegistry
	    .register(TokenAnnotation.class);
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
	protected TokenAnnotation() {/* intentionally empty block */}

	/**
	 * Internal - constructor used by generator
	 * 
	 * @generated
	 */
	public TokenAnnotation(int addr, TOP_Type type) {
		super(addr, type);
		readObject();
	}

	/** @generated */
	public TokenAnnotation(JCas jcas) {
		super(jcas);
		readObject();
	}

	/** @generated */
	public TokenAnnotation(JCas jcas, int begin, int end) {
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

	// *--------------*
	// * Feature: pos

	/**
	 * getter for pos - gets The part-of-speech tag for this token.
	 * 
	 * @generated
	 */
	public String getPos() {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_pos == null)
			jcasType.jcas.throwFeatMissing("pos",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		return jcasType.ll_cas.ll_getStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_pos);
	}

	/**
	 * setter for pos - sets The part-of-speech tag for this token.
	 * 
	 * @generated
	 */
	public void setPos(String v) {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_pos == null)
			jcasType.jcas.throwFeatMissing("pos",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		jcasType.ll_cas.ll_setStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_pos, v);
	}

	// *--------------*
	// * Feature: stem

	/**
	 * getter for stem - gets The stem or lemma of this token.
	 * 
	 * @generated
	 */
	public String getStem() {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_stem == null)
			jcasType.jcas.throwFeatMissing("stem",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		return jcasType.ll_cas.ll_getStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_stem);
	}

	/**
	 * setter for stem - sets The stem or lemma of this token.
	 * 
	 * @generated
	 */
	public void setStem(String v) {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_stem == null)
			jcasType.jcas.throwFeatMissing("stem",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		jcasType.ll_cas.ll_setStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_stem, v);
	}

	// *--------------*
	// * Feature: chunk

	/**
	 * getter for chunk - gets The chunk tag of this token.
	 * 
	 * @generated
	 */
	public String getChunk() {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_chunk == null)
			jcasType.jcas.throwFeatMissing("chunk",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		return jcasType.ll_cas.ll_getStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_chunk);
	}

	/**
	 * setter for chunk - sets The chunk tag of this token.
	 * 
	 * @generated
	 */
	public void setChunk(String v) {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_chunk == null)
			jcasType.jcas.throwFeatMissing("chunk",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		jcasType.ll_cas.ll_setStringValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_chunk, v);
	}

	// *--------------*
	// * Feature: inChunk

	/**
	 * getter for inChunk - gets TRUE if this token is part of a chunk that
	 * started with an earlier token.
	 * 
	 * @generated
	 */
	public boolean getInChunk() {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_inChunk == null)
			jcasType.jcas.throwFeatMissing("inChunk",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		return jcasType.ll_cas.ll_getBooleanValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_inChunk);
	}

	/**
	 * setter for inChunk - sets TRUE if this token is part of a chunk that
	 * started with an earlier token.
	 * 
	 * @generated
	 */
	public void setInChunk(boolean v) {
		if (TokenAnnotation_Type.featOkTst &&
		    ((TokenAnnotation_Type) jcasType).casFeat_inChunk == null)
			jcasType.jcas.throwFeatMissing("inChunk",
			    "txtfnnl.uima.tcas.TokenAnnotation");
		jcasType.ll_cas.ll_setBooleanValue(addr,
		    ((TokenAnnotation_Type) jcasType).casFeatCode_inChunk, v);
	}
}
