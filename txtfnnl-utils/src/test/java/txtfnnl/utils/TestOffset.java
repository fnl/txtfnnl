package txtfnnl.utils;

import org.junit.Assert;
import org.junit.Test;

import txtfnnl.utils.Offset;

public class TestOffset {
  Offset oneValue = new Offset(1);
  Offset twoValues = new Offset(1, 2);
  Offset moreValues = new Offset(1, 2, 3, 4);

  @Test
  public void testHashCode() {
    final Offset other = new Offset(oneValue.values());
    Assert.assertEquals(other.hashCode(), oneValue.hashCode());
    Assert.assertEquals(other.hashCode(), other.hashCode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOffsetIntArrayNoValues() {
    new Offset();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOffsetIntArrayNotIncremental() {
    new Offset(1, 1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOffsetIntArrayDecremental() {
    new Offset(2, 1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOffsetIntArrayUnevenLength() {
    new Offset(1, 2, 3);
  }

  @Test
  public void testOffsetOffset() {
    Assert.assertEquals(oneValue, new Offset(oneValue));
  }

  @Test
  public void testStart() {
    Assert.assertEquals(1, oneValue.start());
    Assert.assertEquals(1, twoValues.start());
    Assert.assertEquals(1, moreValues.start());
  }

  @Test
  public void testEnd() {
    Assert.assertEquals(1, oneValue.end());
    Assert.assertEquals(2, twoValues.end());
    Assert.assertEquals(4, moreValues.end());
  }

  @Test
  public void testGet() {
    Assert.assertEquals(2, moreValues.get(1));
  }

  @Test
  public void testValues() {
    Assert.assertArrayEquals(new int[] { 1, 2 }, twoValues.values());
  }

  @Test
  public void testSize() {
    Assert.assertEquals(1, oneValue.size());
    Assert.assertEquals(2, twoValues.size());
    Assert.assertEquals(4, moreValues.size());
  }

  @Test
  public void testCompareTo() {
    Assert.assertEquals(-1, new Offset(1).compareTo(new Offset(2)));
    Assert.assertEquals(1, new Offset(2).compareTo(new Offset(1)));
    Assert.assertEquals(-1, new Offset(1, 3).compareTo(new Offset(1, 2)));
    Assert.assertEquals(1, new Offset(1, 2).compareTo(new Offset(1, 3)));
    Assert.assertEquals(-1, new Offset(1, 2, 6, 9).compareTo(new Offset(1, 2, 5, 9)));
  }

  @Test
  public void testContainsOffset() {
    Assert.assertFalse(moreValues.contains(new Offset(0, 1)));
    Assert.assertTrue(twoValues.contains(new Offset(1)));
    Assert.assertTrue(twoValues.contains(new Offset(1, 2)));
    Assert.assertTrue(oneValue.contains(new Offset(1)));
    Assert.assertTrue(new Offset(1, 5, 8, 12).contains(new Offset(3)));
    Assert.assertFalse(new Offset(1, 5, 8, 12).contains(new Offset(6)));
    Assert.assertTrue(new Offset(1, 5, 8, 12).contains(new Offset(3, 4, 9, 10)));
    Assert.assertFalse(new Offset(1, 5, 8, 12).contains(new Offset(3, 4, 6, 10)));
    Assert.assertTrue(new Offset(1, 5, 8, 12, 15, 20).contains(new Offset(3, 4, 16, 19)));
    Assert.assertFalse(new Offset(1, 5, 15, 20).contains(new Offset(3, 4, 6, 10, 16, 19)));
  }

  @Test
  public void testClone() {
    Assert.assertEquals(oneValue, oneValue.clone());
  }

  @Test
  public void testEqualsObject() {
    Assert.assertTrue(oneValue.equals(oneValue));
    Assert.assertFalse(oneValue.equals(new Object()));
    Assert.assertFalse(oneValue.equals(twoValues));
    Assert.assertTrue(twoValues.equals(new Offset(twoValues)));
    Assert.assertFalse(twoValues.equals(new Offset(1, 3)));
  }

  @Test
  public void testToString() {
    Assert.assertEquals("1", oneValue.toString());
    Assert.assertEquals("1:2", twoValues.toString());
    Assert.assertEquals("1:2;3:4", moreValues.toString());
  }
}
