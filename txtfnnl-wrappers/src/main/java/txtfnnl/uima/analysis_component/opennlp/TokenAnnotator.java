package txtfnnl.uima.analysis_component.opennlp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import opennlp.uima.chunker.ChunkerModelResource;
import opennlp.uima.chunker.ChunkerModelResourceImpl;
import opennlp.uima.postag.POSModelResource;
import opennlp.uima.postag.POSModelResourceImpl;
import opennlp.uima.tokenize.TokenizerModelResource;
import opennlp.uima.tokenize.TokenizerModelResourceImpl;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ExternalResourceFactory;

import txtfnnl.uima.Views;
import txtfnnl.uima.tcas.SentenceAnnotation;
import txtfnnl.uima.tcas.TokenAnnotation;

/**
 * An OpenNLP-based tokenizer for the txtfnnl pipeline.
 * <p>
 * This AE tokenizes a {@link SentenceAnnotation sentence}, and sets the PoS and chunk tag features
 * for each detected {@link TokenAnnotation token}. Each token's probability is set in the
 * annotation's confidence feature. By default, the AE uses the English Maximum Entropy models of
 * OpenNLP, otherwise a {@link #RESOURCE_TOKEN_MODEL}, a {@link #RESOURCE_POS_MODEL}, and a
 * {@link #RESOURCE_CHUNK_MODEL} have to be provided.
 * 
 * @author Florian Leitner
 */
public class TokenAnnotator extends JCasAnnotator_ImplBase {
  /** The annotator's URI (for the annotations) set by this AE. */
  public static final String URI = "http://opennlp.apache.org";
  /** The namespace used for all annotations. */
  public static final String NAMESPACE = "http://nlp2rdf.lod2.eu/schema/doc/sso/";
  /** The identifier used for all annotations. */
  public static final String IDENTIFIER = "Word";
  /** The name of the (required) tokenizer model resource. */
  public static final String RESOURCE_TOKEN_MODEL = "TokenizerModelResource";
  @ExternalResource(key = RESOURCE_TOKEN_MODEL)
  private TokenizerModelResource tokenizerModel;
  /** The name of the (required) PoS tagger model resource. */
  public static final String RESOURCE_POS_MODEL = "PosTaggerModelResource";
  @ExternalResource(key = RESOURCE_POS_MODEL)
  private POSModelResource posModel;
  /** The name of the (required) chunker model resource. */
  public static final String RESOURCE_CHUNK_MODEL = "ChunkerModelResource";
  @ExternalResource(key = RESOURCE_CHUNK_MODEL)
  private ChunkerModelResource chunkerModel;
  /** The default tokenizer model file in the jar. */
  static final String DEFAULT_TOKEN_MODEL_FILE = "file:txtfnnl/opennlp/en_token.bin";
  /** The default PoS tagger model file in the jar. */
  static final String DEFAULT_POS_MODEL_FILE = "file:txtfnnl/opennlp/en_pos_maxent.bin";
  /** The default chunker model file in the jar. */
  static final String DEFAULT_CHUNK_MODEL_FILE = "file:txtfnnl/opennlp/en_chunker.bin";
  protected Logger logger;
  private TokenizerME tokenizer;
  private POSTaggerME posTagger;
  private ChunkerME chunker;

  /**
   * Configure a TokenAnnotator engine for a pipeline.
   * 
   * @param tokenModelFilePath should indicate the tokenization model file to use, e.g.,
   *        "~/opennlp/models/en-token.bin"; should never be <code>null</code> or the empty string
   * @param posModelFilePath should indicate the PoS tagger model file
   * @param chunkModelFilePath should indicate the chunker model file
   * @throws IOException
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure(String tokenModelFilePath,
      String posModelFilePath, String chunkModelFilePath) throws UIMAException, IOException {
    final ExternalResourceDescription tokenModel = ExternalResourceFactory
        .createExternalResourceDescription(TokenizerModelResourceImpl.class, tokenModelFilePath);
    final ExternalResourceDescription posModel = ExternalResourceFactory
        .createExternalResourceDescription(POSModelResourceImpl.class, posModelFilePath);
    final ExternalResourceDescription chunkModel = ExternalResourceFactory
        .createExternalResourceDescription(ChunkerModelResourceImpl.class, chunkModelFilePath);
    final AnalysisEngineDescription aed = AnalysisEngineFactory.createPrimitiveDescription(
        TokenAnnotator.class, RESOURCE_TOKEN_MODEL, tokenModel, RESOURCE_POS_MODEL, posModel,
        RESOURCE_CHUNK_MODEL, chunkModel);
    return aed;
  }

  /**
   * Configure the AE using the built-in (jar) model files.
   * 
   * @throws IOException
   * @throws UIMAException
   */
  public static AnalysisEngineDescription configure() throws UIMAException, IOException {
    return TokenAnnotator.configure(DEFAULT_TOKEN_MODEL_FILE, DEFAULT_POS_MODEL_FILE,
        DEFAULT_CHUNK_MODEL_FILE);
  }

