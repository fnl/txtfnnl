package txtfnnl.utils.stringsim;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestSimilarity {
  @Test
  public void testJaroWinklerParameters() {
    assertEquals(Jaro.INSTANCE.similarity("abc", "cba"),
        new JaroWinkler(0.0, 0).similarity("abc", "cba"), 0.0);
  }

  @Test
  public void testEqualString() {
    assertEquals(1.0, DamerauLevenshtein.INSTANCE.similarity("abc", "abc"), 0.0);
    assertEquals(1.0, Jaro.INSTANCE.similarity("abc", "abc"), 0.0);
    assertEquals(1.0, JaroWinkler.INSTANCE.similarity("abc", "abc"), 0.0);
    assertEquals(1.0, LeitnerLevenshtein.INSTANCE.similarity("abc", "abc"), 0.0);
  }

  @Test
  public void testNotEqualString() {
    assertEquals(0.0, DamerauLevenshtein.INSTANCE.similarity("abc", "def"), 0.0);
    assertEquals(0.0, Jaro.INSTANCE.similarity("abc", "def"), 0.0);
    assertEquals(0.0, JaroWinkler.INSTANCE.similarity("abc", "def"), 0.0);
    assertEquals(0.0, LeitnerLevenshtein.INSTANCE.similarity("abc", "def"), 0.0);
  }

  @Test
  public void testZeroString() {
    assertEquals(1.0, DamerauLevenshtein.INSTANCE.similarity("", ""), 0.0);
    assertEquals(1.0, Jaro.INSTANCE.similarity("", ""), 0.0);
    assertEquals(1.0, JaroWinkler.INSTANCE.similarity("", ""), 0.0);
    assertEquals(1.0, LeitnerLevenshtein.INSTANCE.similarity("", ""), 0.0);
  }

  @Test
  public void testNotEqualZeroString() {
    assertEquals(0.0, DamerauLevenshtein.INSTANCE.similarity("abc", ""), 0.0);
    assertEquals(0.0, DamerauLevenshtein.INSTANCE.similarity("", "abc"), 0.0);
    assertEquals(0.0, Jaro.INSTANCE.similarity("abc", ""), 0.0);
    assertEquals(0.0, Jaro.INSTANCE.similarity("", "abc"), 0.0);
    assertEquals(0.0, JaroWinkler.INSTANCE.similarity("abc", ""), 0.0);
    assertEquals(0.0, JaroWinkler.INSTANCE.similarity("", "abc"), 0.0);
    assertEquals(0.0, LeitnerLevenshtein.INSTANCE.similarity("abc", ""), 0.0);
    assertEquals(0.0, LeitnerLevenshtein.INSTANCE.similarity("", "abc"), 0.0);
  }

  @Test
  public void testSimilarityMeasure() {
    assertEquals(1 - 3.0 / 05, DamerauLevenshtein.INSTANCE.similarity("abxcd", "badc"), 0.0);
    assertEquals(1.0 / 3 * (3.0 / 5 + 3.0 / 4 + 2.0 / 3), // m = 3, t = 1
        Jaro.INSTANCE.similarity("abxcd", "badc"), 0.0); // NB: d doesn't match (window!)
    assertEquals(1 - 4.0 / 10, LeitnerLevenshtein.INSTANCE.similarity("abxcd", "aBcD"), 0.0);
    // a few more examples from WikiPedia for testing Jaro and Jaro-Winkler
    assertEquals(1 - 4.0 / 15, Jaro.INSTANCE.similarity("crate", "trace"), 0.0); // NB: c's and t's
                                                                                 // don't match
                                                                                 // (window!)
    assertEquals(0.961, JaroWinkler.INSTANCE.similarity("martha", "marhta"), 0.001);
    assertEquals(0.840, JaroWinkler.INSTANCE.similarity("dwayne", "duane"), 0.001);
    assertEquals(0.813, JaroWinkler.INSTANCE.similarity("dixon", "dicksonx"), 0.001);
  }

  @Test
  public void testUnicodeSimilarity() {
    String s = "\uD800\uDC00"; // surrogate pair
    String upperA = "\u03B1"; // Alpha
    String lowerA = "\u0391"; // alpha
    assertEquals(1 - 3.0 / 05, DamerauLevenshtein.INSTANCE.similarity(s + "bxcd", "b" + s + "dc"),
        0.0);
    assertEquals(1.0 / 3 * (3.0 / 5 + 3.0 / 4 + 2.0 / 3),
        Jaro.INSTANCE.similarity(s + "bxcd", "b" + s + "dc"), 0.0);
    assertEquals(1 - 4.0 / 10,
        LeitnerLevenshtein.INSTANCE.similarity(lowerA + "b" + s + "cd", upperA + "bCd"), 0.0);
  }
}
