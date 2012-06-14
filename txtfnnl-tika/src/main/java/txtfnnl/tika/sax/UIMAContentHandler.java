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
import txtfnnl.uima.tcas.TextAnnotation;

/**
 * An event handler that specifically "works" with the UIMA AE philosophy.
 * 
 * This special SAX handler is intended to be used by the
 * {@link txtfnnl.tika.uima.TikaAnnotator} to extract content for UIMA.
 * 
 * @author Florian Leitner
 */
public class UIMAContentHandler extends ContentHandlerDecorator {

	/** The CAS view that will be populated by the handler. */
	private JCas view = null;

	/** The characters received from the parser. */
	private StringBuffer textBuffer;

	/** Started markup elements that are not yet ended (closed). */
	private Stack<TextAnnotation> annotationStack;

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
		annotationStack = new Stack<TextAnnotation>();
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
		TextAnnotation ann = new TextAnnotation(view);

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
		if (len > 0)
			textBuffer.append(ch, offset, len);
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
		TextAnnotation ann = annotationStack.pop();
		String name = chooseName(lName, qName);

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
	 * Return true if the TextAnnotation has the given URI and name.
	 * 
	 * @param e the annotation to be matched
	 * @param uri the namespace the annotation should have
	 * @param name the identifier the annotation should have
	 * 
	 * @return true if the strings match the ones set on the annotation
	 */
	static private boolean matchesElement(TextAnnotation e, String uri,
	                                      String name) {
		return e.getNamespace().equals(uri) && e.getIdentifier().equals(name);
	}

}
