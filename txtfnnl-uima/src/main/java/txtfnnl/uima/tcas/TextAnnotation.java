/* First created by JCasGen Fri Oct 26 14:22:46 CEST 2012 */
package txtfnnl.uima.tcas;

import java.util.Comparator;

import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSMatchConstraint;
import org.apache.uima.cas.FSStringConstraint;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeaturePath;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;

import txtfnnl.uima.cas.Property;
import txtfnnl.utils.Offset;

/**
 * Annotations on a specific text span. Updated by JCasGen Tue Nov 27 14:20:54 CET 2012 XML source:
 * /Users/fleitner/Workspace/txtfnnl/txtfnnl-uima/src/main
 * /resources/txtfnnl/uima/typeSystemDescriptor.xml
 * 
 * @generated
 */
public class TextAnnotation extends Annotation {
  /* ADDITIONS START */
  /**
   * A comparator instance that allows to compare and sort text annotations.
   */
  public static final Comparator<TextAnnotation> TEXT_ANNOTATION_COMPARATOR = new Comparator<TextAnnotation>() {
    public int compare(TextAnnotation ta1, TextAnnotation ta2) {
      if (ta1 == null || ta2 == null) {
        if (ta1 == null && ta2 == null) return 0;
        else if (ta1 == null) return 1;
        else return -1;
      } else if (ta1.getBegin() < ta2.getBegin()) return -1;
      else if (ta1.getBegin() > ta2.getBegin()) return 1;
      else {
        if (ta1.getEnd() < ta2.getEnd()) return 1;
        else if (ta1.getEnd() > ta2.getEnd()) return -1;
        else {
          int r = ta1.getNamespace().compareTo(ta2.getNamespace());
          if (r == 0) {
            r = ta1.getIdentifier().compareTo(ta2.getIdentifier());
          }
          return r;
        }
      }
    }
  };

  /**
   * Return a specialized filter for this annotation type.
   * 
   * @param typeName of the annotation to constrain to
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @param namespaceStr to filter on
   * @param identifierStr to filter on
   * @return a particular text annotation constraint
   */
  protected static FSMatchConstraint makeConstraint(String typeName, JCas jcas,
      String annotatorUri, String namespace, String identifier) {
    final ConstraintFactory cf = jcas.getConstraintFactory();
    FSMatchConstraint constraint = null;
    if (annotatorUri != null) {
      constraint = TextAnnotation
          .createConstraint(typeName + ":annotator", jcas, annotatorUri, cf);
    }
    if (namespace != null) {
      if (constraint == null) {
        constraint = TextAnnotation.createConstraint(typeName + ":namespace", jcas, namespace, cf);
      } else {
        constraint = cf.and(constraint,
            TextAnnotation.createConstraint(typeName + ":namespace", jcas, namespace, cf));
      }
    }
    if (identifier != null) {
      if (constraint == null) {
        constraint = TextAnnotation.createConstraint(typeName + ":identifier", jcas, identifier,
            cf);
      } else {
        constraint = cf.and(constraint,
            TextAnnotation.createConstraint(typeName + ":identifier", jcas, identifier, cf));
      }
    }
    return constraint;
  }

  private static FSMatchConstraint createConstraint(String featureName, JCas jcas,
      String stringConstraint, ConstraintFactory cf) throws CASRuntimeException {
    final Feature feature = jcas.getTypeSystem().getFeatureByFullName(featureName);
    final FeaturePath featurePath = jcas.createFeaturePath();
    featurePath.addFeature(feature);
    final FSStringConstraint fsStringConstraint = cf.createStringConstraint();
    fsStringConstraint.equals(stringConstraint);
    return cf.embedConstraint(featurePath, fsStringConstraint);
  }

  /**
   * Return a specialized filter for this annotation type.
   * 
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @param namespaceStr to filter on
   * @param identifierStr to filter on
   * @return a particular text annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri, String namespace,
      String identifier) {
    return TextAnnotation.makeConstraint(TextAnnotation.class.getName(), jcas, annotatorUri,
        namespace, identifier);
  }

  /**
   * Return a specialized filter for text annotations.
   * 
   * @param jcas to create the constraint for
   * @param annoatorUri to filter on
   * @param namespace to filter on
   * @return a particular text annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String annotatorUri, String namespace) {
    return TextAnnotation.makeConstraint(jcas, annotatorUri, namespace, null);
  }

  /**
   * Return a specialized filter for text annotations.
   * 
   * @param jcas to create the constraint for
   * @param namespace to filter on
   * @return a particular text annotation constraint
   */
  public static FSMatchConstraint makeConstraint(JCas jcas, String namespace) {
    return TextAnnotation.makeConstraint(jcas, null, namespace);
  }

  /**
   * Return an iterator over the index for this annotation type.
   * 
   * @param jcas providing the index
   * @return an annotation feature structure iterator
   */
  public static FSIterator<Annotation> getIterator(JCas jcas) {
    return jcas.getAnnotationIndex(TextAnnotation.type).iterator();
  }

  private Offset offset = null;

  public Offset getOffset() {
    if (offset == null) {
      final int start = getBegin();
      final int end = getEnd();
      if (start == end) {
        offset = new Offset(start);
      } else {
        offset = new Offset(start, end);
      }
    }
    return offset;
  }

  public boolean contains(TextAnnotation other) {
    return getOffset().contains(other.getOffset());
  }

  public TextAnnotation(JCas jcas, Offset offset) {
    super(jcas);
    setBegin(offset.start());
    setEnd(offset.end());
    readObject();
    this.offset = offset;
  }

