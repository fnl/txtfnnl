package txtfnnl.uima.analysis_component.opennlp;

import java.io.IOException;
import java.util.regex.Pattern;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import opennlp.uima.sentdetect.SentenceModelResource;
import opennlp.uima.sentdetect.SentenceModelResourceImpl;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;

/**
 * An OpenNLP-based sentence detector for the txtfnnl pipeline.
 * 
 * This AE segments input text into {@link SentenceAnnotation}s, setting each
 * sentences probability on the confidence feature. The detector requires a
 * {@link #RESOURCE_SENTENCE_MODEL}, which can be the default model found in
 * the jar.
 * 
 * @author Florian Leitner
 */
public final class SentenceAnnotator extends JCasAnnotator_ImplBase {

	/** The annotator's URI (for the annotations) set by this AE. */
	public static final String URI = "http://opennlp.apache.org";

	/** The namespace used for all annotations. */
	public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";

	/** The identifier used for all annotations. */
	public static final String IDENTIFIER = "Sentence";

	/**
	 * Optional parameter to split on newlines; should be either "", "single"
	 * or "multi".
	 * 
	 * The parameter indicates if sentences should be split on newlines never
	 * (null, empty), always (single), or only after consecutive newlines
	 * (multi). Note that consecutive newlines may contain white-spaces in
	 * between line-breaks.
	 */
	public static final String PARAM_SPLIT_ON_NEWLINE = "SplitOnNewline";

	/** The name of the (required) sentence model resource. */
	public static final String RESOURCE_SENTENCE_MODEL = "SentenceModelResource";

	/** Regular expression to detect multiple/consecutive line-breaks. */
	static final Pattern REGEX_MULTI_LINEBREAK = Pattern
	    .compile("(?:\\r?\\n\\s*){2,}");

	/** The default sentence model file in the jar. */
	static final String DEFAULT_SENTENCE_MODEL_FILE = "file:txtfnnl/opennlp/en_sent.bin";

	protected Logger logger;

	private SentenceDetectorME sentenceDetector;

	/**
	 * Set to <code>true</code> via {@link #PARAM_SPLIT_ON_NEWLINE} if the
	 * parameter value is "single".
	 */
	private boolean splitOnSingleNewline = false;

	/**
	 * Set to <code>true</code> via {@link #PARAM_SPLIT_ON_NEWLINE} if the
	 * parameter value is "multi".
	 */
	private boolean splitOnMultiNewline = false;

	/**
	 * Create this AE's descriptor for a pipeline.
	 * 
	 * @param splitSentences indicates whether sentences should not be split
	 *        on newlines (default: the empty string), on single lines using
	 *        value "single", or on double newlines, using "double"
	 * @param modelFilePath should indicate the sentence segmentation model
	 *        file to use, e.g., "~/opennlp/models/en_sent.bin"; should never
	 *        be <code>null</code> or the empty string
	 * @throws IOException
	 * @throws UIMAException
	 */
	public static AnalysisEngineDescription configure(String splitSentences,
	                                                  String modelFilePath)
	        throws UIMAException, IOException {
		AnalysisEngineDescription aed;

		if (splitSentences == null || "".equals(splitSentences))
			aed = AnalysisEngineFactory
			    .createPrimitiveDescription(SentenceAnnotator.class);
		else
			aed = AnalysisEngineFactory.createPrimitiveDescription(
			    SentenceAnnotator.class,
			    SentenceAnnotator.PARAM_SPLIT_ON_NEWLINE, splitSentences);

		ExternalResourceFactory.createDependencyAndBind(aed,
		    RESOURCE_SENTENCE_MODEL, SentenceModelResourceImpl.class,
		    modelFilePath);
		return aed;
	}

	/**
	 * Create this AE's descriptor for a pipeline using the default sentence
	 * model.
	 * 
	 * @see SentenceAnnotator#configure(String, String)
	 */
	public static AnalysisEngineDescription configure(String splitSentences)
	        throws UIMAException, IOException {
		return configure(splitSentences, DEFAULT_SENTENCE_MODEL_FILE);
	}

	/**
	 * Create this AE's descriptor for a pipeline using the default sentence
	 * model while annotating sentences across "single" newlines (only).
	 * 
	 * @see SentenceAnnotator#configure(String, String)
	 */
	public static AnalysisEngineDescription configure() throws UIMAException,
	        IOException {
		return configure("single");
	}

	/**
	 * Load the sentence detector model resource and initialize the model
	 * evaluator.
	 */
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);
		SentenceModel model;
		logger = ctx.getLogger();

		try {
			SentenceModelResource modelResource = (SentenceModelResource) ctx
			    .getResourceObject(RESOURCE_SENTENCE_MODEL);
			model = modelResource.getModel();
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (NullPointerException e) {
			throw new ResourceInitializationException(new AssertionError(
			    "sentence model resource not found"));
		}

		sentenceDetector = new SentenceDetectorME(model);

		String splitMode = (String) ctx
		    .getConfigParameterValue(PARAM_SPLIT_ON_NEWLINE);

		if (splitMode == null || splitMode.equals("")) {
			logger.log(Level.INFO, "no newline-based splitting");
		} else if (splitMode.equals("single")) {
			splitOnSingleNewline = true;
			logger.log(Level.INFO, "splitting on single newlines");
		} else if (splitMode.equals("multi")) {
			splitOnMultiNewline = true;
			logger.log(Level.INFO, "splitting on consecutive newlines");
		} else {
			throw new ResourceInitializationException(new AssertionError(
			    "parameter '" + PARAM_SPLIT_ON_NEWLINE +
			            "' value must be 'single' or 'multi' (was '" +
			            splitMode + "')"));
		}
	}

	/**
	 * Make {@link SentenceAnnotation}s in the {@link Views.CONTENT_TEXT} view
	 * of a CAS.
	 */
	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			jcas = jcas.getView(Views.CONTENT_TEXT.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}
		String[] chunks;
		String text = jcas.getDocumentText();

		if (this.splitOnMultiNewline) {
			chunks = REGEX_MULTI_LINEBREAK.split(text);
		} else if (this.splitOnSingleNewline) {
			chunks = text.split("\\s*\\r?\\n\\s*");
		} else {
			chunks = new String[] { text };
		}

		int offset = -1;

		for (String c : chunks) {
			offset = text.indexOf(c, offset + 1);
			assert offset != -1;
			Span[] spans = sentenceDetector.sentPosDetect(c);
			double probs[] = sentenceDetector.getSentenceProbabilities();
			assert spans.length == probs.length;

			for (int i = 0; i < spans.length; i++) {
				SentenceAnnotation sentence = new SentenceAnnotation(jcas,
				    offset + spans[i].getStart(), offset + spans[i].getEnd());
				sentence.setConfidence(probs[i]);
				sentence.setAnnotator(URI);
				sentence.setIdentifier(IDENTIFIER);
				sentence.setNamespace(NAMESPACE);
				sentence.addToIndexes();
			}
		}
	}

	@Override
	public void destroy() {
		sentenceDetector = null;
	}

}
