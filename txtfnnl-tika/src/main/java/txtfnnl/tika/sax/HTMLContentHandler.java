package txtfnnl.tika.sax;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.sax.ContentHandlerDecorator;

/**
 * Extracts structured HTML content up to HTML version 5. This handler adds in newlines where
 * appropriate and removes consecutive white-spaces/newlines/tabs to make the input more compact
 * and similar to what is seen on screen. Block elements are followed up by one or two newlines,
 * and <code>br</code> and <code>hr</code> tags trigger placing of additional newlines in the
 * character output.
 * <p>
 * In addition, <code>alt</code> and <code>title</code> attributes (if they are forwarded by the
 * used {@link org.apache.tika.parser.html.HtmlMapper}) are extracted, too, and <code>alt</code>
 * attributes that are the names of Greek letters are transformed into the actual Greek characters.
 * If the Greek letter name is written in title-case, it is transformed to the upper-case
 * character; otherwise, the lower-case character is used. Note that <code>alt</code> attributes
 * are only respected where allowed by the HTML specification, too.
 * 
 * @author Florian Leitner
 */
public class HTMLContentHandler extends ContentHandlerDecorator {
    /* === PARSER STATES === */
    /**
     * An abstract class for implementing the state pattern. The pattern is used to keep track of
     * character stream handling with respect to adding line-feed and whitespace characters in it.
     */
    private abstract class ParserState {
        /**
         * State- and context-dependent character handling. Add newline and/or white-space
         * characters.
         */
        abstract void addSpaces(HTMLContentHandler ctx) throws SAXException;
    }

    /**
     * In the normal state, no characters are added.
     */
    private final ParserState normalState = new ParserState() {
        public void addSpaces(HTMLContentHandler ctx) throws SAXException {}
    };
    /**
     * In the whitespace state, a whitespace is added if other character content was added in the
     * current context.
     */
    private final ParserState whitespaceState = new ParserState() {
        public void addSpaces(HTMLContentHandler ctx) throws SAXException {
            if (ctx.hadContent()) {
                ctx.addCharacters(new char[] { ' ', });
            }
        }
    };
    /**
     * In the newline state, a line-feed is added if other character content was added in the
     * current context.
     */
    private final ParserState newlineState = new ParserState() {
        public void addSpaces(HTMLContentHandler ctx) throws SAXException {
            if (ctx.hadContent()) {
                ctx.addCharacters(new char[] { '\n', });
                ctx.unsetContent();
            }
        }
    };
    /**
     * In the double newline state, two line-feeds are added if other character content was added
     * in the current context.
     */
    private final ParserState doubleNewlineState = new ParserState() {
        public void addSpaces(HTMLContentHandler ctx) throws SAXException {
            if (ctx.hadContent()) {
                ctx.addCharacters(new char[] { '\n', '\n' });
                ctx.unsetContent();
            }
        }
    };
    /* === CONFIGURATION === */
    /**
     * Block elements that should be followed by one newline. In addition, if the CDATA did not
     * have a line-break (followed by any number of space characters) just before opening the
     * element, a single newline is added before handling the next content.
     */
    @SuppressWarnings("serial")
    static final Set<String> ADD_LINEBREAK = Collections.unmodifiableSet(new HashSet<String>() {
        {
            add("caption");
            add("dd");
            add("dt");
            add("fieldset");
            add("figcaption");
            add("legend");
            add("li");
            add("optgroup");
            add("option");
            add("textarea");
            add("tr");
        }
    });
    /**
     * Block elements that should be followed by two newlines. In addition, if the CDATA did not
     * have a line-break (followed by any number of space characters) just before opening the
     * element, a single newline is added before handling the next content.
     */
    @SuppressWarnings("serial")
    static final Set<String> ADD_TWO_LINEBREAKS = Collections
        .unmodifiableSet(new HashSet<String>() {
            {
                add("address");
                add("article");
                add("aside");
                add("blockquote");
                add("center");
                add("dir");
                add("div");
                add("dl");
                add("figure");
                add("footer");
                add("form");
                add("frameset");
                add("h1");
                add("h2");
                add("h3");
                add("h4");
                add("h5");
                add("h6");
                add("head");
                add("header");
                add("hgroup");
                add("hr");
                add("menu");
                add("ol");
                add("p");
                add("pre");
                add("section");
                add("select");
                add("table");
                add("textarea");
                add("title");
                add("ul");
            }
        });
    /**
     * Lower-case Greek letter names mapped to the lower-case Unicode representation of the actual
     * Greek letter.
     */
    @SuppressWarnings("serial")
    static final Map<String, String> GREEK_LOWER = Collections
        .unmodifiableMap(new HashMap<String, String>() {
            {
                put("alpha", "α");
                put("beta", "β");
                put("gamma", "γ");
                put("delta", "δ");
                put("epsilon", "ε");
                put("zeta", "ζ");
                put("eta", "η");
                put("theta", "θ");
                put("iota", "ι");
                put("kappa", "κ");
                put("lambda", "λ");
                put("mu", "μ");
                put("nu", "ν");
                put("xi", "ξ");
                put("omicron", "ο");
                put("pi", "π");
                put("rho", "ρ");
                put("sigma", "σ");
                put("tau", "τ");
                put("upsilon", "υ");
                put("ypsilon", "υ");
                put("phi", "φ");
                put("chi", "χ");
                put("psi", "ψ");
                put("omega", "ω");
            }
        });
    /**
     * Title-case Greek letter names mapped to the upper-case Unicode representation of the actual
     * Greek letter.
     */
    @SuppressWarnings("serial")
    static final Map<String, String> GREEK_UPPER = Collections
        .unmodifiableMap(new HashMap<String, String>() {
            {
                put("Alpha", "Α");
                put("Beta", "Β");
                put("Gamma", "Γ");
                put("Delta", "Δ");
                put("Epsilon", "Ε");
                put("Zeta", "Ζ");
                put("Eta", "Η");
                put("Theta", "Θ");
                put("Iota", "Ι");
                put("Kappa", "Κ");
                put("Lambda", "Λ");
                put("Mu", "Μ");
                put("Nu", "Ν");
                put("Xi", "Ξ");
                put("Omicron", "Ο");
                put("Pi", "Π");
                put("Rho", "Ρ");
                put("Sigma", "Σ");
                put("Tau", "Τ");
                put("Upsilon", "Υ");
                put("Ypsilon", "Υ");
                put("Phi", "Φ");
                put("Chi", "Χ");
                put("Psi", "Ψ");
                put("Omega", "Ω");
            }
        });
    /**
     * Characters that browsers display as space characters
     */
    static final char[] SPACE_CHARS = { ' ', '\n', '\t', '\f', '\r', 0xA0 };
    /* === STATE VARIABLES === */
    /**
     * Special state flag for the title element that is handled by Tika no matter if it is
     * "deactivated" in the mapper or not. This flag is used to detect when the handler is going
     * over the the title element.
     */
    protected boolean inTitle = false;
    /**
     * Context flag indicating if non-space ("content") characters have been handled on the current
     * line.
     */
    private boolean hadContent = false;
    /**
     * Context flag indicating if an image alt contained a Greek character name.
     */
    protected boolean greekChar = false;
    /**
     * The current parser state of this handler.
     */
    private ParserState state = normalState;

