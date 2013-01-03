/* First created by JCasGen Wed Jun 06 14:45:22 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP_Type;

/**
 * Annotations of relationships between text annotations. All listed source participants interact
 * with all listed target participants (i.e., add particpants to both slots to create undirected
 * relationships). Updated by JCasGen Tue Nov 27 14:20:54 CET 2012 XML source:
 * /Users/fleitner/Workspace
 * /txtfnnl/txtfnnl-uima/src/main/resources/txtfnnl/uima/typeSystemDescriptor.xml
 * 
 * @generated
 */
public class RelationshipAnnotation extends SofaAnnotation {
  /**
   * @generated
   * @ordered
   */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = JCasRegistry.register(RelationshipAnnotation.class);
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
  protected RelationshipAnnotation() {/* intentionally empty block */}

  /**
   * Internal - constructor used by generator
   * 
   * @generated
   */
  public RelationshipAnnotation(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }

  /** @generated */
  public RelationshipAnnotation(JCas jcas) {
    super(jcas);
    readObject();
  }

  /**
   * <!-- begin-user-doc --> Write your own initialization here <!-- end-user-doc -->
   * 
   * @generated modifiable
   */
  private void readObject() {/*default - does nothing empty block */}

  // *--------------*
  // * Feature: sources
  /**
   * getter for sources - gets Source participants in this relationship.
   * 
   * @generated
   */
  public FSArray getSources() {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_sources == null) {
      jcasType.jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return (FSArray) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources)));
  }

  /**
   * setter for sources - sets Source participants in this relationship.
   * 
   * @generated
   */
  public void setSources(FSArray v) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_sources == null) {
      jcasType.jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.ll_cas.ll_setRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources,
        jcasType.ll_cas.ll_getFSRef(v));
  }

  /**
   * indexed getter for sources - gets an indexed value - Source participants in this relationship.
   * 
   * @generated
   */
  public TextAnnotation getSources(int i) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_sources == null) {
      jcasType.jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources), i);
    return (TextAnnotation) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefArrayValue(
        jcasType.ll_cas.ll_getRefValue(addr,
            ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources), i)));
  }

  /**
   * indexed setter for sources - sets an indexed value - Source participants in this relationship.
   * 
   * @generated
   */
  public void setSources(int i, TextAnnotation v) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_sources == null) {
      jcasType.jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources), i);
    jcasType.ll_cas.ll_setRefArrayValue(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_sources), i, jcasType.ll_cas
        .ll_getFSRef(v));
  }

  // *--------------*
  // * Feature: targets
  /**
   * getter for targets - gets Target participants in this relationship.
   * 
   * @generated
   */
  public FSArray getTargets() {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_targets == null) {
      jcasType.jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return (FSArray) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets)));
  }

  /**
   * setter for targets - sets Target participants in this relationship.
   * 
   * @generated
   */
  public void setTargets(FSArray v) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_targets == null) {
      jcasType.jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.ll_cas.ll_setRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets,
        jcasType.ll_cas.ll_getFSRef(v));
  }

  /**
   * indexed getter for targets - gets an indexed value - Target participants in this relationship.
   * 
   * @generated
   */
  public TextAnnotation getTargets(int i) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_targets == null) {
      jcasType.jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets), i);
    return (TextAnnotation) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefArrayValue(
        jcasType.ll_cas.ll_getRefValue(addr,
            ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets), i)));
  }

  /**
   * indexed setter for targets - sets an indexed value - Target participants in this relationship.
   * 
   * @generated
   */
  public void setTargets(int i, TextAnnotation v) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_targets == null) {
      jcasType.jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets), i);
    jcasType.ll_cas.ll_setRefArrayValue(jcasType.ll_cas.ll_getRefValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_targets), i, jcasType.ll_cas
        .ll_getFSRef(v));
  }

  // *--------------*
  // * Feature: isAuto
  /**
   * getter for isAuto - gets Allow interactions between annotations that are both in source and
   * target (i.e., "self-interactions").
   * 
   * @generated
   */
  public boolean getIsAuto() {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_isAuto == null) {
      jcasType.jcas.throwFeatMissing("isAuto", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return jcasType.ll_cas.ll_getBooleanValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_isAuto);
  }

  /**
   * setter for isAuto - sets Allow interactions between annotations that are both in source and
   * target (i.e., "self-interactions").
   * 
   * @generated
   */
  public void setIsAuto(boolean v) {
    if (RelationshipAnnotation_Type.featOkTst &&
        ((RelationshipAnnotation_Type) jcasType).casFeat_isAuto == null) {
      jcasType.jcas.throwFeatMissing("isAuto", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    jcasType.ll_cas.ll_setBooleanValue(addr,
        ((RelationshipAnnotation_Type) jcasType).casFeatCode_isAuto, v);
  }
}
