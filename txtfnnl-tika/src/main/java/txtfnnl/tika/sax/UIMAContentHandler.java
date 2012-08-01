/**
 * A content handler that bridges SAX, Tika, and UIMA.
 */
package txtfnnl.tika.sax;

import java.util.Stack;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.uima.UIMAFramework;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;

import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.DocumentAnnotation;
import txtfnnl.uima.tcas.StructureAnnotation;

/**
 * An event handler that specifically "works" with the UIMA AE philosophy.
 * 
 * This special SAX handler is intended to be used by the
 * {@link txtfnnl.tika.uima.TikaAnnotator} to extract content for UIMA.
 * 
 * All non-composite Greek characters plus the Latin small sharp S character
 * are replaced with their Latin names (sharp S "ß" gets replaced with
 * "beta"). This includes all default capital letters (U+0391 - U+03A9), small
 * letters (U+03B1 - U03C9, ie., incl. final sigma), Kai and kai (U+03CF,
 * U+03D7) and the variant letter forms for beta, theta, phi, pi, kappa, rho,
 * and Theta.
 * 
 * All non-standard hyphen characters (U+2010, U+2011, U+2043) are replaced
 * with the default hyphen (U+002D). The same is done for the dash characters
 * (U+2012, U+2013, U+2014, U+2015, U+FE58, U+FE63, U+FF0D).
 * 
 * @author Florian Leitner
 */
public class UIMAContentHandler extends ContentHandlerDecorator {

	/** The CAS view that will be populated by the handler. */
	private JCas view = null;

	/** The characters received from the parser. */
	private StringBuffer textBuffer;

	/** Started markup elements that are not yet ended (closed). */
	private Stack<StructureAnnotation> annotationStack;

	/** A logger for this handler. */
	private Logger logger = UIMAFramework.getLogger(UIMAContentHandler.class);

	/**
	 * Content handler that will populate the given CAS' view.
	 * 
	 * @param cas view to populate
	 */
	public UIMAContentHandler(JCas cas) {
		super();
		setView(cas);
	}

	private char[] dashChars = new char[] {
	    '\u2010',
	    '\u2011',
	    '\u2012',
	    '\u2013',
	    '\u2014',
	    '\u2015',
	    '\u2043',
	    '\uFE58',
	    '\uFE63',
	    '\uFF0D', };

	private char[] greekChars = new char[] {
	    // Upper
	    '\u0391',
	    '\u0392',
	    '\u0393',
	    '\u0394',
	    '\u0395',
	    '\u0396',
	    // 6
	    '\u0397',
	    '\u0398',
	    '\u0399',
	    '\u039A',
	    '\u039B',
	    '\u039C',
	    // 12
	    '\u039D',
	    '\u039E',
	    '\u039F',
	    '\u03A0',
	    '\u03A1',
	    '\u03A3',
	    // 18
	    '\u03A4',
	    '\u03A5',
	    '\u03A6',
	    '\u03A7',
	    '\u03A8',
	    '\u03A9',
	    // Lower
	    '\u03B1',
	    '\u03B2',
	    '\u00DF', // latin small letter sharp S (more often used as small beta
	              // rather than the appropriate character use!)
	    '\u03B3',
	    '\u03B4',
	    '\u03B5',
	    '\u03B6',
	    // 6
	    '\u03B7',
	    '\u03B8',
	    '\u03B9',
	    '\u03BA',
	    '\u03BB',
	    '\u03BC',
	    // 12
	    '\u03BD',
	    '\u03BE',
	    '\u03BF',
	    '\u03C0',
	    '\u03C1',
	    '\u03C2',
	    '\u03C3', // final sigma
	    // 18
	    '\u03C4',
	    '\u03C5',
	    '\u03C6',
	    '\u03C7',
	    '\u03C8',
	    '\u03C9',
	    // Variants (6)
	    '\u03CF',
	    '\u03D0',
	    '\u03D1',
	    '\u03D5',
	    '\u03D6',
	    '\u03D7',
	    // More Variants (6)
	    '\u03F0',
	    '\u03F1',
	    '\u03F4', };

