/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.linkgrammar.LinkGrammar;

import txtfnnl.uima.Offset;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.tcas.SyntaxAnnotation;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class LinkGrammarAnnotator extends JCasAnnotator_ImplBase {

	static class ConstituentNode implements Iterable<ConstituentNode> {

		public final String data;
		private ConstituentNode parent;
		private LinkedList<ConstituentNode> children;
		private Offset span;
		private static final LinkedList<ConstituentNode> EMPTY_LIST = new LinkedList<ConstituentNode>();

		public static ConstituentNode parse(String cExpression) {
			ConstituentNode root = new ConstituentNode("ROOT");
			StringBuilder sb = new StringBuilder();

			for (int idx = 0; idx < cExpression.length(); ++idx) {
				char c = cExpression.charAt(idx);

				if (c == '(') {
					idx += parse(cExpression.substring(idx + 1), root);
				} else if (c == ')') {
					throw new RuntimeException("parse error at '" +
					                           cExpression.substring(0, idx) +
					                           ">>>)<<<" +
					                           cExpression.substring(idx + 1) +
					                           "'");
				} else if (c == ' ') {
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

		static int parse(String cExpression, ConstituentNode parent) {
			ConstituentNode node = null;
			StringBuilder sb = new StringBuilder();

			for (int idx = 0; idx < cExpression.length(); ++idx) {
				char c = cExpression.charAt(idx);

				if (c == '(') {
					idx += parse(cExpression.substring(idx + 1), node);
				} else if (c == ')') {
					if (sb.length() > 0) {
						node = buildCNode(node, sb, parent);
					}
					return idx + 1;
				} else if (c == ' ') {
					if (sb.length() > 0) {
						node = buildCNode(node, sb, parent);
						sb = new StringBuilder();
					}
				} else {
					sb.append(c);
				}
			}

			throw new RuntimeException("unclosed constituent '" + cExpression +
			                           "'");
		}

		private static ConstituentNode buildCNode(ConstituentNode node,
		                                          StringBuilder sb,
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

		public ConstituentNode(String data) {
			this.data = data;
			this.children = null;
			this.parent = null;
		}

		public boolean isLeaf() {
			return children == null || children.size() == 0;
		}

		public boolean isRoot() {
			return parent == null;
		}

		/* public boolean isFirstChild() { return !isRoot() &&
		 * parent.children.getFirst() == this; }
		 * 
		 * public boolean isLastChild() { return !isRoot() &&
		 * parent.children.getLast() == this; } */

		public ConstituentNode getParent() {
			return parent;
		}

		public ConstituentNode getChild(int idx) {
			if (!isLeaf())
				return children.get(idx);
			else
				throw new UnsupportedOperationException("leaf node");
		}

		public ConstituentNode getFirst() {
			return children.getFirst();
		}

		public ConstituentNode getLast() {
			return children.getLast();
		}

		public void setOffset(Offset span) {
			if (!isLeaf())
				throw new UnsupportedOperationException("not a leaf");

			this.span = span;
		}

		public Offset getOffset() {
			if (!isLeaf() && span == null) {
				ConstituentNode start = getFirst();
				ConstituentNode end = getLast();

				while (!start.isLeaf())
					start = start.getFirst();

				while (!end.isLeaf())
					end = end.getLast();

				int s = start.getOffset().start();
				int e = end.getOffset().end();

				if (s != e)
					span = new Offset(s, e);
				else
					span = new Offset(s);
			}
			return span;
		}

		public void addChild(ConstituentNode child) {
			if (child.parent == null) {
				if (children == null)
					children = new LinkedList<ConstituentNode>();

				child.parent = this;
				children.add(child);
			} else if (child.parent == this) {
				// already added
			} else {
				throw new IllegalArgumentException(
				    "child already has parent: " + child.parent);
			}
		}

		/* public boolean removeChild(ConstituentNode child) { if (!isLeaf())
		 * { int idx = children.indexOf(child);
		 * 
		 * if (idx != -1) return remove(child, idx) == child; } return false;
		 * }
		 * 
		 * public ConstituentNode removeChild(int idx) { if (!isLeaf() && idx
		 * > -1) return remove(children.get(idx), idx); else throw new
		 * IndexOutOfBoundsException("illegal index=" + idx); }
		 * 
		 * private ConstituentNode remove(ConstituentNode child, int idx) { if
		 * (child.parent == this) { child.parent = null; return
		 * children.remove(idx); } else { return null; } } */

		public int numChildren() {
			if (!isLeaf())
				return children.size();
			else
				return 0;
		}

		public static Iterator<ConstituentNode>
		        walk(final ConstituentNode node) {
			return new Iterator<ConstituentNode>() {

				private Stack<Iterator<ConstituentNode>> stack = new Stack<Iterator<ConstituentNode>>();
				private Iterator<ConstituentNode> iter = node.iterator();

				public boolean hasNext() {
					if (iter.hasNext()) {
						return true;
					} else if (!stack.isEmpty()) {
						while (!stack.isEmpty()) {
							iter = stack.pop();

							if (iter.hasNext())
								return true;
						}
					}
					return false;
				}

				public ConstituentNode next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					} else {
						ConstituentNode elm = iter.next();

						if (elm.isLeaf()) {
							return elm;
						} else {
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

		public Iterator<ConstituentNode> iterator() {
			if (!isLeaf())
				return children.iterator();
			else
				return EMPTY_LIST.iterator();
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			boolean inside = (!isLeaf() && !isRoot());

			if (inside)
				sb = new StringBuilder("(");

			sb.append(data);

			for (ConstituentNode child : this) {
				sb.append(' ');
				sb.append(child.toString());
			}

			if (inside)
				sb.append(')');

			return sb.toString();
		}
	}

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

	public static final String URI = LinkGrammarAnnotator.class.getName();

	/**
	 * The path to the dictionaries used by the LinkGrammar parser.
	 * 
	 * If in the default location (<code>/usr/local/share/link-grammar/</code>
	 * ), it is not necessary to set this parameter.
	 */
	public static final String PARAM_DICTIONARIES_PATH = "DictionariesPath";

	/**
	 * Maximum number of seconds the parser may <em>approximately</em> spend
	 * trying to analyze a sentence.
	 */
	public static final String PARAM_PARSE_SECONDS = "ParseSeconds";

	/** The logger for this Annotator. */
	Logger logger;

	/**
	 * The name of the sentence annotation type.
	 * 
	 * Annotated sentences will be parsed by the LinkGrammar parser.
	 * 
	 * Defaults to {@link SentenceAnnotator#SENTENCE_TYPE_NAME}. Can be set as
	 * an AE descriptor parameter with the name
	 * {@link UimaUtil#SENTENCE_TYPE_PARAMETER}.
	 */
	private String sentenceTypeName;

	private Lock processLock;

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();
		String dictionariesPath = (String) ctx
		    .getConfigParameterValue(PARAM_DICTIONARIES_PATH);
		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(UimaUtil.SENTENCE_TYPE_PARAMETER);
		Integer parseSeconds = (Integer) ctx
		    .getConfigParameterValue(PARAM_PARSE_SECONDS);
		processLock = new ReentrantLock();

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		if (dictionariesPath != null)
			LinkGrammar.setDictionariesPath(dictionariesPath);

		if (parseSeconds != null)
			LinkGrammar.setMaxParseSeconds(parseSeconds.intValue());
	}

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(
	 * org.apache.uima.jcas.JCas) */
	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas textCas;

		try {
			textCas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<Annotation> sentenceIt = SentenceAnnotator
		    .getSentenceIterator(textCas, sentenceTypeName);
		List<SyntaxAnnotation> phrases = new LinkedList<SyntaxAnnotation>();

		while (sentenceIt.hasNext()) {
			SyntaxAnnotation sentenceAnn = (SyntaxAnnotation) sentenceIt
			    .next();

			parseSentence(sentenceAnn.getCoveredText(),
			    sentenceAnn.getBegin(), textCas, phrases);
		}

		for (SyntaxAnnotation ann : phrases) {
			textCas.addFsToIndexes(ann);
		}
	}

	void parseSentence(String sentence, int offset, JCas jcas,
	                   List<SyntaxAnnotation> phrases) {
		processLock.lock();

		try {
			logger.log(Level.FINE, "parsing ''{0}''", sentence);
			// LinkGrammar has problems when parsing curly braces;
			// replace them with parenthesis to circumvent this issue
			sentence = sentence.replace('{', '(').replace('}', ')');
			LinkGrammar.parse(sentence);
			LinkGrammar.makeLinkage(0);
			int numWords = LinkGrammar.getNumWords() - 2;

			if (LinkGrammar.getNumWords() < 1) {
				logger.log(Level.WARNING, "unable to tokenize ''{0}''",
				    sentence);
			} else if (LinkGrammar.getNumLinkages() > 0) {
				String constituentExpression = LinkGrammar
				    .getConstituentString();
				logger.log(Level.FINE, constituentExpression);
				ConstituentNode root = ConstituentNode
				    .parse(constituentExpression);
				Iterator<ConstituentNode> walker = ConstituentNode.walk(root);
				int position = 0;
				int i = 0;
				int len, pNext;

				try {
					for (i = 0; i < numWords; ++i) {
						String word = LinkGrammar.getWord(i + 1);
						pNext = sentence.indexOf(word, position);

						if (i == 0 && Character.isLowerCase(word.charAt(0)))
							pNext = sentence.toLowerCase().indexOf(
							    word.toLowerCase(), position);

						if (pNext == -1)
							throw new AssertionError("'" + word +
							                         "' not found in '" +
							                         sentence +
							                         "' after pos=" + position);

						position = pNext;
						len = word.length();
						walker.next().setOffset(
						    new Offset(offset + position, offset + position +
						                                  len));
						position += len;
					}

					annotate(root, jcas, phrases, offset,
					    offset + sentence.length());
				} catch (NoSuchElementException e) {
					logger
					    .log(
					        Level.WARNING,
					        "parsing failed at word {0}/{1}:"
					                + "\nSentence=''{2}''\nTree=''{3}''",
					        new Object[] {
					            i,
					            numWords,
					            sentence,
					            root.toString() });
				}
			} else {
				logger.log(Level.WARNING, "no linkages found in ''{0}''",
				    sentence);
			}
		} finally {
			processLock.unlock();
		}
	}

	private void annotate(ConstituentNode syntaxTreeRoot, JCas jcas,
	                      List<SyntaxAnnotation> phrases, int begin, int end) {
		Iterator<ConstituentNode> it = syntaxTreeRoot.iterator();
		Stack<Iterator<ConstituentNode>> stack = new Stack<Iterator<ConstituentNode>>();
		Offset lastOffset = null;
		SyntaxAnnotation ann;

		while (it.hasNext()) {
			ConstituentNode n = it.next();

			if (!n.isLeaf()) {
				if (!CONSTITUENT_TAGS.containsKey(n.data))
					logger.log(Level.WARNING, "tag " + n.data + " unknown");

				stack.add(it);
				it = n.iterator();

				// do not annotate the same span twice - instead, choose
				// the innermost ("most decisive") annotation
				if (lastOffset != null && n.getOffset().equals(lastOffset)) {
					ann = phrases.get(phrases.size() - 1);
					logger
					    .log(
					        Level.FINE,
					        "dropping outer constituent {0} of {1} on span ''{2}''",
					        new Object[] {
					            ann.getIdentifier(),
					            n.data,
					            ann.getCoveredText() });
					ann.setIdentifier(n.data);
					continue;
				} else if (n.getOffset().start() == begin &&
				           n.getOffset().end() == end) {
					logger.log(Level.FINER,
					    "ignoring full-sentence length constituent {0}",
					    n.data);
					continue;
				}

				ann = new SyntaxAnnotation(jcas, n.getOffset());
				ann.setAnnotator(URI);
				ann.setConfidence(1.0); // TODO: any confidence scores from
				                        // LG?
				ann.setIdentifier(n.data);
				ann.setNamespace("http://bulba.sdsu.edu/jeanette/thesis/PennTags.html#");
				phrases.add(ann);
				lastOffset = n.getOffset();
			}

			while (!it.hasNext() && !stack.isEmpty())
				it = stack.pop();
		}

	}
}
