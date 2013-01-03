/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AnalysisEngineFactory;

import txtfnnl.subprocess.ReadlineRuntime;
import txtfnnl.subprocess.RuntimeKiller;
import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.SyntaxAnnotation;
import txtfnnl.uima.utils.Offset;
import txtfnnl.uima.utils.UIMAUtils;

/**
 * A wrapper for the link-parser LinkGrammar executable. For this wrapper to work, the
 * <code>link-parser</code> executable must have the following properties (as provided at least by
 * the 4.7 releases):
 * <ol>
 * <li>The first (and only) argument it needs to support is the dictionary path or language code.</li>
 * <li>It must support the <code>constituents=3</code> option, and/or should produce single-line,
 * parenthesis-based constituent tree expressions for the single-line sentence inputs it will
 * receive.</li>
 * <li>It must write four lines of setup status information (usually, echoing the constituents,
 * verbosity, graphics, and timeout options).</li>
 * <li>It must write an empty line after each constituent tree expression.</li>
 * <li>It must not write an other data to the standard output than specified here.</li>
 * <li>It should support the <code>verbosity=0</code> option or at least not break when used.</li>
 * <li>It should support the <code>graphics=0</code> option or at least not break when used.</li>
 * <li>It should support the <code>timeout=</code>int option or at least not break when used.</li>
 * <li>Ideally, the set timeout should be respected.</li>
 * </ol>
 * 
 * @author Florian Leitner
 */
class LinkParser extends ReadlineRuntime<String> {
  /** Flag indicating the first read of the runtime's input stream. */
  boolean first;
  /** The timeout setting (in seconds) for this parser. */
  final int timeout;

  /**
   * Create a new link-parser process fork.
   * 
   * @param dictPath the path to/language for the parser dictionaries
   * @param timeout for the LG parser on a sentence, in seconds (NB: the process gets killed if
   *        twice that time has passed)
   * @param logger to handle all error messages
   * @throws IOException on failure
   */
  public LinkParser(String dictPath, int timeout, Logger logger) throws IOException {
    super(new String[] { "link-parser", dictPath, "-constituents=3", "-verbosity=0",
        "-graphics=0", "-timeout=" + timeout }, logger);
    logger.log(Level.INFO, "started a link-parser process with dict=''{0}'' and timeout={1}",
        new Object[] { dictPath, timeout });
    this.timeout = timeout;
    first = true;
  }

  @Override
  protected String parseResponse() throws IOException {
    String result;
    final RuntimeKiller killer = new RuntimeKiller(this, timeout * 2);
    killer.start();
    if (first) {
      // status/setup messages - despite verbosity=0, the parsers
      // seems to be rather talkative still :)
      // essentially, each command line option is echoed once
      // to STDOUT instead of STDERR...
      first = false;
      for (int i = 0; i < 4; ++i) {
        this.log(Level.FINE, readLine());
      }
    } else {
      // read an empty line
      final String empty = readLine();
      if (empty == null || empty.length() > 0) {
        this.log(Level.WARNING, "expected an empty line from the parser but got: '" + empty + "'");
      }
    }
    result = readLine();
    killer.doNotKill();
    return result;
  }
}

/**
 * An annotator that adds constituent spans to all sentences. The spans are identified by the
 * LinkGrammar link-parser. The :identifiers for the (constituent span) syntax annotations are the
 * Penn Treebank tags.
 * <p>
 * For this annotator to work, the <code>link-parser</code> executable has to be on the global
 * <code>PATH</code>.
 * 
 * @author Florian Leitner
 */
public class LinkGrammarAnnotator extends JCasAnnotator_ImplBase {
  /**
   * A tree node implementation to parse constituent tree expressions.
   * <p>
   * Constituent tree expressions are S-expression-like trees, and can be seen in the interactive
   * link-parser shell after activating constituents (use <code>!constituents=1</code> or
   * <code>!constituents=3</code>).
   * <p>
   * Unless the link-parser changed its output representation (and you are getting RuntimeErrors),
   * you should not have to be interested in this class.
   * 
   * @author Florian Leitner
   */
  static class ConstituentNode implements Iterable<ConstituentNode> {
    /** The actual text if a leaf node or the tag otherwise. */
    public final String data;
    private ConstituentNode parent;
    private LinkedList<ConstituentNode> children;
    private Offset span;
    private static final LinkedList<ConstituentNode> EMPTY_LIST = new LinkedList<ConstituentNode>();

