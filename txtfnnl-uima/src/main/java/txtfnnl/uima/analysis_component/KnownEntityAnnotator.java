package txtfnnl.uima.analysis_component;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import txtfnnl.uima.Views;
import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.resource.EntityStringMapResource;
import txtfnnl.uima.resource.JdbcConnectionResource;
import txtfnnl.uima.tcas.SemanticAnnotation;

/**
 * A "NER" to detect the presence of names for a pre-defined list of entities.
 * 
 * Parameter settings:
 * <ul>
 * <li>String {@link #PARAM_NAMESPACE} (required)</li>
 * <li>String[] {@link #PARAM_QUERIES} (required)</li>
 * </ul>
 * Resources:
 * <dl>
 * <dt>KnownEntities</dt>
 * <dd>a TSV file of known entities</dd>
 * <dt>EntityNameDb</dt>
 * <dd>a SQL DB of names for the entities</dd>
 * </dl>
 * The <b>KnownEntities</b> resource has to be a TSV file with the following
 * columns:
 * <ol>
 * <li>Document ID: SOFA URI basename (without the file suffix)</li>
 * <li>Entity Type: will be used as the IDs of the SemanticAnnotations, using
 * the <i>Namespace<i> parameter of this Annotator as the base namespace for
 * all SemanticAnnotations</li>
 * <li>Namespace: of the entity, as used in the EntityNameDb (and not to be
 * confused with the <i>Namespace<i> parameter of this Annotator)</li>
 * <li>Identifier: of the entity, as used in the EntityNameDb</li>
 * </ol>
 * 
 * The <b>EntityNameDb</b> resource has to be a database that can produce a
 * list of String names for a given namespace and identifier from the
 * <i>KnownEntities</i> by executing all <i>Queries</i>. The namespace/ID
 * pairs from the <i>KnownEntities</i> will be used as positional parameters
 * in the Queries (namespace first, then identifiers). For example:
 * 
 * <pre>
 *   SELECT entity_names.name FROM entities
 *     JOIN entity_names USING (id)
 *     WHERE entities.namespace = ? AND
 *           entities.id = ?
 * </pre>
 * 
 * @author Florian Leitner
 */
public class KnownEntityAnnotator extends JCasAnnotator_ImplBase {

	/** The namespace to use for all annotated entites. */
	public static final String PARAM_NAMESPACE = "Namespace";

	/** The list of SQL queries to fetch the entity names. */
	public static final String PARAM_QUERIES = "Queries";

	/** The key used for the EntityStringMapResource. */
	public static final String MODEL_KEY_ENTITY_STRING_MAP = "KnownEntities";

	/** The key used for the JdbcConnectionResource. */
	public static final String MODEL_KEY_JDBC_CONNECTION = "EntityNameDb";

	/** The URL of this Annotator. */
	public static final String URL = "http://txtfnnl/KnownEntityAnnotator";

	Logger logger;
	private String namespace = null;
	private String[] queries;
	private EntityStringMapResource documentEntityMap;
	private JdbcConnectionResource connector;
	private Connection conn;
	private int truePositives;
	private int falseNegatives;
	private Map<Integer[], Set<Entity>> matched;

	/**
	 * The string length comparator sorts strings first by length (longest
	 * first), then alphabetically (A-z).
	 * 
	 * This is a helper class for the regular expression generator.
	 * 
	 * @author Florian Leitner
	 */
	static class StringLengthComparator implements Comparator<String> {

		public int compare(String o1, String o2) {
			if (o1.length() > o2.length()) {
				return -1;
			} else if (o1.length() < o2.length()) {
				return 1;
			} else {
				return o1.compareTo(o2);
			}
		}
	}

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		logger = ctx.getLogger();
		truePositives = 0;
		falseNegatives = 0;
		namespace = (String) ctx.getConfigParameterValue(PARAM_NAMESPACE);
		queries = (String[]) ctx.getConfigParameterValue(PARAM_QUERIES);

		if (namespace == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { PARAM_NAMESPACE });

		if (queries == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.CONFIG_SETTING_ABSENT,
			    new Object[] { PARAM_QUERIES });

		try {
			documentEntityMap = (EntityStringMapResource) ctx
			    .getResourceObject(MODEL_KEY_ENTITY_STRING_MAP);
			connector = (JdbcConnectionResource) ctx
			    .getResourceObject(MODEL_KEY_JDBC_CONNECTION);
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		}

