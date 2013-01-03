/* First created by JCasGen Tue Nov 27 14:20:55 CET 2012 */
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
 * A subtype for tokens with additional features for the PoS and chunk tag and the token's stem.
 * Updated by JCasGen Tue Nov 27 14:20:55 CET 2012
 * 
 * @generated
 */
public class TokenAnnotation_Type extends SyntaxAnnotation_Type {
  /** @generated */
  @Override
  protected FSGenerator getFSGenerator() {
    return fsGenerator;
  }

  /** @generated */
  private final FSGenerator fsGenerator = new FSGenerator() {
    public FeatureStructure createFS(int addr, CASImpl cas) {
      if (TokenAnnotation_Type.this.useExistingInstance) {
        // Return eq fs instance if already created
        FeatureStructure fs = TokenAnnotation_Type.this.jcas.getJfsFromCaddr(addr);
        if (null == fs) {
          fs = new TokenAnnotation(addr, TokenAnnotation_Type.this);
          TokenAnnotation_Type.this.jcas.putJfsFromCaddr(addr, fs);
          return fs;
        }
        return fs;
      } else return new TokenAnnotation(addr, TokenAnnotation_Type.this);
    }
  };
  /** @generated */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = TokenAnnotation.typeIndexID;
  /**
   * @generated
   * @modifiable
   */
  @SuppressWarnings("hiding")
  public final static boolean featOkTst = JCasRegistry
      .getFeatOkTst("txtfnnl.uima.tcas.TokenAnnotation");
  /** @generated */
  final Feature casFeat_pos;
  /** @generated */
  final int casFeatCode_pos;

  /** @generated */
  public String getPos(int addr) {
    if (featOkTst && casFeat_pos == null) {
      jcas.throwFeatMissing("pos", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    return ll_cas.ll_getStringValue(addr, casFeatCode_pos);
  }

  /** @generated */
  public void setPos(int addr, String v) {
    if (featOkTst && casFeat_pos == null) {
      jcas.throwFeatMissing("pos", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    ll_cas.ll_setStringValue(addr, casFeatCode_pos, v);
  }

  /** @generated */
  final Feature casFeat_stem;
  /** @generated */
  final int casFeatCode_stem;

  /** @generated */
  public String getStem(int addr) {
    if (featOkTst && casFeat_stem == null) {
      jcas.throwFeatMissing("stem", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    return ll_cas.ll_getStringValue(addr, casFeatCode_stem);
  }

  /** @generated */
  public void setStem(int addr, String v) {
    if (featOkTst && casFeat_stem == null) {
      jcas.throwFeatMissing("stem", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    ll_cas.ll_setStringValue(addr, casFeatCode_stem, v);
  }

  /** @generated */
  final Feature casFeat_chunk;
  /** @generated */
  final int casFeatCode_chunk;

  /** @generated */
  public String getChunk(int addr) {
    if (featOkTst && casFeat_chunk == null) {
      jcas.throwFeatMissing("chunk", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    return ll_cas.ll_getStringValue(addr, casFeatCode_chunk);
  }

  /** @generated */
  public void setChunk(int addr, String v) {
    if (featOkTst && casFeat_chunk == null) {
      jcas.throwFeatMissing("chunk", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    ll_cas.ll_setStringValue(addr, casFeatCode_chunk, v);
  }

  /** @generated */
  final Feature casFeat_chunkBegin;
  /** @generated */
  final int casFeatCode_chunkBegin;

  /** @generated */
  public boolean getChunkBegin(int addr) {
    if (featOkTst && casFeat_chunkBegin == null) {
      jcas.throwFeatMissing("chunkBegin", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    return ll_cas.ll_getBooleanValue(addr, casFeatCode_chunkBegin);
  }

  /** @generated */
  public void setChunkBegin(int addr, boolean v) {
    if (featOkTst && casFeat_chunkBegin == null) {
      jcas.throwFeatMissing("chunkBegin", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    ll_cas.ll_setBooleanValue(addr, casFeatCode_chunkBegin, v);
  }

  /** @generated */
  final Feature casFeat_chunkEnd;
  /** @generated */
  final int casFeatCode_chunkEnd;

  /** @generated */
  public boolean getChunkEnd(int addr) {
    if (featOkTst && casFeat_chunkEnd == null) {
      jcas.throwFeatMissing("chunkEnd", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    return ll_cas.ll_getBooleanValue(addr, casFeatCode_chunkEnd);
  }

  /** @generated */
  public void setChunkEnd(int addr, boolean v) {
    if (featOkTst && casFeat_chunkEnd == null) {
      jcas.throwFeatMissing("chunkEnd", "txtfnnl.uima.tcas.TokenAnnotation");
    }
    ll_cas.ll_setBooleanValue(addr, casFeatCode_chunkEnd, v);
  }

  /**
   * initialize variables to correspond with Cas Type and Features
   * 
   * @generated
   */
  public TokenAnnotation_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl) this.casType, getFSGenerator());
    casFeat_pos = jcas.getRequiredFeatureDE(casType, "pos", "uima.cas.String", featOkTst);
    casFeatCode_pos = (null == casFeat_pos) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_pos).getCode();
    casFeat_stem = jcas.getRequiredFeatureDE(casType, "stem", "uima.cas.String", featOkTst);
    casFeatCode_stem = (null == casFeat_stem) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_stem).getCode();
    casFeat_chunk = jcas.getRequiredFeatureDE(casType, "chunk", "uima.cas.String", featOkTst);
    casFeatCode_chunk = (null == casFeat_chunk) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_chunk).getCode();
    casFeat_chunkBegin = jcas.getRequiredFeatureDE(casType, "chunkBegin", "uima.cas.Boolean",
        featOkTst);
    casFeatCode_chunkBegin = (null == casFeat_chunkBegin) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_chunkBegin).getCode();
    casFeat_chunkEnd = jcas.getRequiredFeatureDE(casType, "chunkEnd", "uima.cas.Boolean",
        featOkTst);
    casFeatCode_chunkEnd = (null == casFeat_chunkEnd) ? JCas.INVALID_FEATURE_CODE
        : ((FeatureImpl) casFeat_chunkEnd).getCode();
  }
}