    /**
     * Create a tree from the constituent expression.
     * 
     * @param cExpression of constituents (similar to a sexp)
     * @return the root node (with the special tag data="ROOT")
     */
    public static ConstituentNode parse(String cExpression) {
      final ConstituentNode root = new ConstituentNode("ROOT");
      StringBuilder sb = new StringBuilder();
      for (int idx = 0; idx < cExpression.length(); ++idx) {
        final char c = cExpression.charAt(idx);
        if (c == '(') {
          idx += ConstituentNode.parseRec(cExpression.substring(idx + 1), root);
        } else if (c == ')') throw new RuntimeException("parse error at '" +
            cExpression.substring(0, idx) + ">>>)<<<" + cExpression.substring(idx + 1) + "'");
        else if (c == ' ') {
          if (sb.length() > 0) {
            root.addChild(new ConstituentNode(sb.toString()));
            sb = new StringBuilder();
          }
        } else {
          sb.append(c);
        }
      }
      return root;
    }

    /**
     * Parse all contained expression to child nodes of the given parent, returning the offset up
     * to which the expression could be parsed.
     * 
     * @param cExpression to parse
     * @param parent node to attach the child nodes to
     * @return the offset into the cExpression that could be parsed
     */
    static int parseRec(String cExpression, ConstituentNode parent) {
      ConstituentNode node = null;
      StringBuilder sb = new StringBuilder();
      for (int idx = 0; idx < cExpression.length(); ++idx) {
        final char c = cExpression.charAt(idx);
        if (c == '(') {
          idx += ConstituentNode.parseRec(cExpression.substring(idx + 1), node);
        } else if (c == ')') {
          if (sb.length() > 0) {
            node = ConstituentNode.buildCNode(node, sb, parent);
          }
          return idx + 1;
        } else if (c == ' ') {
          if (sb.length() > 0) {
            node = ConstituentNode.buildCNode(node, sb, parent);
            sb = new StringBuilder();
          }
        } else {
          sb.append(c);
        }
      }
      throw new RuntimeException("unclosed constituent '" + cExpression + "'");
    }

    private static ConstituentNode buildCNode(ConstituentNode node, StringBuilder sb,
        ConstituentNode parent) {
      if (node == null) {
        node = new ConstituentNode(sb.toString());
        parent.addChild(node);
      } else {
        node.addChild(new ConstituentNode(sb.toString()));
      }
      return node;
    }

    @SuppressWarnings("unused")
    private ConstituentNode() {
      throw new AssertionError("illegal constructor use");
    }

    /**
     * Create an "empty" node with no parent or children.
     * 
     * @param data for this node
     */
    public ConstituentNode(String data) {
      this.data = data;
      children = null;
      parent = null;
    }

    /** Return true if the node has no children. */
    public boolean isLeaf() {
      return children == null || children.size() == 0;
    }

    /** Return true if the node has no parent. */
    public boolean isRoot() {
      return parent == null;
    }

    /* public boolean isFirstChild() { return !isRoot() &&
     * parent.children.getFirst() == this; }
     * 
     * public boolean isLastChild() { return !isRoot() &&
     * parent.children.getLast() == this; } */
    /** Get the parent node or <code>null</code>. */
    public ConstituentNode getParent() {
      return parent;
    }

    /**
     * Get the child node at the given index.
     * 
     * @param idx of the child node to fetch
     * @return the child node
     * @throws UnsupportedOperationException if the node is a leaf
     */
    public ConstituentNode getChild(int idx) {
      if (!isLeaf()) return children.get(idx);
      else throw new UnsupportedOperationException("leaf node");
    }

    /** Get the first child node. */
    public ConstituentNode getFirst() {
      return children.getFirst();
    }

