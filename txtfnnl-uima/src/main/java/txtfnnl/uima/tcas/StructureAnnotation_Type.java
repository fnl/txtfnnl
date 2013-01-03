/* First created by JCasGen Fri Jun 22 11:12:49 CEST 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;

/**
 * TextAnnotations of text structure (sections, paragraphs, headings, typeface, sub- and
 * superscript, etc.) Updated by JCasGen Tue Nov 27 14:20:54 CET 2012
 * 
 * @generated
 */
public class StructureAnnotation_Type extends TextAnnotation_Type {
  /** @generated */
  @Override
  protected FSGenerator getFSGenerator() {
    return fsGenerator;
  }

  /** @generated */
  private final FSGenerator fsGenerator = new FSGenerator() {
    public FeatureStructure createFS(int addr, CASImpl cas) {
      if (StructureAnnotation_Type.this.useExistingInstance) {
        // Return eq fs instance if already created
        FeatureStructure fs = StructureAnnotation_Type.this.jcas.getJfsFromCaddr(addr);
        if (null == fs) {
          fs = new StructureAnnotation(addr, StructureAnnotation_Type.this);
          StructureAnnotation_Type.this.jcas.putJfsFromCaddr(addr, fs);
          return fs;
        }
        return fs;
      } else return new StructureAnnotation(addr, StructureAnnotation_Type.this);
    }
  };
  /** @generated */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = StructureAnnotation.typeIndexID;
  /**
   * @generated
   * @modifiable
   */
  @SuppressWarnings("hiding")
  public final static boolean featOkTst = JCasRegistry
      .getFeatOkTst("txtfnnl.uima.tcas.StructureAnnotation");

  /**
   * initialize variables to correspond with Cas Type and Features
   * 
   * @generated
   */
  public StructureAnnotation_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl) this.casType, getFSGenerator());
  }
}
