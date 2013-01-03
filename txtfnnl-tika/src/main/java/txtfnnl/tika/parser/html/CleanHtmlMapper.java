package txtfnnl.tika.parser.html;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.parser.html.HtmlMapper;

/**
 * A HTML mapper that maps <b>all</b> HTML elements (Tika drops, e.g., sub- and super-script, among
 * many others). Allowed tags are essentially all HTML 4 and 5 tags, plus some "frequent" HTML 2
 * and 3 tags, all core attributes, and attributes that can contain additional text or references
 * to such.
 * 
 * @author Florian Leitner
 */
public class CleanHtmlMapper implements HtmlMapper {
  public static final HtmlMapper INSTANCE = new CleanHtmlMapper();
  /** All HTML tags are considered "safe" tags. */
  private static final Map<String, String> SAFE_ELEMENTS = Collections
      .unmodifiableMap(getSafeElements());
  /** Only header content should be discarded. */
  private static final Set<String> DISCARDABLE_ELEMENTS = Collections
      .unmodifiableSet(getDiscardableElements());
  /** Core attributes are always considered safe. */
  private static final Set<String> CORE_ATTRIBUTES = Collections
      .unmodifiableSet(getCoreAttributes());
  /**
   * Element-specific attributes are considered safe if they can contain text or URL references and
   * are allowed on that element.
   */
  private static final Map<String, Set<String>> SAFE_ATTRIBUTES = Collections
      .unmodifiableMap(getSafeAttributes());

  @SuppressWarnings("serial")
  private static Map<String, String> getSafeElements() {
    return new HashMap<String, String>() {
      {
        put("A", "a");
        put("ABBR", "abbr");
        put("ACRONYM", "acronym");
        put("ADDRESS", "address");
        put("APPLET", "applet");
        put("AREA", "area");
        put("ARTICLE", "article");
        put("ASIDE", "aside");
        put("AUDIO", "audio");
        put("B", "strong");
        put("BB", "bb");
        put("BASE", "base");
        put("BASEFONT", "basefont");
        put("BDI", "bdi");
        put("BDO", "bdo");
        put("BIG", "big");
        put("BLINK", "blink");
        put("BLOCKQUOTE", "blockquote");
        put("BODY", "body");
        put("BR", "br");
        put("BUTTON", "button");
        put("CANVAS", "canvas");
        put("CAPTION", "caption");
        put("CENTER", "center");
        put("CITE", "cite");
        put("CODE", "code");
        put("COL", "col");
        put("COLGROUP", "colgroup");
        put("COMMAND", "command");
        put("DATALIST", "ul");
        put("DD", "dd");
        put("DEL", "del");
        put("DETAILS", "details");
        put("DFN", "dfn");
        put("DIR", "ul");
        put("DIV", "div");
        put("DL", "dl");
        put("DT", "dt");
        put("EM", "em");
        put("EMBED", "object");
        put("FIELDSET", "fieldset");
        put("FIGCAPTION", "figcaption");
        put("FIGURE", "figure");
        put("FONT", "font");
        put("FOOTER", "footer");
        put("FORM", "form");
        put("FRAME", "frame");
        put("FRAMESET", "frameset");
        put("H1", "h1");
        put("H2", "h2");
        put("H3", "h3");
        put("H4", "h4");
        put("H5", "h5");
        put("H6", "h6");
        put("HEAD", "head");
        put("HEADER", "header");
        put("HGROUP", "hgroup");
        put("HR", "hr");
        put("HTML", "html");
        put("I", "em");
        put("IFRAME", "iframe");
        put("IMG", "img");
        put("INPUT", "input");
        put("INS", "ins");
        put("KEYGEN", "keygen");
        put("KBD", "code");
        put("LABEL", "label");
        put("LEGEND", "legend");
        put("LI", "li");
        put("LINK", "link");
        put("LISTING", "pre");
        put("MAP", "map");
        put("MARK", "mark");
        put("MARQUEE", "marquee");
        put("MENU", "ul");
        put("META", "meta");
        put("METER", "meter");
        put("NAV", "nav");
        put("NOBR", "nobr");
        put("NOEMBED", "noembed");
        put("NOFRAMES", "noframes");
        put("NOSCRIPT", "noscript");
        put("OBJECT", "object");
        put("OL", "ol");
        put("OPTGROUP", "optgroup");
        put("OPTION", "li");
        put("OUTPUT", "output");
        put("P", "p");
        put("PARAM", "param");
        put("PLAINTEXT", "pre");
        put("PRE", "pre");
        put("PROGRESS", "progress");
        put("Q", "q");
        put("RP", "rp");
        put("RT", "rt");
        put("RUBY", "ruby");
        put("S", "del");
        put("SAMP", "code");
        put("SCRIPT", "script");
        put("SECTION", "section");
        put("SELECT", "ul");
        put("SMALL", "small");
        put("SOURCE", "source");
        put("SPAN", "span");
        put("STRIKE", "del");
        put("STRONG", "strong");
        put("STYLE", "style");
        put("SUB", "sub");
        put("SUMMARY", "summary");
        put("SUP", "sup");
        put("TABLE", "table");
        put("TBODY", "tbody");
        put("TD", "td");
        put("TEXTAREA", "textarea");
        put("TFOOT", "tfoot");
        put("TH", "th");
        put("THEAD", "thead");
        put("TITLE", "title");
        put("TIME", "time");
        put("TR", "tr");
        put("TRACK", "track");
        put("TT", "code");
        put("U", "u");
        put("UL", "ul");
        put("VAR", "var");
        put("VIDEO", "video");
        put("WBR", "wbr");
      }
    };
  }