    /** Get the last child node. */
    public ConstituentNode getLast() {
      return children.getLast();
    }

    /**
     * Set the {@link Offset} (in the SOFA) for this node.
     * 
     * @throws UnsupportedOperationException if not a leaf node
     * @throws IllegalStateException if already set
     */
    public void setOffset(Offset span) {
      if (!isLeaf()) throw new UnsupportedOperationException("not a leaf node");
      if (this.span != null) throw new IllegalStateException("span Offset already set");
      this.span = span;
    }

    /**
     * Get the {@link Offset} (in the SOFA) for this node.
     * 
     * @throws IllegalStateException if the leaf node offsets have not been set beforehand
     */
    public Offset getOffset() {
      if (!isLeaf() && span == null) {
        ConstituentNode start = getFirst();
        ConstituentNode end = getLast();
        while (!start.isLeaf()) {
          start = start.getFirst();
        }
        while (!end.isLeaf()) {
          end = end.getLast();
        }
        final int s = start.getOffset().start();
        final int e = end.getOffset().end();
        if (s != e) {
          span = new Offset(s, e);
        } else {
          span = new Offset(s);
        }
      } else if (isLeaf() && span == null) throw new IllegalStateException("offset not set");
      return span;
    }

    /**
     * Add a child node to this node.
     * 
     * @throws IllegalArgumentException if the child already has a parent
     */
    public void addChild(ConstituentNode child) {
      if (child.parent == null) {
        if (children == null) {
          children = new LinkedList<ConstituentNode>();
        }
        child.parent = this;
        children.add(child);
      } else if (child.parent == this) {
        // already added
      } else throw new IllegalArgumentException("child already has parent: " + child.parent);
    }

    /** Return the number of children for this node. */
    public int numChildren() {
      if (!isLeaf()) return children.size();
      else return 0;
    }

    /** Walk the <b>leaf</b> nodes in their index order. */
    public static Iterator<ConstituentNode> walk(final ConstituentNode node) {
      return new Iterator<ConstituentNode>() {
        private final Stack<Iterator<ConstituentNode>> stack = new Stack<Iterator<ConstituentNode>>();
        private Iterator<ConstituentNode> iter = node.iterator();

        public boolean hasNext() {
          if (iter.hasNext()) return true;
          else if (!stack.isEmpty()) {
            while (!stack.isEmpty()) {
              iter = stack.pop();
              if (iter.hasNext()) return true;
            }
          }
          return false;
        }

        public ConstituentNode next() {
          if (!hasNext()) throw new NoSuchElementException();
          else {
            final ConstituentNode elm = iter.next();
            if (elm.isLeaf()) return elm;
            else {
              stack.add(iter);
              iter = elm.iterator();
              return next();
            }
          }
        }

        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    /** Iterate over all nodes in this branch. */
    public Iterator<ConstituentNode> iterator() {
      if (!isLeaf()) return children.iterator();
      else return EMPTY_LIST.iterator();
    }

    /** Create a constituent expression for this branch. */
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      final boolean inside = (!isLeaf() && !isRoot());
      if (inside) {
        sb = new StringBuilder("(");
      }
      sb.append(data);
      for (final ConstituentNode child : this) {
        sb.append(' ');
        sb.append(child.toString());
      }
      if (inside) {
        sb.append(')');
      }
      return sb.toString();
    }
  }

