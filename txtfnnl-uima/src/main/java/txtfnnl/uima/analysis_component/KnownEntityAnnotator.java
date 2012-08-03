package txtfnnl.uima.analysis_component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import txtfnnl.uima.cas.Property;
import txtfnnl.uima.resource.Entity;
import txtfnnl.uima.resource.JdbcConnectionResource;
import txtfnnl.uima.tcas.SemanticAnnotation;
import txtfnnl.utils.Offset;
import txtfnnl.utils.StringLengthComparator;

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
 * in the Queries (namespace first, then identifier!). For example:
 * 
 * <pre>
 *   SELECT name FROM entities WHERE namespace=? AND identifier=?
 * </pre>
 * 
 * @author Florian Leitner
 */
public class KnownEntityAnnotator extends KnownEvidenceAnnotator<Set<Entity>> {

	/** The namespace to use for all annotated entites. */
	public static final String PARAM_NAMESPACE = "EntityNamespace";

	/** The list of SQL queries to fetch the entity names. */
	public static final String PARAM_QUERIES = "Queries";

	/** The key used for the JdbcConnectionResource. */
	public static final String MODEL_KEY_JDBC_CONNECTION = "EntityNameDb";

	/** The URI of this Annotator. */
	public static final String URI = "http://txtfnnl/KnownEntityAnnotator";

	/** A separator between entity name tokens. */
	static final String SEPARATOR = "[^\\p{L}\\p{Nd}\\p{Nl}]{,3}";

	/* states for the RegEx builder in generateRegex(List, int) */
	private static final int OTHER = 0;
	private static final int UPPER = 1;
	private static final int ALL_UPPER = 2;
	private static final int LOWER = 3;
	private static final int DIGIT = 4;

