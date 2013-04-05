package txtfnnl.utils;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.googlecode.concurrenttrees.common.KeyValuePair;

public class TestConcurrentPatriciaTree {
  PatriciaTree<String> trie;
  int hits;

  @Before
  public void setUp() {
    trie = new ConcurrentPatriciaTree<String>();
    trie.put("key", "value1");
    trie.put("keykey", "value2");
    trie.put("other", "value2");
    hits = 0;
  }

  @Test
  public final void testScanForKeyValuePairsAtStartOf() {
    for (KeyValuePair<String> kvp : trie.scanForKeyValuePairsAtStartOf("keykeyother")) {
      if (kvp.getKey().equals("key")) assertEquals("value1", kvp.getValue());
      else if (kvp.getKey().equals("keykey")) assertEquals("value2", kvp.getValue());
      else fail("unexpected key " + kvp.getKey());
      hits++;
    }
    assertEquals(2, hits);
  }

  @Test
  public final void testScanForKeysAtStartOf() {
    for (CharSequence key : trie.scanForKeysAtStartOf("keykeyother")) {
      if (key.equals("key")) hits++;
      else if (key.equals("keykey")) hits++;
      else fail("unexpected key " + key);
    }
    assertEquals(2, hits);
  }

  @Test
  public final void testScanForValuesAtStartOf() {
    for (String value : trie.scanForValuesAtStartOf("keykeyother")) {
      if (value.equals("value1")) hits++;
      else if (value.equals("value2")) hits++;
      else fail("unexpected value " + value);
    }
    assertEquals(2, hits);
  }
}
