package txtfnnl.utils;

import java.util.Comparator;

/**
 * This comparator sorts strings first by decreasing length (longest first), then
 * lexicographically, ascending, using {@link java.lang.String#compareTo(String)}.
 * 
 * @author Florian Leitner
 */
public enum StringLengthComparator implements Comparator<String> {
    INSTANCE;
    public int compare(String o1, String o2) {
        if (o1.length() > o2.length()) return -1;
        else if (o1.length() < o2.length()) return 1;
        else return o1.compareTo(o2);
    }
}