	private String[] greekNames = new String[] {
	    // Upper
	    "Alpha",
	    "Beta",
	    "Gamma",
	    "Delta",
	    "Epsilon",
	    "Zeta",
	    // 6
	    "Eta",
	    "Theta",
	    "Iota",
	    "Kappa",
	    "Lambda",
	    "Mu",
	    // 12
	    "Nu",
	    "Xi",
	    "Omicron",
	    "Pi",
	    "Rho",
	    "Sigma",
	    // 18
	    "Tau",
	    "Upsilon",
	    "Phi",
	    "Chi",
	    "Psi",
	    "Omega",
	    // lower
	    "alpha",
	    "beta",
	    "beta", // latin small letter sharp S
	    "gamma",
	    "delta",
	    "epsilon",
	    "zeta",
	    // 6
	    "eta",
	    "theta",
	    "iota",
	    "kappa",
	    "lambda",
	    "mu",
	    // 12
	    "nu",
	    "xi",
	    "omicron",
	    "pi",
	    "rho",
	    "sigma",
	    "sigma", // final sigma
	    // 18
	    "tau",
	    "upsilon",
	    "phi",
	    "chi",
	    "psi",
	    "omega",
	    // Variants (6)
	    "Kai",
	    "beta",
	    "theta",
	    "phi",
	    "pi",
	    "kai",
	    // More Variants (3)
	    "kappa",
	    "rho",
	    "Theta", };

	/**
	 * Decorative content handler for the given handler.
	 * 
	 * Needs to have a view assigned first (setView(JCas)).
	 * 
	 * @param handler to decorate
	 */
	public UIMAContentHandler(ContentHandler handler) {
		super(handler);
	}

	/**
	 * Decorative content handler for the given handler.
	 * 
	 * @param handler to decorate
	 * @param cas view to populate
	 */
	public UIMAContentHandler(ContentHandler handler, JCas cas) {
		super(handler);
		setView(cas);
	}

	/**
	 * Get the the CAS view being annotated by the handler.
	 * 
	 * @return a JCas with the handler's SOFA
	 */
	public JCas getView() {
		return view;
	}

	/**
	 * Set the CAS view the handler should populate with text and annotations.
	 * 
	 * @param cas SOFA view to populate
	 */
	public void setView(JCas cas) {
		view = cas;
	}

	/**
	 * Add (Tika) metadata to this view as document annotations.
	 * 
	 * @param metadata values extracted by Tika
	 */
	public void addMetadata(Metadata metadata) {
		int num_names = metadata.size();

		if (num_names > 0) {
			for (String name : metadata.names()) {
				for (String val : metadata.getValues(name)) {
					DocumentAnnotation da = new DocumentAnnotation(view);
					da.setNamespace(name);
					da.setIdentifier(val);
					da.setAnnotator(TikaAnnotator.URI);
					da.setConfidence(1.0);
					da.addToIndexes();
				}
			}
		}
	}

	/**
	 * Start a new document, initializing the handler's internal buffers.
	 */
	@Override
	public void startDocument() {
		textBuffer = new StringBuffer();
		annotationStack = new Stack<StructureAnnotation>();
	}

	/**
	 * Create a new Element starting at the current offset.
	 * 
	 * The element offset is placed at the currently extracted text by the
	 * Tika parser, and the method adds the relevant namespace, identifier,
	 * annotator and property annotations. The confidence of the annotations
	 * will always be 1.
	 */
	@Override
	public void startElement(String uri, String lName, String qName,
	                         Attributes atts) {
		int num_atts = (atts == null) ? 0 : atts.getLength();
		String name = chooseName(lName, qName);;
		StructureAnnotation ann = new StructureAnnotation(view);

		if (!(uri.endsWith("#") || uri.endsWith("/") || uri.endsWith("=")))
			uri += "#";

		ann.setBegin(textBuffer.length());
		ann.setNamespace(uri);
		ann.setIdentifier(name);
		ann.setAnnotator(TikaAnnotator.URI);
		ann.setConfidence(1.0);

		if (num_atts > 0) {
			FSArray attributes = new FSArray(view, num_atts);

			for (int idx = 0; idx < num_atts; ++idx) {
				Property prop = new Property(view);
				String pName = chooseName(atts.getLocalName(idx),
				    atts.getQName(idx));
				prop.setName(pName);
				prop.setValue(atts.getValue(idx));
				attributes.set(idx, prop);
			}
			ann.setProperties(attributes);
		}

		annotationStack.push(ann);
	}