    /**
     * @param handler that is being decorated
     * @see org.apache.tika.sax.ContentHandlerDecorator#ContentHandlerDecorator(ContentHandler)
     */
    public HTMLContentHandler(ContentHandler handler) {
        super(handler);
    }

    /* === PUBLIC API === */
    /**
     * Reset all handler states when a new document starts.
     * 
     * @see org.xml.sax.ContentHandler#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        state = normalState;
        inTitle = false;
        hadContent = false;
        greekChar = false;
    }

    /**
     * Insert alt, label and title attributes in the character stream, replacing alt values of img
     * tags that represent a Greek character name with the actual (Greek) character. Special case
     * handling for TITLE and BR elements: Ignore TITLE elements and their character content and
     * force adding a newline on BR elements.
     * 
     * @see org.xml.sax.ContentHandler#startElement(String, String, String, Attributes)
     */
    @Override
    public void startElement(String uri, String localName, String name, Attributes atts)
            throws SAXException {
        // Special cases:
        if ("title".equals(localName)) {
            // - activate the ignore/skip the title element "state"
            inTitle = true;
            return;
        } else if ("br".equals(localName)) {
            // - force a newline character whenever a BR occurs
            addCharacters(new char[] { '\n' });
            return;
        }
        super.startElement(uri, localName, name, atts);
        // Redirect title, label and alt attribute values to characters()
        if (atts.getLength() > 0) {
            handleAttributes(localName, atts);
        }
        // Transition the parser state
        if (ADD_TWO_LINEBREAKS.contains(localName)) {
            setNewlineState();
        } else if (ADD_LINEBREAK.contains(localName)) {
            setNewlineState();
        } else if (!greekChar) {
            // default transition except if the element is an img with an alt
            // attribute value of a Greek character name
            setWhitespaceState();
        }
    }

