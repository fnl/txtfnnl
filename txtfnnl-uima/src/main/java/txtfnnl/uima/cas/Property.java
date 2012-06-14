/* First created by JCasGen Wed Jun 06 13:10:16 CEST 2012 */
package txtfnnl.uima.cas;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.cas.TOP_Type;

/** A free-form property structure for annotations.
 * Updated by JCasGen Wed Jun 06 14:49:53 CEST 2012
 * XML source: resources/typeSystemDescriptor.xml
 * @generated */
public class Property extends TOP {
  /** @generated
   * @ordered 
   */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = JCasRegistry.register(Property.class);
  /** @generated
   * @ordered 
   */
  @SuppressWarnings("hiding")
  public final static int type = typeIndexID;
  /** @generated  */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Property() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated */
  public Property(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public Property(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** <!-- begin-user-doc -->
    * Write your own initialization here
    * <!-- end-user-doc -->
  @generated modifiable */
  private void readObject() {/*default - does nothing empty block */}
     
 
    
  //*--------------*
  //* Feature: name

  /** getter for name - gets The name of this property.
   * @generated */
  public String getName() {
    if (Property_Type.featOkTst && ((Property_Type)jcasType).casFeat_name == null)
      jcasType.jcas.throwFeatMissing("name", "txtfnnl.uima.cas.Property");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Property_Type)jcasType).casFeatCode_name);}
    
  /** setter for name - sets The name of this property. 
   * @generated */
  public void setName(String v) {
    if (Property_Type.featOkTst && ((Property_Type)jcasType).casFeat_name == null)
      jcasType.jcas.throwFeatMissing("name", "txtfnnl.uima.cas.Property");
    jcasType.ll_cas.ll_setStringValue(addr, ((Property_Type)jcasType).casFeatCode_name, v);}    
   
    
  //*--------------*
  //* Feature: value

  /** getter for value - gets The value of this property.
   * @generated */
  public String getValue() {
    if (Property_Type.featOkTst && ((Property_Type)jcasType).casFeat_value == null)
      jcasType.jcas.throwFeatMissing("value", "txtfnnl.uima.cas.Property");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Property_Type)jcasType).casFeatCode_value);}
    
  /** setter for value - sets The value of this property. 
   * @generated */
  public void setValue(String v) {
    if (Property_Type.featOkTst && ((Property_Type)jcasType).casFeat_value == null)
      jcasType.jcas.throwFeatMissing("value", "txtfnnl.uima.cas.Property");
    jcasType.ll_cas.ll_setStringValue(addr, ((Property_Type)jcasType).casFeatCode_value, v);}    
  }

    