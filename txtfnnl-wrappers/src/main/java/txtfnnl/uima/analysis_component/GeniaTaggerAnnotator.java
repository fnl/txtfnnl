/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;
import txtfnnl.uima.wrappers.ReadlineRuntime;

/** A simple data type for the tokens (lines) produced by the GENIA tagger. */
class Token {

	private final String[] content;

	private static final int WORD = 0;
	private static final int STEM = 1;
	private static final int POS = 2;
	private static final int CHUNK = 3;
	private static final int NER = 4;

	Token(String raw) {
		content = raw.split("\\t");
		assert content.length == 5;
	}

	String word() {
		return content[WORD];
	}

	String stem() {
		return content[STEM];
	}

	String partOfSpeech() {
		return content[POS];
	}

	String chunk() {
		return content[CHUNK];
	}

	String entityTag() {
		return content[NER];
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		for (String s : content) {
			sb.append(s);
			sb.append('\t');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
}

/**
 * A wrapper for the <code>geniatagger</code> executable.
 * 
 * For this wrapper to work, the <code>geniatagger</code> executable must have
 * the following properties:
 * <ol>
 * <li>The directory with the the models and dictionaries must be present and
 * their content readable.</li>
 * </ol>
 * 
 * @author Florian Leitner
 */
class GeniaTagger extends ReadlineRuntime<List<Token>> {

	/**
	 * Create a new link-parser process fork.
	 * 
	 * @param dictPath the path to the parser dictionaries and models
	 * @param logger to handle all error messages
	 * @throws IOException on failure
	 */
	public GeniaTagger(String dictPath, Logger logger) throws IOException {
		super("geniatagger", new File(dictPath), logger);
	}

	/**
	 * Tag a single sentence.
	 * 
	 * @return the tagged tokens
	 * @throws IOException on IO failures
	 */
	@Override
	protected List<Token> parseResponse() throws IOException {
		List<Token> tokens = new LinkedList<Token>();
		String line = readLine();

		while (line.length() > 0) {
			tokens.add(new Token(line));
			line = readLine();
		}

		return tokens;
	}
}

/**
 * An Genia Tagger Token AE variant for the txtfnnl pipeline.
 * 
 * For this annotator to work, the <code>geniatagger</code> executable has to
 * be on the global <code>PATH</code>.
 * 
 * This AE tokenizes a {@link SentenceAnnotation}, and sets the PoS tag and
 * chunk tag feature for each detected {@link TokenAnnotation}.
 * 
 * @author Florian Leitner
 */
public class GeniaTaggerAnnotator extends JCasAnnotator_ImplBase {

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://www.nactem.ac.uk/tsujii/GENIA/tagger/";

	/** The namespace used for all annotations. */
	public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";

	/** The identifier used for all annotations. */
	public static final String IDENTIFIER = "Word";

	/**
	 * The path to the dictionaries used by the GENIA Tagger.
	 * 
	 * If in the default location (<code>/usr/local/share/geniatagger/</code>
	 * ), it is not necessary to set this parameter.
	 */
	public static final String PARAM_DICTIONARIES_PATH = "DictionariesPath";
	@ConfigurationParameter(name = PARAM_DICTIONARIES_PATH,
	                        defaultValue = "/usr/local/share/geniatagger/",
	                        description = "Path to the directory with the model files.")
	private String dictionariesPath;

	protected Logger logger;

	private GeniaTagger tagger;

	/**
	 * Configure a Genia Tagger AE for a pipeline using a specific directory
	 * where the GENIA dictionaries are located.
	 */
	public static AnalysisEngineDescription configure(String dictPath) throws UIMAException,
	        IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(GeniaTaggerAnnotator.class,
		    PARAM_DICTIONARIES_PATH, dictPath);
	}

	/**
	 * Configure a Genia Tagger AE for a pipeline using the default location
	 * of the GENIA dictionaries.
	 */
	public static AnalysisEngineDescription configure() throws UIMAException, IOException {
		return AnalysisEngineFactory.createPrimitiveDescription(GeniaTaggerAnnotator.class);
	}

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();

		try {
			tagger = new GeniaTagger(dictionariesPath, logger);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "geniatagger setup failed (path=" + dictionariesPath + ")");
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas textCas;

		try {
			textCas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(textCas);
		List<TokenAnnotation> buffer = new LinkedList<TokenAnnotation>();
		List<Token> tokens;

		while (sentenceIt.hasNext()) {
			Annotation sentenceAnn = sentenceIt.next();
			String sentence = sentenceAnn.getCoveredText().replace('\n', ' ');
			int sentenceOffset = sentenceAnn.getBegin();
			int wordOffset = 0;
			int wordLength = 0;

			try {
				tokens = tagger.process(sentence);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "geniatagger failed on: '{0}'", sentence);
				throw new AnalysisEngineProcessException(e);
			}

			for (Token t : tokens) {
				wordLength = t.word().length();
				wordOffset = sentence.indexOf(t.word(), wordOffset);

				if (wordOffset == -1) {
					logger.log(Level.SEVERE, "unmatched word '{0}' in '{1}'",
					    new Object[] { t.word(), sentence });
					throw new AnalysisEngineProcessException(new RuntimeException(
					    "unmatched word '" + t.word() + "' in '" + sentence + "'"));
				}

				/* annotate the token */
				TokenAnnotation tokenAnn = new TokenAnnotation(textCas, sentenceOffset +
				                                                        wordOffset,
				    sentenceOffset + wordOffset + wordLength);
				tokenAnn.setAnnotator(URI);
				tokenAnn.setNamespace(NAMESPACE);
				tokenAnn.setIdentifier(IDENTIFIER);
				tokenAnn.setConfidence(1.0);
				tokenAnn.setPos(t.partOfSpeech());
				tokenAnn.setStem(t.stem());

				switch (t.chunk().charAt(0)) {
				case 'B':
					tokenAnn.setChunk(t.chunk().substring(2));
				break;
				case 'I':
					tokenAnn.setChunk(t.chunk().substring(2));
					tokenAnn.setInChunk(true);
				break;
				case 'O':
				break;
				default:
					logger.log(Level.SEVERE, "unexpected chunk tag '" + t.chunk() + "'");
					throw new AnalysisEngineProcessException(new RuntimeException(
					    "unexpected chunk tag '" + t.chunk() + "'"));
				}

				buffer.add(tokenAnn);
				wordOffset += wordLength;
			}
		}

		for (TokenAnnotation ann : buffer) {
			textCas.addFsToIndexes(ann);
		}
	}

	public void destroy() {
		super.destroy();

		try {
			tagger.stop();
		} catch (IOException e) {
			logger.log(Level.INFO, "IOException while stopping the logger: " + e.getMessage());
		}
	}
}
