/**
 * 
 */
package txtfnnl.tika.sax;

import org.apache.tika.sax.ContentHandlerDecorator;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A content handler decorator that translates Greek letters to their spelled-out, Latin names.
 * Upper-case letters are written capitalized (Alpha, Beta, ...), while lower-case letters are
 * written all small letters (alpha, beta, ...). All non-composite Greek characters plus the Latin
 * small sharp S character are replaced with their Latin names (sharp S "ß" gets replaced with
 * "beta"). This includes all default capital letters (U+0391 - U+03A9), small letters (U+03B1 -
 * U03C9, ie., incl. final sigma), Kai and kai (U+03CF, U+03D7) and the variant letter forms for
 * beta, theta, phi, pi, kappa, rho, and Theta.
 * 
 * @author Florian Leitner
 */
public class GreekLetterContentHandler extends ContentHandlerDecorator {
    static final char[] GREEK_CHARS = new char[] {
        // Upper
        '\u0391', '\u0392', '\u0393', '\u0394', '\u0395', '\u0396',
        // 6
        '\u0397', '\u0398', '\u0399', '\u039A', '\u039B', '\u039C',
        // 12
        '\u039D', '\u039E', '\u039F', '\u03A0', '\u03A1', '\u03A3',
        // 18
        '\u03A4', '\u03A5', '\u03A6', '\u03A7', '\u03A8', '\u03A9',
        // Lower
        // U+00DF: latin small letter sharp S (more often used as small beta
        // rather than the appropriate character use)
        '\u03B1', '\u03B2', '\u00DF', '\u03B3', '\u03B4', '\u03B5', '\u03B6',
        // 6
        '\u03B7', '\u03B8', '\u03B9', '\u03BA', '\u03BB', '\u03BC',
        // 12
        // U+03C3: final sigma
        '\u03BD', '\u03BE', '\u03BF', '\u03C0', '\u03C1', '\u03C2', '\u03C3',
        // 18
        '\u03C4', '\u03C5', '\u03C6', '\u03C7', '\u03C8', '\u03C9',
        // Variants (6)
        '\u03CF', '\u03D0', '\u03D1', '\u03D5', '\u03D6', '\u03D7',
        // More Variants (6)
        '\u03F0', '\u03F1', '\u03F4', };
    static final char[][] GREEK_NAMES = new char[][] {
        // Upper
        "Alpha".toCharArray(),
        "Beta".toCharArray(),
        "Gamma".toCharArray(),
        "Delta".toCharArray(),
        "Epsilon".toCharArray(),
        "Zeta".toCharArray(),
        // 6
        "Eta".toCharArray(),
        "Theta".toCharArray(),
        "Iota".toCharArray(),
        "Kappa".toCharArray(),
        "Lambda".toCharArray(),
        "Mu".toCharArray(),
        // 12
        "Nu".toCharArray(),
        "Xi".toCharArray(),
        "Omicron".toCharArray(),
        "Pi".toCharArray(),
        "Rho".toCharArray(),
        "Sigma".toCharArray(),
        // 18
        "Tau".toCharArray(),
        "Upsilon".toCharArray(),
        "Phi".toCharArray(),
        "Chi".toCharArray(),
        "Psi".toCharArray(),
        "Omega".toCharArray(),
        // lower
        "alpha".toCharArray(),
        "beta".toCharArray(),
        "beta".toCharArray(), // latin small letter sharp S
        "gamma".toCharArray(),
        "delta".toCharArray(),
        "epsilon".toCharArray(),
        "zeta".toCharArray(),
        // 6
        "eta".toCharArray(), "theta".toCharArray(), "iota".toCharArray(),
        "kappa".toCharArray(),
        "lambda".toCharArray(),
        "mu".toCharArray(),
        // 12
        "nu".toCharArray(), "xi".toCharArray(), "omicron".toCharArray(),
        "pi".toCharArray(),
        "rho".toCharArray(),
        "sigma".toCharArray(),
        "sigma".toCharArray(), // final sigma
        // 18
        "tau".toCharArray(), "upsilon".toCharArray(), "phi".toCharArray(), "chi".toCharArray(),
        "psi".toCharArray(), "omega".toCharArray(),
        // Variants (6)
        "Kai".toCharArray(), "beta".toCharArray(), "theta".toCharArray(), "phi".toCharArray(),
        "pi".toCharArray(), "kai".toCharArray(),
        // More Variants (3)
        "kappa".toCharArray(), "rho".toCharArray(), "Theta".toCharArray() };
    static {
        assert GREEK_CHARS.length == GREEK_NAMES.length;
    }

    /**
     * Decorate another handler with this one.
     * 
     * @param handler to decorate
     */
    public GreekLetterContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Normalize Greek characters to their spelled-out Latin (ASCII) letter form.
     */
    @Override
    public void characters(char ch[], int offset, int len) throws SAXException {
        if (len > 0) {
            int idx;
            int end = offset + len;
            for (int pos = offset; pos < end; ++pos) {
                for (idx = GREEK_CHARS.length; idx-- > 0;) {
                    if (GREEK_CHARS[idx] == ch[pos]) {
                        int cleanLen = pos - offset;
                        if (cleanLen > 0) super.characters(ch, offset, cleanLen);
                        super.characters(GREEK_NAMES[idx], 0, GREEK_NAMES[idx].length);
                        offset = pos + 1;
                        len -= cleanLen + 1;
                        break;
                    }
                }
            }
            if (len > 0) super.characters(ch, offset, len);
        }
    }
}