	/**
	 * Store characters extracted by the Tika parser in a text buffer.
	 */
	@Override
	public void characters(char ch[], int offset, int len) {
		if (len > 0) {
			int idx;
			int end = offset + len;

			for (int pos = offset; pos < end; ++pos) {
				for (idx = greekChars.length - 1; idx >= 0; --idx) {
					if (greekChars[idx] == ch[pos]) {
						int cleanLen = pos - offset;
						textBuffer.append(ch, offset, cleanLen);
						greekCharacters(ch, pos, len - cleanLen);
						return;
					}
				}
				for (idx = dashChars.length - 1; idx >= 0; --idx) {
					if (dashChars[idx] == ch[pos])
						ch[pos] = '-';
				}
			}
			textBuffer.append(ch, offset, len);
		}
	}

	/**
	 * Replace any Greek characters in the character array with their Latin
	 * names.
	 * 
	 * @param ch the character array containing Greek characters
	 * @param offset of the relevant characters in the array
	 * @param len of the characters in the array
	 */
	private void greekCharacters(char ch[], int offset, int len) {
		int idx;
		int end = offset + len;
		boolean replaced = false;

		for (int pos = offset; pos < end; ++pos) {
			replaced = false;

			for (idx = greekChars.length - 1; idx >= 0; --idx) {
				if (greekChars[idx] == ch[pos]) {
					textBuffer.append(greekNames[idx]);
					replaced = true;
					break;
				}
			}
			if (!replaced) {
				for (idx = dashChars.length - 1; idx >= 0; --idx) {
					if (dashChars[idx] == ch[pos])
						ch[pos] = '-';
				}
				textBuffer.append(ch[pos]);
			}
		}
	}

	/**
	 * Set the end of the first matching, started element to offset in the
	 * text being extracted (into a buffer) and add the finished annotation to
	 * the indexes of the CAS view.
	 * 
	 * @throws AssertionError if no matching, started element is found
	 */
	@Override
	public void endElement(String uri, String lName, String qName) {
		StructureAnnotation ann = annotationStack.pop();
		String name = chooseName(lName, qName);

		if (!(uri.endsWith("#") || uri.endsWith("/")))
			uri += "#";

		if (!matchesElement(ann, uri, name)) {
			while (!annotationStack.empty()) {
				logger.log(Level.WARNING, "unmatched " + ann +
				                          " while looking for <{" + uri + "}" +
				                          lName + ": '" + qName + "'>");
				ann = annotationStack.pop();
				if (matchesElement(ann, uri, name))
					break;
			}

			if (!matchesElement(ann, uri, name))
				throw new AssertionError("endElement: no matching Element");
		}

		ann.setEnd(textBuffer.length());
		ann.addToIndexes();
	}

	/**
	 * End a document, setting the SOFA's document text from the text buffer.
	 * 
	 * @throws AssertionError if any unclosed elements remain
	 */
	@Override
	public void endDocument() {
		if (!annotationStack.empty()) {
			while (!annotationStack.empty()) {
				logger
				    .log(Level.SEVERE, "unconsumed " + annotationStack.pop());
			}
			throw new AssertionError("endDocument: unconsumed elements");
		}
		view.setSofaDataString(textBuffer.toString(), "text/plain");
	}

	/**
	 * Return the second string (qName) if the first (lName) is
	 * <code>null</code> or if lName has zero length and qName is not
	 * <code>null</code>.
	 * 
	 * @param lName the first string
	 * @param qName the second string
	 * @return lName if defined and non-empty or if qName is undefined, qName
	 *         otherwise
	 */
	static private String chooseName(String lName, String qName) {
		if (lName == null || (lName.length() == 0 && qName != null))
			return qName;

		return lName;
	}

	/**
	 * Return true if the StructureAnnotation has the given URI and name.
	 * 
	 * @param e the annotation to be matched
	 * @param uri the namespace the annotation should have
	 * @param name the identifier the annotation should have
	 * 
	 * @return true if the strings match the ones set on the annotation
	 */
	static private boolean matchesElement(StructureAnnotation e, String uri,
	                                      String name) {
		return e.getNamespace().equals(uri) && e.getIdentifier().equals(name);
	}

}
