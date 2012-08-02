package txtfnnl.uima.resource;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestEntity {

	static final Entity e = new Entity("type-val", "ns-val", "id-val");

	@Test
	public void testHashCode() {
		assertTrue(0 != e.hashCode());
	}

	@Test
	public void testGetType() {
		assertEquals("type-val", e.getType());
	}

	@Test
	public void testGetNamespace() {
		assertEquals("ns-val", e.getNamespace());
	}

	@Test
	public void testGetIdentifier() {
		assertEquals("id-val", e.getIdentifier());
	}

	@Test
	public void testToString() {
		assertEquals("type-val(ns-val:id-val)", e.toString());
	}

	@Test
	public void testEqualsObject() {
		assertTrue(e.equals(e));
		assertTrue(e.equals(new Entity("type-val", "ns-val", "id-val")));
		assertFalse(e.equals(new Entity("type-val", "ns-val", "other-val")));
		assertFalse(e.equals(new Object()));
	}

}
