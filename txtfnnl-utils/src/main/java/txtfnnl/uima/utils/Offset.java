package txtfnnl.uima.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A data structure to manage comparable offset values. Offset values are lists of integers with
 * length 1 or 2n where n > 0. An Offset is immutable. Two Offsets with exactly the same values are
 * considered equal and have the same hash codes.
 * 
 * @author Florian Leitner
 */
public class Offset implements Comparable<Offset> {
    private final int[] values;

    /**
     * Create a new Offset. Offset values always have to be increasing from one value to the next.
     * 
     * @param values one or 2n offset values
     * @throws IllegalArgumentException if the wrong number of values is given or if the values are
     *         not incremental
     */
    public Offset(int... values) {
        if (values.length == 0) throw new IllegalArgumentException("no offset values");
        else if (values.length > 2 && values.length % 2 != 0)
            throw new IllegalArgumentException("uneven number of offset values");
        int last = values[0];
        if (last < 0) throw new IllegalArgumentException("offset value < 0 (" + last + ")");
        for (int idx = 1; idx < values.length; ++idx) {
            if (values[idx] <= last)
                throw new IllegalArgumentException("offset values [" + last + ", " + values[idx] +
                    "] not ascending");
            last = values[idx];
        }
        this.values = values.clone();
    }

    /**
     * A (fast) copy constructor.
     * 
     * @param clone to copy
     */
    public Offset(Offset clone) {
        values = clone.values();
    }

    /**
     * The start of this Offset span.
     * 
     * @return the start position
     */
    public int start() {
        return values[0];
    }

    /**
     * The end of this Offset span.
     * 
     * @return the end position
     */
    public int end() {
        return values[values.length - 1];
    }

    /**
     * Get a specific offset value.
     * 
     * @param idx position of the value
     * @return the offset value
     */
    public int get(int idx) {
        return values[idx];
    }

    /**
     * Fetch a list of all offset values.
     * 
     * @return an array of the offset values
     */
    public int[] values() {
        return values.clone();
    }

    /**
     * Return the number of values this Offset has.
     * 
     * @return the number of values
     */
    public int size() {
        return values.length;
    }

    /**
     * Compare this Offset to another. An offset that starts before another is considered smaller,
     * while with equal starts, the one with the larger end value is considered smaller for
     * ordering. If the starts and ends are equal, the Offset with the first value that is larger
     * than the other's is ordered first.
     * 
     * @param other offset to compare
     * @return -1 if this Offset is smaller than the other, 1 if larger, and 0 if they are equal.
     */
    public int compareTo(Offset other) {
        /* compare start values */
        if (start() < other.start()) return -1;
        else if (start() > other.start()) return 1;
        // start values are equal...
        /* compare end values */
        if (end() > other.end()) return -1;
        else if (end() < other.end()) return 1;
        // end values are equal...
        /* compare internal values */
        final int[] other_values = other.values();
        for (int idx = values.length; idx-- > 0;) {
            if (values[idx] != other_values[idx])
            // order the one with a larger offset value first
                return values[idx] > other_values[idx] ? -1 : 1;
        }
        // all values are the same...
        return 0;
    }

    /**
     * Test if the other offset is contained exactly within this offset.
     * 
     * @param other offset to check
     * @return true if all spans (and, if present, sub-spans) of the other offset are contained
     *         exactly within this offset
     */
    public boolean contains(Offset other) {
        final int oLen = other.size();
        if (!Offset.contains(start(), end(), other.start(), other.end())) return false; // impossible
                                                                                        // that the
                                                                                        // other is
                                                                                        // within
                                                                                        // this
                                                                                        // Offset
        else if (values.length == 2 && oLen < 3) return true; // other must have length 1 or 2,
                                                              // within this Offset
        else if (values.length == 1) return true; // other must be an Offset at the same position
        else {
            // at least one of the two Offsets has more than 2 values,
            // the other Offset's start & end are within this offset,
            // and this Offset's size is 2 or more
            final Set<Offset> matches = new HashSet<Offset>();
            final List<Offset> tests = new LinkedList<Offset>();
            final int[] other_values = other.values();
            int idx, start, end;
            if (oLen < 3) {
                tests.add(other);
            } else {
                for (idx = oLen / 2; idx-- > 0;) {
                    tests.add(new Offset(other_values[idx * 2], other_values[idx * 2 + 1]));
                }
            }
            for (idx = values.length / 2; idx-- > 0;) {
                start = values[idx * 2];
                end = values[idx * 2 + 1];
                for (final Offset m : tests) {
                    if (Offset.contains(start, end, m.start(), m.end())) {
                        matches.add(m);
                    }
                }
            }
            return matches.size() == tests.size();
        }
    }

    /**
     * True if start1 <= start2 and end1 >= end2.
     */
    private static boolean contains(int start1, int end1, int start2, int end2) {
        return start1 <= start2 && end1 >= end2;
    }

    @Override
    public Offset clone() {
        return new Offset(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        else if (!(o instanceof Offset)) return false;
        final Offset other = (Offset) o;
        if (other.size() == values.length) {
            for (int idx = values.length; idx-- > 0;) {
                if (values[idx] != other.values[idx]) return false;
            }
            return true;
        } else return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < values.length; ++idx) {
            sb.append(values[idx]);
            if (idx + 1 != values.length) {
                if (idx % 2 == 0) {
                    sb.append(':');
                } else {
                    sb.append(',');
                }
            }
        }
        return sb.toString();
    }
}
