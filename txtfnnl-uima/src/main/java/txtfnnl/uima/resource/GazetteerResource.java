package txtfnnl.uima.resource;

import java.util.Map;
import java.util.Set;

import txtfnnl.utils.Offset;

/**
 * API specification for resources that "fuzzy match" named entities against some input.
 * <p>
 * The Gazetteer should be {@link GazetteerResource#match(String) matched} against an input String,
 * returning a map of the matching offsets in the input String with their associated resource keys.
 * Because these mapped resource keys might not always exactly match to the actual resource keys in
 * the Gazetteer (approximate matches), it is possible to {@link GazetteerResource#resolve(String)
 * resolve} matched keys that are not actually {@link GazetteerResource#containsKey(String)
 * contained} in the Gazetteer to a set of keys that do exist.
 * 
 * @author Florian Leitner
 */
public interface GazetteerResource extends StringMapResource<Set<String>> {
  /** A private range character to normalize the representation of token boundaries. */
  public static final String SEPARATOR = "\uE3A7";

  /**
   * Scan the input with the Gazetteer, returning the {@link Offset Offsets} of all matches
   * together with the entity key (the normalized entity name) associated to each hit.
   * 
   * @param input to match the Gazetteer against
   * @return A mapping of {@link Offset Offsets} to entity key values for all matches.
   */
  public Map<Offset, String> match(String input);

  /**
   * Return all valid, existing entity keys (normalized entity names) for an entity key that does
   * not exist in the Gazetteer but was found by {@link #match(String)}.
   * 
   * @param key to resolve against the known Gazetteer keys
   * @return A set of existing Gazetteer keys for the resolved key.
   */
  public Set<String> resolve(String key);
}
