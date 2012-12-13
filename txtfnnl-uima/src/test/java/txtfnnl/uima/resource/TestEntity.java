package txtfnnl.uima.resource;

import org.junit.Assert;
import org.junit.Test;

public class TestEntity {
    static final Entity e = new Entity("type-val", "ns-val", "id-val");

    @Test
    public void testHashCode() {
        Assert.assertTrue(0 != e.hashCode());
    }

    @Test
    public void testGetType() {
        Assert.assertEquals("type-val", e.getType());
    }

    @Test
    public void testGetNamespace() {
        Assert.assertEquals("ns-val", e.getNamespace());
    }

    @Test
    public void testGetIdentifier() {
        Assert.assertEquals("id-val", e.getIdentifier());
    }

    @Test
    public void testToString() {
        Assert.assertEquals("type-val(ns-val:id-val)", e.toString());
    }

    @Test
    public void testEqualsObject() {
        Assert.assertTrue(e.equals(e));
        Assert.assertTrue(e.equals(new Entity("type-val", "ns-val", "id-val")));
        Assert.assertFalse(e.equals(new Entity("type-val", "ns-val", "other-val")));
        Assert.assertFalse(e.equals(new Object()));
    }
}
