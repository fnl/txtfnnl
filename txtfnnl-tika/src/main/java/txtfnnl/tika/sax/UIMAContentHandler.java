/**
 * 
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
 * An event handler that ...
 * 
 * TODO
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
	 * Create a new Element starting at the current offset in the text being
	 * extracted by the Tika parser, and add the namespace, name, and
	 * attribute annotations.
	 * 
	 * Stores the started Element on an internal element stack until it can be
	 * closed (by endElement()).
	 */
	@Override
	public void startElement(String uri, String lName, String qName,
	                         Attributes atts) {
		int num_atts = atts.getLength();
		String name = (lName != null && lName.length() > 0) ? lName : qName;
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
				String propName = atts.getLocalName(idx);

				if (propName == null || propName.length() < 0)
					propName = atts.getQName(idx);

				prop.setName(propName);
				prop.setValue(atts.getValue(idx));
				attributes.set(idx, prop);
			}

			ann.setProperties(attributes);
		}

		annotationStack.push(ann);
	}

	/**
	 * Store characters extracted by the Tika parser in the text buffer.
	 */
	@Override
	public void characters(char ch[], int offset, int len) {
		if (len > 0)
			textBuffer.append(ch, offset, len);
	}

	/**
	 * Set the end of the first matching started Element on the stack of open
	 * elements to offset in the text being extracted and add the Element to
	 * the indexes of the CAS view.
	 */
	@Override
	public void endElement(String uri, String lName, String qName) {
		TextAnnotation ann = annotationStack.pop();
		String name = (lName != null && lName.length() > 0) ? lName : qName;

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
	 * End a document, setting the document text on the handler's SOFA to the
	 * content extracted by the Tika parser.
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
	 * Return true if the TextAnnotation has the given URI and name.
	 * 
	 * @param e the annotation to be matched
	 * @param uri the namespace the annotation should have
	 * @param name the identifier the annotation should have
	 * 
	 * @return true if the strings match the ones set on the annotation
	 */
	private boolean matchesElement(TextAnnotation e, String uri, String name) {
		return e.getNamespace().equals(uri) && e.getIdentifier().equals(name);
	}

}
