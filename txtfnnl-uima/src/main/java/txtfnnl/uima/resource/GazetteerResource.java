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
  /** Whether to enable reverse scanning for matches (default: <code>false</code>). */
  public static final String PARAM_REVERSE_SCANNING = "ReverseScanning";

  /**
   * Check the entire input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * matches together with the entity IDs associated to each hit.
   * 
   * @param input to match the Gazetteer against
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> match(String input);

  /**
   * Scan the start of input with the Gazetteer, returning a map of {@link Offset Offsets} of all
   * matches together with the entity IDs associated to each hit <i>at the beginning of input
   * sequence</i> only.
   * 
   * @param prefix to scan with the Gazetteer against
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> scan(String prefix);
  public Map<Offset, Set<String>> scan(String prefix, int baseOffset);
  
  /**
   * Works just as {@link #scan(String)}, but scans from the <b>end</b> of the input.
   * <p>
   * To use this function, the {@link #PARAM_REVERSE_SCANNING} flag has to be set.
   * 
   * @param suffix scanned for matches, starting the scan from the <b>end</b>
   * @return A mapping of {@link Offset Offsets} to entity IDs for all hits.
   */
  public Map<Offset, Set<String>> reverseScan(String suffix);
  public Map<Offset, Set<String>> reverseScan(String suffix, int baseOffset);
  
  /** Return <code>true</code> if the {@link #PARAM_REVERSE_SCANNING} flag is set. */
  public boolean canScanReverse();
}