  /**
   * All Penn Treebank tags that can be used to annotate constituent spans.
   */
  @SuppressWarnings("serial")
  public static final Map<String, String> CONSTITUENT_TAGS = Collections
      .unmodifiableMap(new HashMap<String, String>() {
        {
          // clauses
          put("S", "Sentence");
          put("SBAR", "SubordinatingConjunction");
          put("SBARQ", "DirectQuestion");
          put("SINV", "InvertedDeclarativeSentence");
          put("SQ", "InvertedQuestion");
          // phrases
          put("ADJP", "AdjectivePhrase");
          put("ADVP", "AdverbPhrase");
          put("CONJP", "ConjunctionPhrase");
          put("FRAG", "Fragment");
          put("INTJ", "Interjection");
          put("LST", "ListMarker");
          put("NAC", "NotAConstituent");
          put("NP", "NounPhrase");
          put("NX", "NounPhraseHead");
          put("PP", "PrepositionalPhrase");
          put("PRN", "Parenthetical");
          put("PRT", "Participle");
          put("QP", "QuantifierPhrase");
          put("RRC", "ReducedRelativeClause");
          put("UCP", "UnlikeCoordinatedPhrase");
          put("VP", "VerbPhrase");
          put("WHADJP", "WhAdjectivePhrase");
          put("WHADVP", "WhAdverbPhrase");
          put("WHNP", "WhNounPhrase");
          put("WHPP", "WhPrepositionalPhrase");
          put("X", "Unknown");
        }
      });
  /** The public URI of this annotator. */
  public static final String URI = LinkGrammarAnnotator.class.getName();
  /**
   * The namespace to use for the constituent span {@link SyntaxAnnotation}s made by this AE.
   * <p>
   * Note that the identifier feature of the annotations is the Penn Treebank phrase tag (see
   * {@link #CONSTITUENT_TAGS}).
   */
  public static final String NAMESPACE = "http://bulba.sdsu.edu/jeanette/thesis/PennTags.html#";
  /**
   * The path to the dictionaries used by the LinkGrammar parser.
   * <p>
   * If the Link Grammar parser was installed in the default location ( <code>/usr/local</code> ),
   * and the language is English, it is not necessary to set this parameter. I.e., by default, the
   * English dictionaries are used. Therefore, the default value of this parameter is
   * <code>/usr/local/share/link-grammar/en</code>.
   */
  public static final String PARAM_DICTIONARIES_PATH = "DictionariesPath";
  @ConfigurationParameter(name = PARAM_DICTIONARIES_PATH,
      defaultValue = "/usr/local/share/link-grammar/en",
      description = "Path to the directory with the dictionary files.")
  private String dictionariesPath;
  /**
   * Number of seconds the parser may <em>approximately</em> spend trying to analyze a sentence
   * before the algorithm tries stops itself. After twice this time has passed, the AE kills the
   * parser if it still is working on the same sentence.
   * <p>
   * By default, this parameter is set at 15 seconds. This means, the hard cap is at 30 seconds
   * (twice times the timeout value), at which point the link-parser gets killed and the AE moves
   * on. If you expect very long, "tough" sentences, try settings this parameter higher (30-60
   * seconds).
   */
  public static final String PARAM_TIMEOUT_SECONDS = "TimeoutSeconds";
  @ConfigurationParameter(name = PARAM_TIMEOUT_SECONDS,
      defaultValue = "15",
      description = "One quarter of the total max. timeout value.")
  private int timeout;
  /** The logger for this Annotator. */
  Logger logger;
  /** The wrapper for the LinkGrammar parser runtime executable. */
  LinkParser parser;

  /**
   * Configure a LinkGrammarAnnotator description.
   * 
   * @param dictPath the path to the directory containing the corresponding LG dictionary
   * @param timeout soft and hard of the parser in seconds - the LG parser should stop after the
   *        given number of seconds (soft) and the parser process is killed after twice that time
   *        has passed (hard)
   * @return an AE description
   * @throws UIMAException
   * @throws IOException
   */
  @SuppressWarnings("serial")
  public static AnalysisEngineDescription configure(final File dictPath, final int timeout)
      throws UIMAException, IOException {
    return AnalysisEngineFactory.createPrimitiveDescription(LinkGrammarAnnotator.class,
        UIMAUtils.makeParameterArray(new HashMap<String, Object>() {
          {
            put(PARAM_DICTIONARIES_PATH, dictPath);
            put(PARAM_TIMEOUT_SECONDS, timeout);
          }
        }));
  }

  /**
   * Configure a LinkGrammarAnnotator description using the default timeout.
   * 
   * @param dictPath the path to the directory containing the corresponding LG dictionary
   * @return an AE description
   * @throws UIMAException
   * @throws IOException
   */
  public static AnalysisEngineDescription configure(File dictPath) throws UIMAException,
      IOException {
    return LinkGrammarAnnotator.configure(dictPath, 15);
  }

