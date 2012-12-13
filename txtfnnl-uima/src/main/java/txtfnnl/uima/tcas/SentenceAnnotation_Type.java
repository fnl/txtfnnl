/* First created by JCasGen Tue Nov 27 14:20:55 CET 2012 */
package txtfnnl.uima.tcas;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;

/**
 * An explicit text annoation type for sentences to simplify their index retrieval. Updated by
 * JCasGen Tue Nov 27 14:20:55 CET 2012
 * 
 * @generated
 */
public class SentenceAnnotation_Type extends SyntaxAnnotation_Type {
    /** @generated */
    @Override
    protected FSGenerator getFSGenerator() {
        return fsGenerator;
    }

    /** @generated */
    private final FSGenerator fsGenerator = new FSGenerator() {
        public FeatureStructure createFS(int addr, CASImpl cas) {
            if (SentenceAnnotation_Type.this.useExistingInstance) {
                // Return eq fs instance if already created
                FeatureStructure fs = SentenceAnnotation_Type.this.jcas.getJfsFromCaddr(addr);
                if (null == fs) {
                    fs = new SentenceAnnotation(addr, SentenceAnnotation_Type.this);
                    SentenceAnnotation_Type.this.jcas.putJfsFromCaddr(addr, fs);
                    return fs;
                }
                return fs;
            } else return new SentenceAnnotation(addr, SentenceAnnotation_Type.this);
        }
    };
    /** @generated */
    @SuppressWarnings("hiding")
    public final static int typeIndexID = SentenceAnnotation.typeIndexID;
    /**
     * @generated
     * @modifiable
     */
    @SuppressWarnings("hiding")
    public final static boolean featOkTst = JCasRegistry
        .getFeatOkTst("txtfnnl.uima.tcas.SentenceAnnotation");

    /**
     * initialize variables to correspond with Cas Type and Features
     * 
     * @generated
     */
    public SentenceAnnotation_Type(JCas jcas, Type casType) {
        super(jcas, casType);
        casImpl.getFSClassRegistry()
            .addGeneratorForType((TypeImpl) this.casType, getFSGenerator());
    }
}
