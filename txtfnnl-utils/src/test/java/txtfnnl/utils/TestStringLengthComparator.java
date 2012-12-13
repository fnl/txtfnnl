package txtfnnl.utils;

import org.junit.Assert;
import org.junit.Test;

public class TestStringLengthComparator {
    StringLengthComparator c = StringLengthComparator.INSTANCE;

    @Test
    public void testCompare() {
        Assert.assertEquals(1, c.compare("short", "long string"));
        Assert.assertEquals(-1, c.compare("long string", "short"));
        Assert.assertEquals(0, c.compare("equal string", "equal string"));
    }
}
