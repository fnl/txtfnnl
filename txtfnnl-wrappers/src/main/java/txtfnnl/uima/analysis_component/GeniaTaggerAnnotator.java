/**
 * 
 */
package txtfnnl.uima.analysis_component;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.ChunkAnnotator;
import txtfnnl.uima.analysis_component.opennlp.PartOfSpeechAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.tcas.SyntaxAnnotation;
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
 * An annotator that tokenizes and tags the tokens of all sentences.
 * 
 * For this annotator to work, the <code>geniatagger</code> executable has to
 * be on the global <code>PATH</code>.
 * 
 * @author Florian Leitner
 */
public class GeniaTaggerAnnotator extends JCasAnnotator_ImplBase {

	/** The public URI of this annotator. */
	public static final String URI = "http://www.nactem.ac.uk/tsujii/GENIA/tagger/";

	/**
	 * The path to the dictionaries used by the LinkGrammar parser.
	 * 
	 * If in the default location (<code>/usr/local/share/geniatagger/</code>
	 * ), it is not necessary to set this parameter.
	 */
	public static final String PARAM_DICTIONARIES_PATH = "DictionariesPath";

	/** The logger for this Annotator. */
	Logger logger;

	/** The wrapper for the LinkGrammar parser runtime executable. */
	GeniaTagger tagger;

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

	/** The path the dictionaries and models to use. */
	private String dictionariesPath;

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		logger = ctx.getLogger();
		dictionariesPath = (String) ctx
		    .getConfigParameterValue(PARAM_DICTIONARIES_PATH);
		sentenceTypeName = (String) ctx
		    .getConfigParameterValue(UimaUtil.SENTENCE_TYPE_PARAMETER);

		if (dictionariesPath == null)
			dictionariesPath = "/usr/local/share/geniatagger/";

		if (sentenceTypeName == null)
			sentenceTypeName = SentenceAnnotator.SENTENCE_TYPE_NAME;

		try {
			tagger = new GeniaTagger(dictionariesPath, logger);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "geniatagger setup failed");
			throw new ResourceInitializationException(e);
		}
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
		List<SyntaxAnnotation> buffer = new LinkedList<SyntaxAnnotation>();
		List<Token> tokens;

		while (sentenceIt.hasNext()) {
			SyntaxAnnotation sentenceAnn = (SyntaxAnnotation) sentenceIt
			    .next();
			String sentence = sentenceAnn.getCoveredText().replace('\n', ' ');
			SyntaxAnnotation chunkAnn = null;
			int sentenceOffset = sentenceAnn.getBegin();
			int wordOffset = 0;
			int wordLength = 0;
			int chunkEnd = -1;

			try {
				tokens = tagger.process(sentence);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "geniatagger failed on: '{0}'",
				    sentence);
				throw new AnalysisEngineProcessException(e);
			}

			for (Token t : tokens) {
				char c = t.chunk().charAt(0);
				wordLength = t.word().length();
				wordOffset = sentence.indexOf(t.word(), wordOffset);

				if (wordOffset == -1) {
					logger.log(Level.SEVERE, "unmatched word '{0}' in '{1}'",
					    new Object[] { t.word(), sentence });
					throw new AnalysisEngineProcessException();
				}

				/* annotate the token */
				SyntaxAnnotation tokenAnn = new SyntaxAnnotation(textCas,
				    sentenceOffset + wordOffset, sentenceOffset + wordOffset +
				                                 wordLength);
				tokenAnn.setAnnotator(GeniaTaggerAnnotator.URI);
				tokenAnn.setNamespace(TokenAnnotator.NAMESPACE);
				tokenAnn.setIdentifier(TokenAnnotator.IDENTIFIER);
				tokenAnn.setConfidence(1.0);
				Property tag = new Property(textCas);
				Property prob = new Property(textCas);
				Property lemma = new Property(jcas);
				tag.setName(PartOfSpeechAnnotator.POS_TAG_VALUE_PROPERTY_NAME);
				prob.setName(PartOfSpeechAnnotator.POS_TAG_CONFIDENCE_PROPERTY_NAME);
				lemma.setName(BioLemmatizerAnnotator.LEMMA_PROPERTY_NAME);
				tag.setValue(t.partOfSpeech());
				prob.setValue("1.0");
				lemma.setValue(t.stem());
				FSArray properties = new FSArray(jcas, 3);
				properties.set(0, tag);
				properties.set(1, prob);
				properties.set(2, lemma);
				tokenAnn.setProperties(properties);
				buffer.add(tokenAnn);

				/* annotate the chunk */

				switch (c) {
				case 'B':
					if (chunkAnn != null) {
						chunkAnn.setEnd(chunkEnd);
						buffer.add(chunkAnn);
					}
					chunkAnn = new SyntaxAnnotation(textCas);
					chunkAnn.setBegin(sentenceOffset + wordOffset);
					chunkAnn.setAnnotator(GeniaTaggerAnnotator.URI);
					chunkAnn.setNamespace(ChunkAnnotator.NAMESPACE);
					chunkAnn.setIdentifier(t.chunk().substring(2));
					chunkAnn.setConfidence(1.0);
					chunkEnd = sentenceOffset + wordOffset + wordLength;
				break;
				case 'I':
					chunkEnd = sentenceOffset + wordOffset + wordLength;
				break;
				case 'O':
					if (chunkAnn != null) {
						chunkAnn.setEnd(chunkEnd);
						buffer.add(chunkAnn);
						chunkAnn = null;
					}
				break;
				default:
					logger.log(Level.SEVERE, "unknown chunk tag '" + t.chunk() + "'");
					throw new AnalysisEngineProcessException();
				}

				wordOffset += wordLength;
			}

			if (chunkAnn != null) {
				chunkAnn.setEnd(chunkEnd);
				buffer.add(chunkAnn);
			}
		}

		for (SyntaxAnnotation ann : buffer) {
			textCas.addFsToIndexes(ann);
		}
	}

	/* (non-Javadoc)
	 * 
	 * @see
	 * org.apache.uima.analysis_component.AnalysisComponent_ImplBase#destroy() */
	public void destroy() {
		super.destroy();

		try {
			tagger.stop();
		} catch (IOException e) {
			logger.log(Level.INFO, "IOException while stopping the logger: " +
			                       e.getMessage());
		}
	}
}