  @Override
  public void initialize(UimaContext ctx) throws ResourceInitializationException {
    super.initialize(ctx);
    logger = ctx.getLogger();
    tokenizer = new TokenizerME(tokenizerModel.getModel());
    posTagger = new POSTaggerME(posModel.getModel(), POSTaggerME.DEFAULT_BEAM_SIZE, 0);
    chunker = new ChunkerME(chunkerModel.getModel(), ChunkerME.DEFAULT_BEAM_SIZE);
  }

  /**
   * Make {@link TokenAnnotation}s within {@link SentenceAnnotation}s found in the
   * {@link Views.CONTENT_TEXT} view of a CAS.
   */
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // TODO: use default view
    try {
      jcas = jcas.getView(Views.CONTENT_TEXT.toString());
    } catch (final CASException e) {
      throw new AnalysisEngineProcessException(e);
    }
    final FSIterator<Annotation> sentenceIt = SentenceAnnotation.getIterator(jcas);
    // buffer new tokens and only add them to the index after we have
    // iterated over all sentences - otherwise a concurrent modification
    // exception would be raised
    final List<TokenAnnotation> buffer = new LinkedList<TokenAnnotation>();
    while (sentenceIt.hasNext()) {
      final Annotation sentence = sentenceIt.next();
      final String text = sentence.getCoveredText();
      final Span tokenSpans[] = tokenizer.tokenizePos(text);
      final double tokenProbabilties[] = tokenizer.getTokenProbabilities();
      assert tokenSpans.length == tokenProbabilties.length;
      final String[] tokens = new String[tokenSpans.length];
      final TokenAnnotation[] anns = new TokenAnnotation[tokenSpans.length];
      final int sentenceOffset = sentence.getBegin();
      // annotate and retrieve the tokens
      for (int i = 0; i < tokenSpans.length; i++) {
        final TokenAnnotation token = new TokenAnnotation(jcas, sentenceOffset +
            tokenSpans[i].getStart(), sentenceOffset + tokenSpans[i].getEnd());
        tokens[i] = (String) tokenSpans[i].getCoveredText(text);
        token.setConfidence(tokenProbabilties[i]);
        token.setAnnotator(URI);
        token.setIdentifier(IDENTIFIER);
        token.setNamespace(NAMESPACE);
        anns[i] = token;
        buffer.add(token);
      }
      // annotate the PoS and chunk tags on each token
      final String[] tags = posTagger.tag(tokens);
      final String[] chunks = chunker.chunk(tokens, tags);
      TokenAnnotation last = null;
      for (int i = 0; i < tokenSpans.length; i++) {
        final TokenAnnotation token = anns[i];
        token.setPos(tags[i]);
        switch (chunks[i].charAt(0)) {
        case 'B':
          if (last != null) last.setChunkEnd(true);
          token.setChunk(chunks[i].substring(2));
          last = token;
          break;
        case 'I':
          token.setChunk(chunks[i].substring(2));
          break;
        case 'O':
          if (last != null) last.setChunkEnd(true);
          last = null;
          break;
        default:
          throw new AssertionError("Unexpected chunk: " + chunks[i] + " at postion " + i +
              " in '" + text + "'");
        }
      }
    }
    for (final TokenAnnotation token : buffer) {
      token.addToIndexes(jcas);
    }
    logger.log(Level.FINE, "annotated " + buffer.size() + " tokens");
  }

  @Override
  public void destroy() {
    tokenizer = null;
    posTagger = null;
    chunker = null;
  }
}
