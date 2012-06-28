package txtfnnl.uima.analysis_component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.Views;
import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

public class KnownEntityTypeAnnotator extends JCasAnnotator_ImplBase {

	public static final String PARAM_NAMESPACE = "Namespace";

	public static final String URL = "http://txtfnnl/";

	private String namespace = null;
	private EntityStringMapResource documentEntityMap;
	private JdbcConnectionResource connector;
	private Connection conn;

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		namespace = (String) ctx.getConfigParameterValue(PARAM_NAMESPACE);

		if (namespace == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { PARAM_NAMESPACE });

		try {
			documentEntityMap = (EntityStringMapResource) ctx
			    .getResourceObject("KnownEntities");
			connector = (JdbcConnectionResource) ctx
			    .getResourceObject("EntityDb");
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		}

		try {
			conn = connector.getConnection();
		} catch (SQLException e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas textCas;
		JCas rawCas;

		try {
			textCas = jcas.getView(Views.CONTENT_TEXT.toString());
			rawCas = jcas.getView(Views.CONTENT_RAW.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		String text = textCas.getDocumentText();
		String documentId;

		try {
			documentId = new File(new URI(rawCas.getSofaDataURI())).getName();
		} catch (URISyntaxException e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (documentId.indexOf('.') > -1)
			documentId = documentId.substring(0, documentId.lastIndexOf('.'));

		List<Entity> list = documentEntityMap.get(documentId);
		Map<String, List<Pattern>> regexMap = generateRegexMap(list);

		for (String entityType : regexMap.keySet()) {
			for (Pattern namePattern : regexMap.get(entityType)) {
				Matcher m = namePattern.matcher(text);

				while (m.find()) {
					SemanticAnnotation ann = new SemanticAnnotation(textCas);
					ann.setAnnotator(URL);
					ann.setNamespace(namespace);
					ann.setIdentifier(entityType);
					ann.setConfidence(1.0);
					ann.setBegin(m.start());
					ann.setEnd(m.end());
					ann.addToIndexes();
				}
			}
		}
	}

	Map<String, List<Pattern>> generateRegexMap(List<Entity> entities)
	        throws AnalysisEngineProcessException {
		Map<String, List<Pattern>> regexMap = new HashMap<String, List<Pattern>>();

		for (Entity e : entities) {
			String type = e.getType();

			if (!regexMap.containsKey(type))
				regexMap.put(type, new LinkedList<Pattern>());

			List<Pattern> patterns = regexMap.get(type);

			for (String name : getNames(e)) {
				patterns.add(Pattern
				    .compile("\\b" + name + "|" + name + "\\b"));
			}
		}

		return regexMap;
	}

	List<String> getNames(Entity entity) throws AnalysisEngineProcessException {
		List<String> names = new LinkedList<String>();
		Statement stmt;
		
		try {
			stmt = conn.createStatement();
			ResultSet result = stmt.executeQuery("SELECT name FROM ..."); // TODO
			
			while (result.next())
				names.add(result.getString(1));
		} catch (SQLException e) {
			throw new AnalysisEngineProcessException(e);
		}
		
		return names;
	}
}
