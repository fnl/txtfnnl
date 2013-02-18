package txtfnnl.utils.stringsim;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestDistance {
  @Test
  public void testDamerauLevenshteinOnEqualStrings() {
    assertEquals(0, DamerauLevenshtein.INSTANCE.distance("abc", "abc"));
  }

  @Test
  public void testDamerauLevenshteinDistanceOneChar() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    assertEquals(0, measure.distance("a", "a"));
    assertEquals(0, measure.distance("A", "A"));
    assertEquals(1, measure.distance("a", "A"));
    assertEquals(1, measure.distance("A", "a"));
    assertEquals(1, measure.distance("a", "b"));
    assertEquals(1, measure.distance("A", "B"));
  }

  @Test
  public void testDamerauLevenshteinDistanceTwoChars() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    assertEquals(0, measure.distance("aa", "aa"));
    assertEquals(1, measure.distance("ab", "ba"));
    assertEquals(2, measure.distance("aa", "AA"));
  }

  @Test
  public void testDamerauLevenshteinDistanceZeroLength() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    assertEquals(3, measure.distance("abc", ""));
    assertEquals(3, measure.distance("", "abc"));
    assertEquals(0, measure.distance("", ""));
  }

  @Test
  public void testDamerauLevenshteinOnUnequalLengthStrings() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    assertEquals(3, measure.distance("A", "AAAA"));
    assertEquals(3, measure.distance("AAAA", "A"));
    assertEquals(4, measure.distance("B", "AAAA"));
    assertEquals(4, measure.distance("AAAA", "B"));
  }

  @Test
  public void testDamerauLevenshteinOnUnicodeString() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    String s = "\uD800\uDC00"; // surrogate pair
    // sure surrogate pairs have the same distance as when using any other character
    assertEquals(2, measure.distance("asa", "sas"));
    assertEquals(2, measure.distance("a" + s + "a", s + "a" + s));
  }

  @Test
  public void testDamerauLevenshteinOnThreeChars() {
    Distance measure = DamerauLevenshtein.INSTANCE;
    assertEquals(1, measure.distance("bba", "bab"));
    assertEquals(2, measure.distance("bba", "abb"));
    assertEquals(1, measure.distance("bab", "bba"));
    assertEquals(1, measure.distance("bab", "abb"));
    assertEquals(2, measure.distance("abb", "bba"));
    assertEquals(1, measure.distance("abb", "bab"));
  }

  @Test
  public void testLeitnerLevenshteinOnEqualStrings() {
    assertEquals(0, LeitnerLevenshtein.INSTANCE.distance("abc", "abc"));
  }

  @Test
  public void testLeitnerLevenshteinDistanceOneChar() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    assertEquals(0, measure.distance("a", "a"));
    assertEquals(0, measure.distance("A", "A"));
    assertEquals(1, measure.distance("a", "A"));
    assertEquals(1, measure.distance("A", "a"));
    assertEquals(2, measure.distance("a", "b"));
    assertEquals(2, measure.distance("A", "B"));
  }

  @Test
  public void testLeitnerLevenshteinDistanceTwoChars() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    assertEquals(0, measure.distance("aa", "aa"));
    assertEquals(4, measure.distance("ab", "ba"));
    assertEquals(2, measure.distance("aa", "AA"));
  }

  @Test
  public void testLeitnerLevenshteinDistanceZeroLength() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    assertEquals(6, measure.distance("abc", ""));
    assertEquals(6, measure.distance("", "abc"));
    assertEquals(0, measure.distance("", ""));
  }

  @Test
  public void testLeitnerLevenshteinOnUnequalLengthStrings() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    assertEquals(6, measure.distance("A", "AAAA"));
    assertEquals(6, measure.distance("AAAA", "A"));
    assertEquals(8, measure.distance("B", "AAAA"));
    assertEquals(8, measure.distance("AAAA", "B"));
  }

  @Test
  public void testLeitnerLevenshteinOnUnicodeString() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    String s = "\uD800\uDC00"; // surrogate pair
    // sure surrogate pairs have the same distance as when using any other character
    assertEquals(4, measure.distance("asa", "sas"));
    assertEquals(4, measure.distance("a" + s + "a", s + "a" + s));
  }

  @Test
  public void testLeitnerLevenshteinOnThreeChars() {
    Distance measure = LeitnerLevenshtein.INSTANCE;
    int equal = 0;
    int oneCh = 1;
    int twoCh = 2;
    int three = 3;
    assertEquals(equal, measure.distance("AAA", "AAA"));
    assertEquals(twoCh, measure.distance("AAA", "Aaa"));
    assertEquals(twoCh, measure.distance("AAA", "aAa"));
    assertEquals(twoCh, measure.distance("AAA", "aaA"));
    assertEquals(three, measure.distance("AAA", "aaa"));
    assertEquals(twoCh, measure.distance("Aaa", "AAA"));
    assertEquals(equal, measure.distance("Aaa", "Aaa"));
    assertEquals(twoCh, measure.distance("Aaa", "aAa"));
    assertEquals(twoCh, measure.distance("Aaa", "aaA"));
    assertEquals(oneCh, measure.distance("Aaa", "aaa"));
    assertEquals(twoCh, measure.distance("aAa", "AAA"));
    assertEquals(twoCh, measure.distance("aAa", "Aaa"));
    assertEquals(equal, measure.distance("aAa", "aAa"));
    assertEquals(twoCh, measure.distance("aAa", "aaA"));
    assertEquals(oneCh, measure.distance("aAa", "aaa"));
    assertEquals(twoCh, measure.distance("aaA", "AAA"));
    assertEquals(twoCh, measure.distance("aaA", "Aaa"));
    assertEquals(twoCh, measure.distance("aaA", "aAa"));
    assertEquals(equal, measure.distance("aaA", "aaA"));
    assertEquals(oneCh, measure.distance("aaA", "aaa"));
    assertEquals(three, measure.distance("aaa", "AAA"));
    assertEquals(oneCh, measure.distance("aaa", "Aaa"));
    assertEquals(oneCh, measure.distance("aaa", "aAa"));
    assertEquals(oneCh, measure.distance("aaa", "aaA"));
    assertEquals(equal, measure.distance("aaa", "aaa"));
  }
}