  /* ADDITIONS END */
  /**
   * @generated
   * @ordered
   */
  @SuppressWarnings("hiding")
  public final static int typeIndexID = JCasRegistry.register(TextAnnotation.class);
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
  protected TextAnnotation() {/* intentionally empty block */}

  /**
   * Internal - constructor used by generator
   * 
   * @generated
   */
  public TextAnnotation(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }

  /** @generated */
  public TextAnnotation(JCas jcas) {
    super(jcas);
    readObject();
  }

  /** @generated */
  public TextAnnotation(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }

  /**
   * <!-- begin-user-doc --> Write your own initialization here <!-- end-user-doc -->
   * 
   * @generated modifiable
   */
  private void readObject() {/* default - does nothing empty block */}

  // *--------------*
  // * Feature: annotator
  /**
   * getter for annotator - gets A URI identifying the annotator (humans: e-mail, automated: URL).
   * 
   * @generated
   */
  public String getAnnotator() {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_annotator == null) {
      jcasType.jcas.throwFeatMissing("annotator", "txtfnnl.uima.tcas.TextAnnotation");
    }
    return jcasType.ll_cas.ll_getStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_annotator);
  }

  /**
   * setter for annotator - sets A URI identifying the annotator (humans: e-mail, automated: URL).
   * 
   * @generated
   */
  public void setAnnotator(String v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_annotator == null) {
      jcasType.jcas.throwFeatMissing("annotator", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.ll_cas.ll_setStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_annotator, v);
  }

  // *--------------*
  // * Feature: confidence
  /**
   * getter for confidence - gets The annotator's confidence in this annotation (range: (0,1]).
   * 
   * @generated
   */
  public double getConfidence() {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_confidence == null) {
      jcasType.jcas.throwFeatMissing("confidence", "txtfnnl.uima.tcas.TextAnnotation");
    }
    return jcasType.ll_cas.ll_getDoubleValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_confidence);
  }

  /**
   * setter for confidence - sets The annotator's confidence in this annotation (range: (0,1]).
   * 
   * @generated
   */
  public void setConfidence(double v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_confidence == null) {
      jcasType.jcas.throwFeatMissing("confidence", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.ll_cas.ll_setDoubleValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_confidence, v);
  }

  // *--------------*
  // * Feature: namespace
  /**
   * getter for namespace - gets The namespace URL defining the annotation type system (e.g., the
   * URL of the used ontology).
   * 
   * @generated
   */
  public String getNamespace() {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_namespace == null) {
      jcasType.jcas.throwFeatMissing("namespace", "txtfnnl.uima.tcas.TextAnnotation");
    }
    return jcasType.ll_cas.ll_getStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_namespace);
  }

  /**
   * setter for namespace - sets The namespace URL defining the annotation type system (e.g., the
   * URL of the used ontology).
   * 
   * @generated
   */
  public void setNamespace(String v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_namespace == null) {
      jcasType.jcas.throwFeatMissing("namespace", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.ll_cas.ll_setStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_namespace, v);
  }

  // *--------------*
  // * Feature: identifier
  /**
   * getter for identifier - gets The annotation type ID that should usually form a fully valid URL
   * by joining the namespace and this value with a slash ("/").
   * 
   * @generated
   */
  public String getIdentifier() {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_identifier == null) {
      jcasType.jcas.throwFeatMissing("identifier", "txtfnnl.uima.tcas.TextAnnotation");
    }
    return jcasType.ll_cas.ll_getStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_identifier);
  }

  /**
   * setter for identifier - sets The annotation type ID that should usually form a fully valid URL
   * by joining the namespace and this value with a slash ("/").
   * 
   * @generated
   */
  public void setIdentifier(String v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_identifier == null) {
      jcasType.jcas.throwFeatMissing("identifier", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.ll_cas.ll_setStringValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_identifier, v);
  }

  // *--------------*
  // * Feature: properties
  /**
   * getter for properties - gets Additional, free-form properties for this annotation.
   * 
   * @generated
   */
  public FSArray getProperties() {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_properties == null) {
      jcasType.jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.TextAnnotation");
    }
    return (FSArray) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_properties)));
  }

  /**
   * setter for properties - sets Additional, free-form properties for this annotation.
   * 
   * @generated
   */
  public void setProperties(FSArray v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_properties == null) {
      jcasType.jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.ll_cas.ll_setRefValue(addr, ((TextAnnotation_Type) jcasType).casFeatCode_properties,
        jcasType.ll_cas.ll_getFSRef(v));
  }

  /**
   * indexed getter for properties - gets an indexed value - Additional, free-form properties for
   * this annotation.
   * 
   * @generated
   */
  public Property getProperties(int i) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_properties == null) {
      jcasType.jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_properties), i);
    return (Property) (jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefArrayValue(
        jcasType.ll_cas.ll_getRefValue(addr,
            ((TextAnnotation_Type) jcasType).casFeatCode_properties), i)));
  }

  /**
   * indexed setter for properties - sets an indexed value - Additional, free-form properties for
   * this annotation.
   * 
   * @generated
   */
  public void setProperties(int i, Property v) {
    if (TextAnnotation_Type.featOkTst &&
        ((TextAnnotation_Type) jcasType).casFeat_properties == null) {
      jcasType.jcas.throwFeatMissing("properties", "txtfnnl.uima.tcas.TextAnnotation");
    }
    jcasType.jcas.checkArrayBounds(jcasType.ll_cas.ll_getRefValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_properties), i);
    jcasType.ll_cas.ll_setRefArrayValue(jcasType.ll_cas.ll_getRefValue(addr,
        ((TextAnnotation_Type) jcasType).casFeatCode_properties), i, jcasType.ll_cas
        .ll_getFSRef(v));
  }
}