		if (documentEntityMap == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.NO_RESOURCE_FOR_PARAMETERS,
			    new Object[] { MODEL_KEY_ENTITY_STRING_MAP });

		if (connector == null)
			throw new ResourceInitializationException(
			    ResourceInitializationException.NO_RESOURCE_FOR_PARAMETERS,
			    new Object[] { MODEL_KEY_JDBC_CONNECTION });

		try {
			conn = connector.getConnection();
		} catch (SQLException e) {
			throw new ResourceInitializationException(e);
		}
	}

	/**
	 * Log the overall Entity recall to INFO.
	 */
	public void destroy() {
		super.destroy();
		float recall = 100 * (float) truePositives /
		               (truePositives + falseNegatives);
		logger.log(Level.INFO,
		    String.format("known entity annotation recall=%.2f%%", recall));
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		// Setup ...
		JCas textCas;
		JCas rawCas;

		try {
			textCas = jcas.getView(Views.CONTENT_TEXT.toString());
			rawCas = jcas.getView(Views.CONTENT_RAW.toString());
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}

		String documentId;

		try {
			documentId = new File(new URI(rawCas.getSofaDataURI())).getName();
		} catch (URISyntaxException e) {
			throw new AnalysisEngineProcessException(e);
		}

		if (documentId.indexOf('.') > -1)
			documentId = documentId.substring(0, documentId.lastIndexOf('.'));

		// Fetch the list of known entities for this document
		List<Entity> list = documentEntityMap.get(documentId);

		if (list == null) {
			logger.log(Level.INFO, "no entities mapped to doc '" + documentId +
			                       "' (" + rawCas.getSofaDataURI() + ")");
		} else {
			// Store a registry of done matches ([start, end]: entity)
			matched = new HashMap<Integer[], Set<Entity>>();

			// Match the names of those entities to the document text
			int[] matches = matchEntities(list, documentId, textCas,
			    Pattern.UNICODE_CASE);

			if (matches != null) {
				// Check missed matches: missed matches are either cases of no
				// match at all or when less than 10% of the average number of
				// matches/entity are found for that entity
				List<Entity> missed = new LinkedList<Entity>();
				int tp = list.size();
				int sum = 0;

				for (int i : matches)
					sum += i;

				int min = sum / matches.length / 10;

				for (int idx = 0; idx > matches.length; ++idx) {
					if (matches[idx] <= min)
						missed.add(list.get(idx));
				}

				// Try to find missed entities with a case-insensitive regex
				if (missed.size() > 0) {
					matches = matchEntities(missed, documentId, textCas,
					    Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

					if (matches != null) {
						for (int idx = 0; idx > matches.length; ++idx) {
							if (matches[idx] == 0) {
								--tp;
								logger.log(Level.INFO,
								    "no names for " + missed.get(idx) +
								            " found in doc '" + documentId +
								            "' (" + rawCas.getSofaDataURI() +
								            ")");
							}
						}
					} else {
						tp -= missed.size();
					}
				}
				truePositives += tp;
				falseNegatives += list.size() - tp;
			} else {
				falseNegatives += list.size();
			}
		}
	}

	/**
	 * Annotate the names of all known entities in the text as
	 * SemanticAnnotation spans.
	 * 
	 * @param list of Entities to match/find
	 * @param documentId of the current CAS
	 * @param textCas the actual SOFA to scan
	 * @param patternFlags for the Java Pattern
	 * @throws AnalysisEngineProcessException if the SQL query or the JDBC
	 *         fails
	 */
	int[] matchEntities(List<Entity> list, String documentId, JCas textCas,
	                    int patternFlags)
	        throws AnalysisEngineProcessException {
		// Create a mapping of all names to their entities in the list
		Map<String, Set<Entity>> nameMap = generateNameMap(list);
		int[] entityMatches = null;
		Integer[] offset = new Integer[2];

		if (nameMap.size() == 0) {
			logger.log(Level.INFO, "no entity names for doc '" + documentId +
			                       "'");
			for (Entity e : list) {
				logger
				    .log(Level.FINE, "doc '" + documentId + "' entity: " + e);
			}
		} else {
			// Generate one "gigantic" regex from all names
			Pattern regex = generateRegex(
			    new ArrayList<String>(nameMap.keySet()), patternFlags);
			String text = textCas.getDocumentText();

			/* As the regex contains versions of the name where any non-
			 * letter or -digit character is allowed in between letter and
			 * digit "token spans", we create a "compressed" version of the
			 * name only consisting of letters and digits. */

			// Store these compressed names separately, as we would rather
			// want to match the "real" names
			Map<String, Set<Entity>> compressionMap = new HashMap<String, Set<Entity>>();

			for (String name : nameMap.keySet()) {
				String compressedName = compressed(name);

				// But only do this if the removal of non-letter and -digit
				// characters does not shorten the name by one third or more
				if (compressedNameIsTwoThirdsOfLength(compressedName, name)) {
					if (compressionMap.containsKey(compressedName)) {
						// The issue: compressed names might merge
						// "entity spaces"
						Set<Entity> entities = compressionMap
						    .get(compressedName);
						// This is why a separate compressionMap is used!
						entities.addAll(nameMap.get(name));
					} else {
						compressionMap.put(compressedName, nameMap.get(name));
					}
				}
			}

			Matcher match = regex.matcher(text);
			entityMatches = new int[list.size()];
			String name;

			while (match.find()) {
				offset[0] = match.start();
				offset[1] = match.end();
				Set<Entity> alreadyMatched;
				name = match.group();
				Map<String, Set<Entity>> map = nameMap;

				if (matched.containsKey(offset)) {
					alreadyMatched = matched.get(offset);
				} else {
					alreadyMatched = new HashSet<Entity>();
					matched.put(offset, alreadyMatched);
				}

				if (!nameMap.containsKey(name)) {
					// If the name does not match, it *should* match to a
					// name in the compressionMap
					name = compressed(name);
					map = compressionMap;
				}

				if (map.containsKey(name)) {
					for (Entity e : map.get(name)) {
						if (!alreadyMatched.contains(e)) {
							// Annotate all entities that map to that name on
							// the
							// matched text span
							annotate(e, textCas, match.start(), match.end());
							// Count a match for that entity
							++entityMatches[list.indexOf(e)];
							alreadyMatched.add(e);
						}
					}
				} else {
					logger.log(Level.WARNING,
					    "name='" + name + "' not found in name map for doc '" +
					            documentId + "'");
					logger.log(
					    Level.INFO,
					    "names='" +
					            Arrays.toString(nameMap.keySet().toArray()) +
					            "' for doc '" + documentId + "'");
					logger.log(Level.FINE, "regex='" + regex.pattern() +
					                       "' for doc '" + documentId + "'");
				}
			}
		}

		// Return the counted matches for each entity
		// (or null, to show we did not even get that far)
		return entityMatches;
	}

	/**
	 * Annotate an entity mention on the CAS at the given offsets.
	 * 
	 * @param e the Entity to annotate
	 * @param textCas with the SOFA
	 * @param start of the entity mention
	 * @param end of the entity mention
	 */
	void annotate(Entity e, JCas textCas, int start, int end) {
		SemanticAnnotation ann = new SemanticAnnotation(textCas);
		ann.setAnnotator(URL);
		ann.setNamespace(namespace);
		ann.setIdentifier(e.getType());
		ann.setConfidence(1.0);
		ann.setBegin(start);
		ann.setEnd(end);
		// Add the original entity ns & id, so we can
		// backtrace
		Property ns = new Property(textCas);
		ns.setName("namespace");
		ns.setValue(e.getNamespace());
		Property id = new Property(textCas);
		id.setName("identifier");
		id.setValue(e.getIdentifier());
		FSArray properties = new FSArray(textCas, 2);
		properties.set(0, ns);
		properties.set(1, id);
		ann.setProperties(properties);
		ann.addToIndexes();
	}

	/**
	 * Return a mapping of each known name to their associated entities.
	 * 
	 * Note that a name can map to multiple entities, just as an entity can
	 * have multiple names.
	 * 
	 * @param entities to create the mapping for
	 * @return a Map of name Strings associated to Entity Sets
	 * @throws AnalysisEngineProcessException if the SQL query or JDBC used to
	 *         fetch the names fails
	 */
	Map<String, Set<Entity>> generateNameMap(List<Entity> entities)
	        throws AnalysisEngineProcessException {
		Map<String, Set<Entity>> nameMap = new HashMap<String, Set<Entity>>();

		for (Entity e : entities) {
			for (String name : getNames(e)) {
				if (!nameMap.containsKey(name))
					nameMap.put(name, new HashSet<Entity>());
				nameMap.get(name).add(e);
			}
		}
		return nameMap;
	}

	/**
	 * Return the name with all non-digit and -letter character removed.
	 * 
	 * @param name to "compress"
	 * @return the "compressed" name
	 */
	static String compressed(String name) {
		StringBuffer compressedName = new StringBuffer();
		int nLen = name.length();
		char c;

		for (int i = 0; i < nLen; i++) {
			c = name.charAt(i);

			if (Character.isLetter(c) || Character.isDigit(c))
				compressedName.append(c);
		}

		return compressedName.toString();
	}

	/**
	 * Return a set of all known names for a given entity.
	 * 
	 * @param entity to fetch names for
	 * @return a Set of names
	 * @throws AnalysisEngineProcessException if the SQL query or JDBC fails
	 */
	Set<String> getNames(Entity entity) throws AnalysisEngineProcessException {
		Set<String> names = new HashSet<String>();
		PreparedStatement stmt;
		ResultSet result;

		for (String query : queries) {
			try {
				stmt = conn.prepareStatement(query,
				    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				stmt.setString(1, entity.getNamespace());
				stmt.setString(2, entity.getIdentifier());
				result = stmt.executeQuery();

				while (result.next())
					names.add(result.getString(1));
			} catch (SQLException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}

		return names;
	}

	/* states for the RegEx builder in generateRegex(List, int) */
	static final private int OTHER = 0;
	static final private int UPPER = 1;
	static final private int ALL_UPPER = 2;
	static final private int LOWER = 3;
	static final private int DIGIT = 4;

	/**
	 * Compile a regular expression for a set of names.
	 * 
	 * @param names String set to use to build the Pattern
	 * @return regex of all names or <code>null</code> if the set is empty
	 */
	static Pattern generateRegex(List<String> names, int flags) {
		if (names.size() == 0)
			return null;

		StringBuffer regex = new StringBuffer();
		Collections.sort(names, new StringLengthComparator());

		for (String name : names) {
			regex.append("\\b");
			regex.append(Pattern.quote(name));

			if (compressedNameIsTwoThirdsOfLength(compressed(name), name)) {
				// Create a version where all variants with spaces, dashes,
				// slashes, or any other non-letter spacing characters are
				// matched at token boundaries. Tokens can be stretches of:
				// Digits, lower-case letters, upper-case letters, and
				// one upper-case letter followed by lower-case letters
				regex.append("\\b|\\b");
				int nLen = name.length();
				int state = OTHER;

				for (int i = 0; i < nLen; i++)
					state = handleCharacter(regex, name.charAt(i), state);
			}
			regex.append("\\b|");
		}

		return Pattern.compile(regex.substring(0, regex.length() - 1),
		    flags);
	}

	private static boolean
	        compressedNameIsTwoThirdsOfLength(String compressedName,
	                                          String name) {
		return (float) compressedName.length() / name.length() > 2.0 / 3.0;
	}

	private static int handleCharacter(StringBuffer buf, char c, int state) {
		if (Character.isUpperCase(c) || Character.isTitleCase(c)) {
			state = handleUppercase(buf, c, state);
		} else if (Character.isLowerCase(c)) {
			state = handleLowercase(buf, c, state);
		} else if (Character.isLetter(c)) {
			if (state == LOWER || state == UPPER || state == ALL_UPPER) {
				buf.append(c);
			} // else skip (modifier letter)!
		} else if (Character.isDigit(c)) {
			state = handleDigit(buf, c, state);
		} else if (state != OTHER && Character.isDefined(c) &&
		           !Character.isISOControl(c)) {
			state = OTHER;
			buf.append("\\W*");
		}
		return state;
	}

	private static int handleDigit(StringBuffer buf, char c, int state) {
		if (state == DIGIT) {
			buf.append(c);
		} else {
			if (state != OTHER)
				buf.append("\\W*");

			state = DIGIT;
			buf.append(c);
		}
		return state;
	}

	private static int handleLowercase(StringBuffer buf, char c, int state) {
		if (state == LOWER) {
			buf.append(c);
		} else if (state == UPPER) {
			state = LOWER;
			buf.append(c);
		} else {
			if (state != OTHER)
				buf.append("\\W*");

			state = LOWER;
			buf.append(c);
		}
		return state;
	}

	private static int handleUppercase(StringBuffer buf, char c, int state) {
		if (state == UPPER) {
			state = ALL_UPPER;
			buf.append(c);
		} else if (state == ALL_UPPER) {
			buf.append(c);
		} else {
			if (state != OTHER)
				buf.append("\\W*");

			state = UPPER;
			buf.append(c);
		}
		return state;
	}
}