    /**
     * Detect and handle attribute values that should be processed when a new element starts.
     * Extracts values from alt, title, and label attributes.
     */
    private void handleAttributes(String elementName, Attributes atts) throws SAXException {
        for (final String key : new String[] { "title", "label", "alt" }) {
            String value = atts.getValue(key);
            if (value != null) {
                value = value.trim();
                if (value.length() > 0) {
                    if ("img".equals(elementName) && GREEK_LOWER.containsKey(value.toLowerCase())) {
                        value =
                            (GREEK_UPPER.containsKey(value)) ? GREEK_UPPER.get(value)
                                : GREEK_LOWER.get(value.toLowerCase());
                        greekChar = true;
                    } else {
                        setWhitespaceState();
                    }
                    characters(value.toCharArray(), 0, value.length());
                }
            }
        }
    }

    /**
     * Handle characters (context-dependent). While in the TITLE element, this method does not
     * handle the characters. The state pattern will add space characters where necessary, while
     * any content (i.e., non-space) characters are handled normally.
     * 
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inTitle || length == 0) return;
        final int end = start + length;
        int pos = start;
        length = start;
        // only handle consecutive non-space segments, while the state
        // pattern implementation will handle line-feed and whitespace
        // insertion
        while (pos < end) {
            if (HTMLContentHandler.isDisplayedAsSpace(ch[pos])) {
                if (length > start) {
                    handleCharacters(ch, start, length - start);
                }
                setWhitespaceState();
                start = ++pos;
            } else {
                length = ++pos;
            }
        }
        if (length > start) {
            handleCharacters(ch, start, length - start);
        } else {
            setWhitespaceState();
        }
    }

    /**
     * Transition to double newline or newline states for block elements, and to whitespace states
     * otherwise. Special case handling for TITLE and BR elements: ignore them.
     * 
     * @see org.xml.sax.ContentHandler#endElement(String, String, String)
     */
    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        if (inTitle && "title".equals(localName)) {
            inTitle = false;
        } else if (!"br".equals(localName)) {
            super.endElement(uri, localName, name);
            // Transition the parser state
            if (greekChar && "img".equals(localName)) {
                greekChar = false; // images of Greek characters do not
                                   // trigger
                                   // the whitespace state
            } else if (ADD_TWO_LINEBREAKS.contains(localName)) {
                setDoubleNewlineState();
            } else if (ADD_LINEBREAK.contains(localName)) {
                setNewlineState();
            } else {
                setWhitespaceState();
            }
        }
    }

    /* === PROTECTED API === */
    /**
     * Return true if the character would be displayed as space in the browser.
     * 
     * @param ch any character
     * @return <b>true</b> if <code>ch</code> is a space, line-feed, (horizontal) tab, or carriage
     *         return character
     */
    static boolean isDisplayedAsSpace(char ch) {
        for (final char sc : SPACE_CHARS)
            if (sc == ch) return true;
        return false;
    }

    // character handling methods
    /**
     * Handle characters directly, ignoring state or context.
     * 
     * @param ch characters to send to the wrapped content handler
     * @throws SAXException
     */
    void addCharacters(char[] ch) throws SAXException {
        super.characters(ch, 0, ch.length);
    }

    /**
     * Handle characters using state and applying context changes and transitions.
     * 
     * @param ch characters to send to the wrapped content handler
     * @param start of the relevant characters
     * @param length of the relevant character string
     * @throws SAXException
     */
    void handleCharacters(char[] ch, int start, int length) throws SAXException {
        state.addSpaces(this);
        super.characters(ch, start, length);
        hadContent = true;
        state = normalState;
    }

    // parser state methods
    /**
     * Try to transition to the whitespace state. Transitioning is only possible from the normal
     * state.
     */
    void setWhitespaceState() {
        if (state == normalState) {
            state = whitespaceState;
        }
    }

    /**
     * Try to transition to the newline state. Transitioning is always possible unless the double
     * newline state has been reached.
     */
    void setNewlineState() {
        if (state != doubleNewlineState) {
            state = newlineState;
        }
    }

    /**
     * Try to transition to the double newline state. Transitioning to this state is always
     * possible.
     */
    void setDoubleNewlineState() {
        state = doubleNewlineState;
    }

    // content context methods
    /**
     * Deactivate the content context. This event should occur whenever a newline is handled. Note
     * that adding newlines from BR elements should be ignored.
     */
    void unsetContent() {
        hadContent = false;
    }

    /**
     * Determine if (non-space) content has been handled on the current line.
     * 
     * @return <b>true</b> if content had been handled
     */
    boolean hadContent() {
        return hadContent;
    }
}