  /**
   * Configure a LinkGrammarAnnotator description using the default dictionary path.
   * 
   * @param timeout soft and hard of the parser in seconds - the LG parser should stop after the
   *        given number of seconds (soft) and the parser process is killed after twice that time
   *        has passed (hard)
   * @return an AE description
   * @throws UIMAException
   * @throws IOException
   */
  public static AnalysisEngineDescription configure(int timeout) throws UIMAException, IOException {
    return LinkGrammarAnnotator.configure(null, timeout);
  }

  /**
   * Configure a default LinkGrammarAnnotator description.
   * 
   * @return an AE description
   * @throws UIMAException
   * @throws IOException
   */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return LinkGrammarAnnotator.configure(null);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    try {
      parser = new LinkParser(dictionariesPath, timeout, logger);
    } catch (final IOException e) {
      logger.log(Level.SEVERE, "link-parser setup failed");
      throw new ResourceInitializationException(e);
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    JCas textCas;
    try {
      textCas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textCas);
    final List<SyntaxAnnotation> phrases = new LinkedList<SyntaxAnnotation>();
    while (sentenceIt.hasNext()) {
      final Annotation sentenceAnn = sentenceIt.next();
      phrases.addAll(parseSentence(sentenceAnn.getCoveredText(), sentenceAnn.getBegin(), textCas));
    }
    for (final SyntaxAnnotation ann : phrases) {
      textCas.addFsToIndexes(ann);
    }
  }

  /**
   * Parse a sentence with the LinkGrammar parser.
   * 
   * @param sentence to parse
   * @param offset of the sentence (begin) in the text
   * @param jcas containing the sentence
   * @return the found constituent span annotations (might be empty)
   * @throws AnalysisEngineProcessException
   */
  List<SyntaxAnnotation> parseSentence(String sentence, int offset, JCas jcas)
      throws AnalysisEngineProcessException {
    final List<SyntaxAnnotation> phrases = new LinkedList<SyntaxAnnotation>();
    final boolean doubled = false; // if retried with double+1 timeout seconds
    // NB: any "normalizations" must be 1:1, otherwise the offsets will
    // be wrong!
    // LinkGrammar has issues when parsing curly and/or square braces;
    // normalize them to parenthesis, circumventing these issues:
    sentence = sentence.replace('{', '(').replace('}', ')');
    sentence = sentence.replace('[', '(').replace(']', ')');
    // There should be no newline characters in the sentence:
    sentence = sentence.replace('\n', ' ');
    String constituentExpression = null;
    try {
      logger.log(Level.FINE, "sentence: ''{0}''", sentence);
      /* ================= PARSE ================= */
      constituentExpression = parser.process(sentence);
      /* ================= PARSE ================= */
      if (constituentExpression == null) throw new IOException(
          "LinkParser returned NULL (likely cause: killed)");
      else if (constituentExpression.length() == 0 && sentence.trim().length() > 0) {
        constituentExpression = null;
        logger.log(Level.WARNING, "link-parser failed on ''{0}''", sentence);
      }
    } catch (final IOException e) {
      logger.log(Level.WARNING, "link-parser failed on ''{0}'': {1}",
          new String[] { sentence, e.getMessage() });
      try {
        parser.stop();
        parser = new LinkParser(dictionariesPath, timeout, logger);
      } catch (final IOException e2) {
        logger.log(Level.SEVERE, "link-parser setup failed: {0}", e2.getMessage());
        throw new AnalysisEngineProcessException(e2);
      }
    }
    if (constituentExpression != null) {
      logger.log(Level.FINE, "constituents: {0}", constituentExpression);
      // de-normalize parenthesis to curly brackets, just as LGP
      sentence = sentence.replace('(', '{').replace(')', '}');
      final ConstituentNode root = ConstituentNode.parse(constituentExpression);
      final Iterator<ConstituentNode> walker = ConstituentNode.walk(root);
      int position = 0;
      int len, pNext;
      while (walker.hasNext()) {
        final ConstituentNode node = walker.next();
        pNext = sentence.indexOf(node.data, position);
        // undo "normalizations"
        if (position == 0 && Character.isLowerCase(node.data.charAt(0))) {
          // link-parser lower-cases the first word
          pNext = sentence.toLowerCase().indexOf(node.data.toLowerCase(), position);
        }
        if (pNext == -1)
          throw new AnalysisEngineProcessException(new AssertionError("'" + node.data +
              "' not found in '" + sentence + "' after pos=" + position));
        position = pNext;
        len = node.data.length();
        node.setOffset(new Offset(offset + position, offset + position + len));
        position += len;
      }
      annotate(root, jcas, phrases, offset, offset + sentence.length());
    } else if (!doubled && sentence.length() > 200) {
      for (final char chop : new char[] { ';', ',' }) {
        int idx = -1;
        int last = 0;
        while ((idx = sentence.indexOf(chop, idx + 1)) != -1) {
          phrases.addAll(parseSentence(sentence.substring(last, idx), offset + last, jcas));
          last = idx + 1;
        }
        if (last != 0) {
          phrases.addAll(parseSentence(sentence.substring(last), offset + last, jcas));
          break;
        }
      }
    }
    return phrases;
  }

