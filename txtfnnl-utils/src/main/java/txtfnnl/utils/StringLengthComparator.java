package txtfnnl.utils;

import java.util.Comparator;

/**
 * The string length comparator sorts strings first by decreasing length
 * (longest first), then alphabetically, ascending, using
 * {@link java.lang.String#compareTo(String)}.
 * 
 * @author Florian Leitner
 */
public enum StringLengthComparator implements Comparator<String> {
	INSTANCE;

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
