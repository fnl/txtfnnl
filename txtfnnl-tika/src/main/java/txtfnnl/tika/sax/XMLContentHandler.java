package txtfnnl.tika.sax;

import org.apache.tika.sax.ContentHandlerDecorator;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extract XML CDATA content into a simple structured format.
 * 
 * Each element's content is separated by a newline and indented by tabs
 * according to the depth of the element.
 * 
 * @author Florian Leitner
 */
public class XMLContentHandler extends ContentHandlerDecorator {

	/**
	 * Track the tree level/element depth.
	 */
	int level;

	/**
	 * Track the tree level/element depth changes.
	 */
	int delta;

	/**
	 * Will be true if a start/end element tag was parsed and no characters have
	 * been produced since that event.
	 */
	boolean indent;

	/**
	 * @param handler to decorate
	 */
	public XMLContentHandler(ContentHandler handler) {
		super(handler);
		level = 0;
		delta = 0;
		indent = false;
	}

	/* (non-Javadoc)
	 * 
	 * @see org.apache.tika.sax.ContentHandlerDecorator#startDocument() */
	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		level = 0;
		delta = 0;
		indent = false;
	}

	/* (non-Javadoc)
	 * 
	 * @see org.apache.tika.sax.ContentHandlerDecorator#endDocument() */
	@Override
	public void endDocument() throws SAXException {
		super.endDocument();
		super.characters(new char[] {
			'\n'
		}, 0, 1);
	}

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tika.sax.ContentHandlerDecorator#startElement(java.lang.String
	 * , java.lang.String, java.lang.String, org.xml.sax.Attributes) */
	@Override
	public void startElement(String uri, String localName, String name,
	                         Attributes atts) throws SAXException {
		super.startElement(uri, localName, name, atts);

		if (delta < -1) {
			super.characters(new char[] {
				'\n'
			}, 0, 1);
			for (int i = 0; i < level; ++i)
				super.characters(new char[] {
					'\t'
				}, 0, 1);
			delta = 0;
		}

		delta += 1;
		level += 1;
		indent = true;
	}

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.tika.sax.ContentHandlerDecorator#endElement(java.lang.String,
	 * java.lang.String, java.lang.String) */
	@Override
	public void endElement(String uri, String localName, String name)
	        throws SAXException {
		super.endElement(uri, localName, name);
		if (delta > 0)
			delta = 0;
		delta -= 1;
		level -= 1;
		indent = true;
	}

	/**
	 * Print characters if they have other content than spaces, newlines, or
	 * tabs and put them on a new, indented line if they are from another
	 * element than the characters printed before.
	 */
	@Override
	public void characters(char[] ch, int start, int length)
	        throws SAXException {
		boolean content = false;

		for (int i = start + length - 1; i >= start; --i) {
			if (ch[i] != ' ' && ch[i] != '\n' && ch[i] != '\t') {
				content = true;
				break;
			}
		}

		if (content) {
			if (indent) {
				super.characters(new char[] {
					'\n'
				}, 0, 1);
				for (int i = 0; i < level; ++i)
					super.characters(new char[] {
						'\t'
					}, 0, 1);
				indent = false;
			}

			super.characters(ch, start, length);
		}
	}

	/**
	 * Drop ignorable characters from the character stream, ie., do nothing.
	 */
	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
	        throws SAXException {}

}