  @Override
  public void destroy() {
    super.destroy();
    try {
      parser.stop();
    } catch (final IOException e) {
      logger.log(Level.INFO, "IOException while halting parser: {0}", e.getMessage());
    }
  }

  /**
   * Create the constituent span syntax annotations from the parsed syntax tree on the given CAS
   * within the specified offset range.
   * <p>
   * Note that this method does not add the annotations to the CAS indices. Instead, it adds the
   * syntax annotations to the given list of phrases and the caller is responsible for adding the
   * annotations to the CAS indices.
   * <p>
   * The begin and end offsets usually would be the positions of the underlying sentence
   * annotation.
   * 
   * @param syntaxTreeRoot of the constituents
   * @param jcas to add the annotations too
   * @param phrases to collect the constituent spans
   * @param begin of the possible annotation span
   * @param end of the possible annotation span
   */
  private void annotate(ConstituentNode syntaxTreeRoot, JCas jcas, List<SyntaxAnnotation> phrases,
      int begin, int end) {
    Iterator<ConstituentNode> it = syntaxTreeRoot.iterator();
    final Stack<Iterator<ConstituentNode>> stack = new Stack<Iterator<ConstituentNode>>();
    Offset lastOffset = null;
    SyntaxAnnotation ann;
    while (it.hasNext()) {
      final ConstituentNode n = it.next();
      if (!n.isLeaf()) {
        if (!CONSTITUENT_TAGS.containsKey(n.data)) {
          logger.log(Level.WARNING, "unknown tag {0}", n.data);
        }
        stack.add(it);
        it = n.iterator();
        // here's why a tree-like annotation type would be useful...
        // do not annotate the same span twice - instead, choose
        // the innermost ("most decisive") annotation
        if (lastOffset != null && n.getOffset().equals(lastOffset)) {
          ann = phrases.get(phrases.size() - 1);
          logger.log(Level.FINE, "dropping outer constituent {0} of {1} on span ''{2}''",
              new Object[] { ann.getIdentifier(), n.data, ann.getCoveredText() });
          ann.setIdentifier(n.data);
          continue;
        } else if (n.getOffset().start() == begin && n.getOffset().end() == end) {
          logger.log(Level.FINER, "ignoring full-sentence length constituent {0}", n.data);
          continue;
        }
        // XXX: TreeAnnotation type? (w/ pointer to parent annotation)
        // (see comments above, too)
        ann = new SyntaxAnnotation(jcas, n.getOffset());
        ann.setAnnotator(URI);
        ann.setConfidence(1.0); // XXX: confidence score?
        ann.setNamespace(NAMESPACE);
        ann.setIdentifier(n.data);
        phrases.add(ann);
        lastOffset = n.getOffset();
      }
      while (!it.hasNext() && !stack.isEmpty()) {
        it = stack.pop();
      }
    }
  }
}