  @SuppressWarnings("serial")
  private static Set<String> getDiscardableElements() {
    return new HashSet<String>() {
      {
        add("COMMAND"); // may be found in HTML5 headers (only IE)
        add("SCRIPT");
        add("STYLE");
      }
    };
  }

  @SuppressWarnings("serial")
  private static Set<String> getCoreAttributes() {
    return new HashSet<String>() {
      {
        add("class");
        add("id");
        add("style");
        add("title");
        add("dir");
        add("lang");
        add("xml:lang");
      }
    };
  }

  @SuppressWarnings("serial")
  private static Map<String, Set<String>> getSafeAttributes() {
    return new HashMap<String, Set<String>>() {
      {
        put("a", attrSet("charset", "type", "name", "href", "hreflang", "rel", "rev"));
        put("applet", attrSet("code", "archive", "codebase", "alt"));
        put("area", attrSet("href", "nohref", "alt"));
        put("audio", attrSet("src"));
        put("base", attrSet("href"));
        put("bdo", attrSet("dir"));
        put("blockquote", attrSet("cite"));
        put("command", attrSet("icon", "label"));
        put("del", attrSet("cite", "datetime"));
        put("form", attrSet("action", "method", "accept", "accept-charset", "name"));
        put("frame", attrSet("longdesc", "name", "src"));
        put("head", attrSet("profile"));
        put("iframe", attrSet("longdesc", "name", "src", "srcdoc"));
        put("img", attrSet("src", "alt", "longdesc"));
        put("input", attrSet("alt"));
        put("ins", attrSet("cite", "datetime"));
        put("link", attrSet("charset", "href", "hreflang", "type", "rel", "rev", "media"));
        put("map", attrSet("name"));
        put("menu", attrSet("label"));
        put("meta", attrSet("content", "http-equiv", "name", "scheme"));
        put("object",
            attrSet("data", "name", "type", "usemap", "archive", "codebase", "codetype",
                "declare", "classid", "standby", "src"));
        put("optgroup", attrSet("label"));
        put("option", attrSet("label"));
        put("param", attrSet("id", "name", "value", "valuetype", "type"));
        put("q", attrSet("cite"));
        put("source", attrSet("src", "type", "media"));
        put("time", attrSet("datetime", "pubdate"));
        put("track", attrSet("src", "kind", "label", "srclang"));
        put("video", attrSet("poster", "src"));
      }
    };
  }

  /** Transform any number of String parameters into a Set. */
  private static Set<String> attrSet(String... attrs) {
    Set<String> result = new HashSet<String>();
    for (String attr : attrs)
      result.add(attr);
    return result;
  }

  /** {@inheritDoc} */
  public String mapSafeElement(String name) {
    return SAFE_ELEMENTS.get(name);
  }

  /** {@inheritDoc} */
  public boolean isDiscardElement(String name) {
    return DISCARDABLE_ELEMENTS.contains(name);
  }

  /** {@inheritDoc} */
  public String mapSafeAttribute(String elementName, String attributeName) {
    Set<String> safeAttrs = SAFE_ATTRIBUTES.get(elementName.toLowerCase());
    String normalName = attributeName.toLowerCase();
    if (CORE_ATTRIBUTES.contains(normalName) ||
        ((safeAttrs != null) && safeAttrs.contains(normalName))) {
      return attributeName;
    } else {
      return null;
    }
  }
}