	/* flags for the two RegEx matching modes */
	private static final int CASE_INSENSITIVE = (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final int CASE_SENSITIVE = Pattern.UNICODE_CASE;

	/* internal state of the AE */
	private String namespace = null; // PARAM_NAMESPACE
	private String[] queries; // PARAM_QUERIES
	private PreparedStatement[] statements;
	private JdbcConnectionResource connector; // MODEL_KEY_JDBC_CONNECTION
	private Connection conn; // the connection from MODEL_KEY_JDBC_CONNECTION
	private Set<Entity> unknownEntities; // entities not in the DB

	@Override
	public void initialize(UimaContext ctx)
	        throws ResourceInitializationException {
		super.initialize(ctx);

		unknownEntities = new HashSet<Entity>();
		namespace = (String) ctx.getConfigParameterValue(PARAM_NAMESPACE);
		queries = (String[]) ctx.getConfigParameterValue(PARAM_QUERIES);

		try {
			connector = (JdbcConnectionResource) ctx
			    .getResourceObject(MODEL_KEY_JDBC_CONNECTION);
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		}

		ensureNotNull(namespace,
		    ResourceInitializationException.CONFIG_SETTING_ABSENT,
		    PARAM_NAMESPACE);

		ensureNotNull(queries,
		    ResourceInitializationException.CONFIG_SETTING_ABSENT,
		    PARAM_QUERIES);

		ensureNotNull(connector,
		    ResourceInitializationException.NO_RESOURCE_FOR_PARAMETERS,
		    MODEL_KEY_JDBC_CONNECTION);

		statements = new PreparedStatement[queries.length];
		
		try {
			conn = connector.getConnection();

			for (int idx = 0; idx < queries.length; ++idx)
				statements[idx] = conn.prepareStatement(queries[idx],
				    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		} catch (SQLException e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	void process(String documentId, JCas textCas, Set<Entity> entities)
	        throws AnalysisEngineProcessException {
		int numEntities;
		// Store a "registry" of found matches (to avoid "double tagging"
		// if case-insensitive matching is attempted)
		Map<Offset, Set<Entity>> matched = new HashMap<Offset, Set<Entity>>();
		numEntities = entities.size();
		checksum += numEntities;

		// Match the names of those entities to the document text
		Map<Entity, Integer> matches = annotateEntities(entities, matched,
		    documentId, textCas, CASE_SENSITIVE);

		if (matches != null) {
			// Check missed matches: missed matches are either cases of no
			// match at all or when less than 10% of the average number of
			// matches/entity are found for that entity
			int tp = entities.size();
			int sum = 0;

			for (int i : matches.values())
				sum += i;

			int min = sum / matches.size() / 10;
			Iterator<Entity> it = entities.iterator();

			while (it.hasNext()) {
				if (matches.get(it.next()) > min)
					it.remove();
			}

			// Try to find missed entities with a case-insensitive regex
			if (entities.size() > 0) {
				if (logger.isLoggable(Level.FINE))
					logger.log(
					    Level.FINE,
					    "case-insensitive matching for " +
					            Arrays.toString(entities
					                .toArray(new Entity[] {})));
				matches = annotateEntities(entities, matched, documentId,
				    textCas, CASE_INSENSITIVE);

				if (matches != null) {
					for (Entity e : entities) {
						if (matches.get(e) == 0) {
							--tp;
							logger.log(Level.INFO, "no names for " + e +
							                       " found in doc '" +
							                       documentId + "'");
						}
					}
				} else {
					tp -= entities.size();
				}
			}
			truePositives += tp;
			falseNegatives += numEntities - tp;
		} else {
			falseNegatives += numEntities;
		}
	}

	/**
	 * Annotate the names of all known entities in the text as
	 * SemanticAnnotation spans.
	 * 
	 * @param entities to match/find
	 * @param alreadyMatched entities at a given offset
	 * @param documentId of the current CAS
	 * @param textCas the actual SOFA to scan
	 * @param patternFlags for the Java Pattern
	 * @return the number of matches for each Entity in list or
	 *         <code>null</code> if no matching could be done
	 * @throws AnalysisEngineProcessException if the SQL query or the JDBC
	 *         fails
	 */
	        Map<Entity, Integer>
	        annotateEntities(Set<Entity> entities,
	                         Map<Offset, Set<Entity>> alreadyMatched,
	                         String documentId, JCas textCas, int patternFlags)
	                throws AnalysisEngineProcessException {
		// Create a mapping of all names to their entities in the list
		Map<String, Set<Entity>> nameMap = generateNameMap(entities);
		Map<Entity, Integer> entityMatches = null;

		if (nameMap.size() == 0) {
			if (patternFlags == CASE_SENSITIVE)
				logger.log(Level.WARNING,
				    "no known names for any entity in doc '" + documentId +
				            "' found");
		} else {
			entityMatches = matchEntities(entities, documentId, textCas,
			    patternFlags, nameMap, alreadyMatched);
		}

		// Return the counted matches for each entity
		// (or null, to show we did not even get that far)
		return entityMatches;
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
	Map<String, Set<Entity>> generateNameMap(Set<Entity> entities)
	        throws AnalysisEngineProcessException {
		Map<String, Set<Entity>> nameMap = new HashMap<String, Set<Entity>>();

		for (Entity e : entities) {
			if (unknownEntities.contains(e))
				continue;

			for (String name : getNames(e)) {
				if (!nameMap.containsKey(name))
					nameMap.put(name, new HashSet<Entity>());

				nameMap.get(name).add(e);
			}
		}
		return nameMap;
	}

	/**
	 * Return a set of all known names for a given entity.
	 * 
	 * @param entity to fetch names for
	 * @return a Set of names
	 * @throws AnalysisEngineProcessException if the SQL query or JDBC fails
	 */
	private Set<String> getNames(Entity entity)
	        throws AnalysisEngineProcessException {
		Set<String> names = new HashSet<String>();
		ResultSet result;

		for (PreparedStatement stmt : statements) {
			try {
				stmt.setString(1, entity.getNamespace());
				stmt.setString(2, entity.getIdentifier());
				result = stmt.executeQuery();

				while (result.next()) {
					String n = result.getString(1);

					if (n.length() > 0)
						names.add(n);
				}
			} catch (SQLException e) {
				throw new AnalysisEngineProcessException(e);
			}
		}

		if (names.size() == 0) {
			logger.log(Level.WARNING, "no known names for " + entity);
			unknownEntities.add(entity);
		}
		return names;
	}

	/**
	 * Match the mapped names of all known entities in the text, annotating
	 * them as SemanticAnnotation spans.
	 * 
	 * @param entities of Entities to match/find
	 * @param documentId of the current CAS
	 * @param textCas the actual SOFA to scan
	 * @param patternFlags for the Java Pattern
	 * @param nameMap mapping of all names to their entities
	 * @param alreadyMatched entities at a given offset
	 * @return the number of matches for each Entity in list
	 */
	Map<Entity, Integer>
	        matchEntities(Set<Entity> entities, String documentId,
	                      JCas textCas, int patternFlags,
	                      Map<String, Set<Entity>> nameMap,
	                      Map<Offset, Set<Entity>> alreadyMatched) {
		// Generate one "gigantic" regex from all names
		Pattern regex = generateRegex(new ArrayList<String>(nameMap.keySet()),
		    patternFlags);
		String text = textCas.getDocumentText();
		Matcher match = regex.matcher(text);
		boolean caseInsensitiveMatching = (patternFlags == CASE_INSENSITIVE);
		Map<Entity, Integer> matchCounts = new HashMap<Entity, Integer>(
		    entities.size());

		for (Entity e : entities)
			matchCounts.put(e, Integer.valueOf(0));

		/* As the regex contained versions of the name where any non- letter
		 * or -digit character is allowed in between letter and digit
		 * "token spans", we create a "compressed" version of the name only
		 * consisting of letters and digits. */

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
					Set<Entity> eSet = compressionMap.get(compressedName);
					// This is why a separate compressionMap is used!
					eSet.addAll(nameMap.get(name));
				} else {
					compressionMap.put(compressedName, nameMap.get(name));
				}
			}
		}

		// Expand the mappings to cover the lower-case versions -
		// this is needed to find the correct names if case-insensitive
		if (caseInsensitiveMatching) {
			expandMapWithLowerCase(nameMap);
			expandMapWithLowerCase(compressionMap);
		}

		while (match.find()) {
			String name = match.group();
			String lower = caseInsensitiveMatching ? name.toLowerCase() : null;
			Map<String, Set<Entity>> map = nameMap;
			Set<Entity> done;
			Offset offset = new Offset(match.start(), match.end());

			if (alreadyMatched.containsKey(offset)) {
				done = alreadyMatched.get(offset);
			} else {
				done = new HashSet<Entity>();
				alreadyMatched.put(offset, done);
			}

			if (!nameMap.containsKey(name) &&
			    (lower == null || !nameMap.containsKey(lower))) {
				// If the name does not match, it *should* match to a
				// name in the compressionMap
				name = compressed(name);
				lower = caseInsensitiveMatching ? name.toLowerCase() : null;
				map = compressionMap;
			}

			if (map.containsKey(name)) {
				annotateAll(entities, textCas, offset, matchCounts, done,
				    map.get(name));
			} else if (lower != null && map.containsKey(lower)) {
				annotateAll(entities, textCas, offset, matchCounts, done,
				    map.get(lower));
			} else {
				logFailedMatch(documentId, nameMap, regex, text,
				    compressionMap, name, offset);
			}
		}
		return matchCounts;
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
	 * Compile a regular expression for a set of names.
	 * 
	 * For each name, add an exact word-boundary match, and if the compressed
	 * name is at least two thirds the length of the original name, add a
	 * pattern for the compressed name where each token boundary may be
	 * separated by up to three non-letter, -digit, or -numeral characters.
	 * 
	 * @param names String set to use to build the Pattern
	 * @return regex of all names or <code>null</code> if the set is empty
	 */
	static Pattern generateRegex(List<String> names, int flags) {
		if (names.size() == 0)
			return null;

		StringBuffer regex = new StringBuffer();
		Collections.sort(names, StringLengthComparator.INSTANCE);

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

		return Pattern.compile(regex.substring(0, regex.length() - 1), flags);
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

	private void expandMapWithLowerCase(Map<String, Set<Entity>> map) {
		for (String n : map.keySet().toArray(new String[] {})) {
			String l = n.toLowerCase();

			if (!l.equals(n)) {
				if (!map.containsKey(l))
					map.put(l, new HashSet<Entity>());

				map.get(l).addAll(map.get(n));
			}
		}
	}

	private void annotateAll(Set<Entity> list, JCas textCas, Offset span,
	                         Map<Entity, Integer> matchCount,
	                         Set<Entity> alreadyMatched, Set<Entity> entities) {
		for (Entity e : entities) {
			if (!alreadyMatched.contains(e)) {
				// Annotate all entities that map to that name on
				// the
				// matched text span
				SemanticAnnotation ann = new SemanticAnnotation(textCas, span);
				ann.setNamespace(namespace);
				ann.setIdentifier(e.getType());
				ann.setAnnotator(URI);
				ann.setConfidence(1.0);
				// Add the original entity ns & id, so we can
				// backtrace
				Property ns = new Property(textCas);
				Property id = new Property(textCas);
				ns.setName("namespace");
				id.setName("identifier");
				ns.setValue(e.getNamespace());
				id.setValue(e.getIdentifier());
				FSArray properties = new FSArray(textCas, 2);
				properties.set(0, ns);
				properties.set(1, id);
				ann.setProperties(properties);
				ann.addToIndexes();
				// Count a match for that entity
				// Note: lots of auto-boxing...
				matchCount.put(e, 1 + matchCount.get(e));
				alreadyMatched.add(e);
			}
		}
	}

	private void logFailedMatch(String documentId,
	                            Map<String, Set<Entity>> nameMap,
	                            Pattern regex, String text,
	                            Map<String, Set<Entity>> compressionMap,
	                            String name, Offset offset) {
		logger.log(Level.WARNING, "name='" + name +
		                          "' not found in name map for doc '" +
		                          documentId + "'");
		logger.log(Level.INFO,
		    "map names=" + Arrays.toString(nameMap.keySet().toArray()) +
		            " for doc '" + documentId + "'");

		if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "surrounding text='" +
			            text.substring(
			                offset.start() - 10 > 0 ? offset.start() - 10 : 0,
			                offset.end() + 10 < text.length()
			                        ? offset.end() + 10
			                        : text.length()) + "' in doc '" +
			            documentId + "'");
			logger.log(
			    Level.FINE,
			    "compressed names=" +
			            Arrays.toString(compressionMap.keySet().toArray()) +
			            " for doc '" + documentId + "'");

			logger.log(Level.FINE, "regex='" + regex.pattern() +
			                       "' for doc '" + documentId + "'");
		}
	}
}
