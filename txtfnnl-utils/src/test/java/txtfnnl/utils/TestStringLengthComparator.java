package txtfnnl.utils;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestStringLengthComparator {

	StringLengthComparator c = StringLengthComparator.INSTANCE;
	
	@Test
	public void testCompare() {
		assertEquals(1, c.compare("short", "long string"));
		assertEquals(-1, c.compare("long string", "short"));
		assertEquals(0, c.compare("equal string", "equal string"));
	}
}
