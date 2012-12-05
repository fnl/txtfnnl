
/* First created by JCasGen Wed Jun 06 13:10:16 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.cas.Feature;
import org.apache.uima.jcas.cas.AnnotationBase_Type;

/** A base type for SOFA-wide annotations.
 * Updated by JCasGen Tue Nov 27 14:20:54 CET 2012
 * @generated */
public class SofaAnnotation_Type extends AnnotationBase_Type {
  /** @generated */
  @Override
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (SofaAnnotation_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = SofaAnnotation_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new SofaAnnotation(addr, SofaAnnotation_Type.this);
  			   SofaAnnotation_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new SofaAnnotation(addr, SofaAnnotation_Type.this);
  	  }
    };
  /** @generated */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = SofaAnnotation.typeIndexID;
  /** @generated 
     @modifiable */
  @SuppressWarnings("hiding")
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("txtfnnl.uima.tcas.SofaAnnotation");
 
  /** @generated */
  final Feature casFeat_annotator;
  /** @generated */
  final int     casFeatCode_annotator;
  /** @generated */ 
  public String getAnnotator(int addr) {
        if (featOkTst && casFeat_annotator == null)
      jcas.throwFeatMissing("annotator", "txtfnnl.uima.tcas.SofaAnnotation");
    return ll_cas.ll_getStringValue(addr, casFeatCode_annotator);
  }
  /** @generated */    
  public void setAnnotator(int addr, String v) {
        if (featOkTst && casFeat_annotator == null)
      jcas.throwFeatMissing("annotator", "txtfnnl.uima.tcas.SofaAnnotation");
    ll_cas.ll_setStringValue(addr, casFeatCode_annotator, v);}
    
  
 
  /** @generated */
  final Feature casFeat_confidence;
  /** @generated */
  final int     casFeatCode_confidence;
  /** @generated */ 
  public double getConfidence(int addr) {
        if (featOkTst && casFeat_confidence == null)
      jcas.throwFeatMissing("confidence", "txtfnnl.uima.tcas.SofaAnnotation");
    return ll_cas.ll_getDoubleValue(addr, casFeatCode_confidence);
  }
  /** @generated */    
  public void setConfidence(int addr, double v) {
        if (featOkTst && casFeat_confidence == null)
      jcas.throwFeatMissing("confidence", "txtfnnl.uima.tcas.SofaAnnotation");
    ll_cas.ll_setDoubleValue(addr, casFeatCode_confidence, v);}
    
  
 
  /** @generated */
  final Feature casFeat_namespace;
  /** @generated */
  final int     casFeatCode_namespace;
  /** @generated */ 
  public String getNamespace(int addr) {
        if (featOkTst && casFeat_namespace == null)
      jcas.throwFeatMissing("namespace", "txtfnnl.uima.tcas.SofaAnnotation");
    return ll_cas.ll_getStringValue(addr, casFeatCode_namespace);
  }
  /** @generated */    
  public void setNamespace(int addr, String v) {
        if (featOkTst && casFeat_namespace == null)
      jcas.throwFeatMissing("namespace", "txtfnnl.uima.tcas.SofaAnnotation");
    ll_cas.ll_setStringValue(addr, casFeatCode_namespace, v);}
    
  
 
  /** @generated */
  final Feature casFeat_identifier;
  /** @generated */
  final int     casFeatCode_identifier;
  /** @generated */ 
  public String getIdentifier(int addr) {
        if (featOkTst && casFeat_identifier == null)
      jcas.throwFeatMissing("identifier", "txtfnnl.uima.tcas.SofaAnnotation");
    return ll_cas.ll_getStringValue(addr, casFeatCode_identifier);
  }
  /** @generated */    
  public void setIdentifier(int addr, String v) {
        if (featOkTst && casFeat_identifier == null)
      jcas.throwFeatMissing("identifier", "txtfnnl.uima.tcas.SofaAnnotation");
    ll_cas.ll_setStringValue(addr, casFeatCode_identifier, v);}
    
  
 
  /** @generated */
  final Feature casFeat_properties;
  /** @generated */
  final int     casFeatCode_properties;
  /** @generated */ 
  public int getProperties(int addr) {
        if (featOkTst && casFeat_properties == null)
      jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.SofaAnnotation");
    return ll_cas.ll_getRefValue(addr, casFeatCode_properties);
  }
  /** @generated */    
  public void setProperties(int addr, int v) {
        if (featOkTst && casFeat_properties == null)
      jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.SofaAnnotation");
    ll_cas.ll_setRefValue(addr, casFeatCode_properties, v);}
    
   /** @generated */
  public int getProperties(int addr, int i) {
        if (featOkTst && casFeat_properties == null)
      jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.SofaAnnotation");
    if (lowLevelTypeChecks)
      return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i, true);
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i);
  return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i);
  }
   
  /** @generated */ 
  public void setProperties(int addr, int i, int v) {
        if (featOkTst && casFeat_properties == null)
      jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.SofaAnnotation");
    if (lowLevelTypeChecks)
      ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i, v, true);
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i);
    ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_properties), i, v);
  }
 



  /** initialize variables to correspond with Cas Type and Features
	* @generated */
  public SofaAnnotation_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

 
    casFeat_annotator = jcas.getRequiredFeatureDE(casType, "annotator", "uima.cas.String", featOkTst);
    casFeatCode_annotator  = (null == casFeat_annotator) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_annotator).getCode();

 
    casFeat_confidence = jcas.getRequiredFeatureDE(casType, "confidence", "uima.cas.Double", featOkTst);
    casFeatCode_confidence  = (null == casFeat_confidence) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_confidence).getCode();

 
    casFeat_namespace = jcas.getRequiredFeatureDE(casType, "namespace", "uima.cas.String", featOkTst);
    casFeatCode_namespace  = (null == casFeat_namespace) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_namespace).getCode();

 
    casFeat_identifier = jcas.getRequiredFeatureDE(casType, "identifier", "uima.cas.String", featOkTst);
    casFeatCode_identifier  = (null == casFeat_identifier) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_identifier).getCode();

 
    casFeat_properties = jcas.getRequiredFeatureDE(casType, "properties", "uima.cas.FSArray", featOkTst);
    casFeatCode_properties  = (null == casFeat_properties) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_properties).getCode();

  }
}



    