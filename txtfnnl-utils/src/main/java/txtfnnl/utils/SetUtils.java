/**
 * 
 */
package txtfnnl.utils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class SetUtils {

	private SetUtils() {
		throw new AssertionError("not allowed");
	}

	/**
	 * Create a list of lists that are composed of one item of each set in a
	 * given list of sets.
	 * 
	 * @param listOfSets to compose the list of lists from
	 * @return a list of lists with one item from each set
	 * @throws IllegalArgumentException if any set is empty
	 */
	public static <T> List<List<T>> combinate(List<Set<T>> listOfSets)
	        throws IllegalArgumentException {
		List<List<T>> combinations = new LinkedList<List<T>>();
		int len = listOfSets.size();

		for (Set<T> set : listOfSets)
			extendCombinations(combinations, set, len);

		return combinations;
	}

	private static <T> void extendCombinations(List<List<T>> combinations,
	                                           Set<T> items, int len)
	        throws IllegalArgumentException {
		int total = items.size();
		int size = combinations.size();
		int idx;

		if (total == 0)
			throw new IllegalArgumentException(
			    "combining an empty set is impossible");

		for (T item : items) {
			if (size == 0) {
				List<T> newList = new ArrayList<T>(len);
				newList.add(item);
				combinations.add(newList);
			} else if (--total == 0) {
				for (idx = 0; idx < size; idx++) {
					List<T> original = combinations.get(idx);
					original.add(item);
				}
			} else {
				for (idx = 0; idx < size; idx++) {
					List<T> original = combinations.get(idx);
					List<T> clone = new ArrayList<T>(len);
					clone.addAll(original);
					clone.add(item);
					combinations.add(clone);
				}
			}
		}
	}
}
