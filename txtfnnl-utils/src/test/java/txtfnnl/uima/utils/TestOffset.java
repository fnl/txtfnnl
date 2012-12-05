package txtfnnl.uima.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import txtfnnl.uima.utils.Offset;

public class TestOffset {

	Offset oneValue = new Offset(1);
	Offset twoValues = new Offset(1, 2);
	Offset moreValues = new Offset(1, 2, 3, 4);

	@Test
	public void testHashCode() {
		Offset other = new Offset(oneValue.values());
		assertEquals(other.hashCode(), oneValue.hashCode());
		assertEquals(other.hashCode(), other.hashCode());
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
		assertEquals(oneValue, new Offset(oneValue));
	}

	@Test
	public void testStart() {
		assertEquals(1, oneValue.start());
		assertEquals(1, twoValues.start());
		assertEquals(1, moreValues.start());
	}

	@Test
	public void testEnd() {
		assertEquals(1, oneValue.end());
		assertEquals(2, twoValues.end());
		assertEquals(4, moreValues.end());
	}

	@Test
	public void testGet() {
		assertEquals(2, moreValues.get(1));
	}

	@Test
	public void testValues() {
		assertArrayEquals(new int[] { 1, 2 }, twoValues.values());
	}

	@Test
	public void testSize() {
		assertEquals(1, oneValue.size());
		assertEquals(2, twoValues.size());
		assertEquals(4, moreValues.size());
	}

	@Test
	public void testCompareTo() {
		assertEquals(-1, new Offset(1).compareTo(new Offset(2)));
		assertEquals(1, new Offset(2).compareTo(new Offset(1)));
		assertEquals(-1, new Offset(1, 3).compareTo(new Offset(1, 2)));
		assertEquals(1, new Offset(1, 2).compareTo(new Offset(1, 3)));
		assertEquals(-1,
		    new Offset(1, 2, 6, 9).compareTo(new Offset(1, 2, 5, 9)));
	}

	@Test
	public void testContainsOffset() {
		assertFalse(moreValues.contains(new Offset(0, 1)));
		assertTrue(twoValues.contains(new Offset(1)));
		assertTrue(twoValues.contains(new Offset(1, 2)));
		assertTrue(oneValue.contains(new Offset(1)));
		assertTrue(new Offset(1, 5, 8, 12).contains(new Offset(3)));
		assertFalse(new Offset(1, 5, 8, 12).contains(new Offset(6)));
		assertTrue(new Offset(1, 5, 8, 12).contains(new Offset(3, 4, 9, 10)));
		assertFalse(new Offset(1, 5, 8, 12).contains(new Offset(3, 4, 6, 10)));
		assertTrue(new Offset(1, 5, 8, 12, 15, 20).contains(new Offset(3, 4,
		    16, 19)));
		assertFalse(new Offset(1, 5, 15, 20).contains(new Offset(3, 4, 6, 10,
		    16, 19)));
	}

	@Test
	public void testClone() {
		assertEquals(oneValue, oneValue.clone());
	}

	@Test
	public void testEqualsObject() {
		assertTrue(oneValue.equals(oneValue));
		assertFalse(oneValue.equals(new Object()));
		assertFalse(oneValue.equals(twoValues));
		assertTrue(twoValues.equals(new Offset(twoValues)));
		assertFalse(twoValues.equals(new Offset(1,3)));
	}

	@Test
	public void testToString() {
		assertEquals("1", oneValue.toString());
		assertEquals("1:2", twoValues.toString());
		assertEquals("1:2,3:4", moreValues.toString());
	}

}
