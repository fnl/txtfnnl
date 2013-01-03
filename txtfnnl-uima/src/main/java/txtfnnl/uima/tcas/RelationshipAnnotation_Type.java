/* First created by JCasGen Wed Jun 06 14:45:22 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;

/**
 * Annotations of relationships between text annotations. All listed source participants interact
 * with all listed target participants (i.e., add particpants to both slots to create undirected
 * relationships). Updated by JCasGen Tue Nov 27 14:20:54 CET 2012
 * 
 * @generated
 */
public class RelationshipAnnotation_Type extends SofaAnnotation_Type {
  /** @generated */
  @Override
  protected FSGenerator getFSGenerator() {
    return fsGenerator;
  }

  /** @generated */
  private final FSGenerator fsGenerator = new FSGenerator() {
    public FeatureStructure createFS(int addr, CASImpl cas) {
      if (RelationshipAnnotation_Type.this.useExistingInstance) {
        // Return eq fs instance if already created
        FeatureStructure fs = RelationshipAnnotation_Type.this.jcas.getJfsFromCaddr(addr);
        if (null == fs) {
          fs = new RelationshipAnnotation(addr, RelationshipAnnotation_Type.this);
          RelationshipAnnotation_Type.this.jcas.putJfsFromCaddr(addr, fs);
          return fs;
        }
        return fs;
      } else return new RelationshipAnnotation(addr, RelationshipAnnotation_Type.this);
    }
  };
  /** @generated */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = RelationshipAnnotation.typeIndexID;
  /**
   * @generated
   * @modifiable
   */
  @SuppressWarnings("hiding")
  public final static boolean featOkTst = JCasRegistry
      .getFeatOkTst("txtfnnl.uima.tcas.RelationshipAnnotation");
  /** @generated */
  final Feature casFeat_sources;
  /** @generated */
  final int casFeatCode_sources;

  /** @generated */
  public int getSources(int addr) {
    if (featOkTst && casFeat_sources == null) {
      jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return ll_cas.ll_getRefValue(addr, casFeatCode_sources);
  }

  /** @generated */
  public void setSources(int addr, int v) {
    if (featOkTst && casFeat_sources == null) {
      jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    ll_cas.ll_setRefValue(addr, casFeatCode_sources, v);
  }

  /** @generated */
  public int getSources(int addr, int i) {
    if (featOkTst && casFeat_sources == null) {
      jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    if (lowLevelTypeChecks)
      return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i, true);
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i);
    return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i);
  }

  /** @generated */
  public void setSources(int addr, int i, int v) {
    if (featOkTst && casFeat_sources == null) {
      jcas.throwFeatMissing("sources", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    if (lowLevelTypeChecks) {
      ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i, v, true);
    }
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i);
    ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_sources), i, v);
  }

  /** @generated */
  final Feature casFeat_targets;
  /** @generated */
  final int casFeatCode_targets;

  /** @generated */
  public int getTargets(int addr) {
    if (featOkTst && casFeat_targets == null) {
      jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return ll_cas.ll_getRefValue(addr, casFeatCode_targets);
  }

  /** @generated */
  public void setTargets(int addr, int v) {
    if (featOkTst && casFeat_targets == null) {
      jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    ll_cas.ll_setRefValue(addr, casFeatCode_targets, v);
  }

  /** @generated */
  public int getTargets(int addr, int i) {
    if (featOkTst && casFeat_targets == null) {
      jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    if (lowLevelTypeChecks)
      return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i, true);
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i);
    return ll_cas.ll_getRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i);
  }

  /** @generated */
  public void setTargets(int addr, int i, int v) {
    if (featOkTst && casFeat_targets == null) {
      jcas.throwFeatMissing("targets", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    if (lowLevelTypeChecks) {
      ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i, v, true);
    }
    jcas.checkArrayBounds(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i);
    ll_cas.ll_setRefArrayValue(ll_cas.ll_getRefValue(addr, casFeatCode_targets), i, v);
  }

  /** @generated */
  final Feature casFeat_isAuto;
  /** @generated */
  final int casFeatCode_isAuto;

  /** @generated */
  public boolean getIsAuto(int addr) {
    if (featOkTst && casFeat_isAuto == null) {
      jcas.throwFeatMissing("isAuto", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    return ll_cas.ll_getBooleanValue(addr, casFeatCode_isAuto);
  }

  /** @generated */
  public void setIsAuto(int addr, boolean v) {
    if (featOkTst && casFeat_isAuto == null) {
      jcas.throwFeatMissing("isAuto", "txtfnnl.uima.tcas.RelationshipAnnotation");
    }
    ll_cas.ll_setBooleanValue(addr, casFeatCode_isAuto, v);
  }

  /**
   * initialize variables to correspond with Cas Type and Features
   * 
   * @generated
   */
  public RelationshipAnnotation_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl) this.casType, getFSGenerator());
    casFeat_sources = jcas.getRequiredFeatureDE(casType, "sources", "uima.cas.FSArray", featOkTst);
    casFeatCode_sources = (null == casFeat_sources) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_sources).getCode();
    casFeat_targets = jcas.getRequiredFeatureDE(casType, "targets", "uima.cas.FSArray", featOkTst);
    casFeatCode_targets = (null == casFeat_targets) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_targets).getCode();
    casFeat_isAuto = jcas.getRequiredFeatureDE(casType, "isAuto", "uima.cas.Boolean", featOkTst);
    casFeatCode_isAuto = (null == casFeat_isAuto) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_isAuto).getCode();
  }
}
