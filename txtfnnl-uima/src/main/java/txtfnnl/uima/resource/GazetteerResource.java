package txtfnnl.uima.resource;

import txtfnnl.utils.Offset;

import java.util.List;
import java.util.Map;

/**
 * API specification for resources that String-match named entities against an input String.
 * <p/>
 * The Gazetteer should be {@link GazetteerResource#match(String) matched} against an input String,
 * returning a Map of the Offsets in the input String with their associated resource keys (i.e., the
 * <strong>entity IDs</strong>) stored by the Gazetteer. Because these matched names might not
 * always exactly match to the actual names stored in the Gazetteer (e.g., when using approximate
 * String matching), a Gazetteer must be able to provide the list of ("exact") names for a given
 * resource key. To ensure this behaviour, Gazetteers implement the <code>StringMapResource</code>
 * API for <code>String[]</code> to <code>get(String)</code> the list of names, provide check if
 * they contain a given resource key, enable iterating over the keys, and determining the "size" of
 * a Gazetteer in terms of the number of mapped resource keys.
 *
 * @author Florian Leitner
 */
public
interface GazetteerResource extends StringMapResource<String[]> {
  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * hits together with the entity IDs associated to each hit.
   *
   * @param input to match the Gazetteer against
   *
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public
  Map<Offset, List<String>> match(String input);

  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * hits together with the entity IDs associated to each hit.
   *
   * @param input to match the Gazetteer against
   * @param start scanning input at this offset (inclusive)
   *
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public
  Map<Offset, List<String>> match(String input, int start);

  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * hits together with the entity IDs associated to each hit.
   *
   * @param input to match the Gazetteer against
   * @param start scanning input at this offset (inclusive)
   * @param end   end scanning input at this offset (exclusive)
   *
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public
  Map<Offset, List<String>> match(String input, int start, int end);
}
