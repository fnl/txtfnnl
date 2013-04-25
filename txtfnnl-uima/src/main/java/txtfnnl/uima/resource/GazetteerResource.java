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
  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * matches together with the entity IDs associated to each hit.
   * 
   * @param input to match the Gazetteer against
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> match(String input);

  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * matches together with the entity IDs associated to each hit.
   * 
   * @param input to match the Gazetteer against
   * @param start scanning input at this offset (inclusive)
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> match(String input, int start);

  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * matches together with the entity IDs associated to each hit.
   * 
   * @param input to match the Gazetteer against
   * @param start scanning input at this offset (inclusive)
   * @param end end scanning input at this offset (exclusive)
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> match(String input, int start, int end);
}
