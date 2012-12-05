package txtfnnl.uima.collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import org.easymock.EasyMock;
import org.uimafit.factory.CollectionReaderFactory;

import txtfnnl.uima.Views;

public class TestDirectoryReader {

	CollectionReader fileSystemReader;
	CAS baseCasMock;
	CAS rawCasMock;
	String basePath;

	@Before
	public void setUp() throws Exception {
		fileSystemReader = CollectionReaderFactory.createCollectionReader(
		    DirectoryReader.configure("src/test/resources"));
		baseCasMock = EasyMock.createMock(CAS.class);
		rawCasMock = EasyMock.createMock(CAS.class);
		basePath = "file:" + (new File(".")).getCanonicalPath() +
		           "/src/test/resources/";

		setGetNextMockExpectations(basePath + "test.html");
	}

	@After
	public void tearDown() throws Exception {
		fileSystemReader.close();
	}

	private void setGetNextMockExpectations(String path)
	        throws CASException {
		expect(baseCasMock.createView(Views.CONTENT_RAW.toString()))
		    .andReturn(rawCasMock);
		rawCasMock.setSofaDataURI(path, "text/html");
	}

	private void doGetNext(int times) {
		while (times-- > 0) {
			try {
				fileSystemReader.getNext(baseCasMock);
			} catch (CollectionException e) {
				e.printStackTrace();
				fail("collection exception: " + e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
				fail("IO exception: " + e.getMessage());
			}
		}
	}

	private void replayAll() {
		replay(baseCasMock);
		replay(rawCasMock);
	}

	private void verifyAll() {
		verify(baseCasMock);
		verify(rawCasMock);
	}

	@Test
	public void testRecursiveReading() throws IOException, UIMAException {
		try {
			fileSystemReader = CollectionReaderFactory.createCollectionReader(
			    DirectoryReader.configure("src/test/resources", true));
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
			fail("resource initialization exception: " + e.getMessage());
		}

		setGetNextMockExpectations(basePath + "test2.html");
		setGetNextMockExpectations(basePath + "test-subdir/test-sub.html");
		replayAll();
		doGetNext(3);
		verifyAll();
	}

	@Test
	public void testGetNext() throws CASException, CASRuntimeException,
	        IOException {
		replayAll();
		doGetNext(1);
		verifyAll();
	}

	@Test
	public void testHasNext() throws CollectionException, IOException,
	        CASException {
		assertTrue(fileSystemReader.hasNext());

		setGetNextMockExpectations(basePath + "test2.html");
		replayAll();
		doGetNext(2);

		assertFalse(fileSystemReader.hasNext());
	}

	@Test
	public void testGetProgress() throws CASException {
		Progress[] p = fileSystemReader.getProgress();

		assertEquals(1, p.length);
		assertEquals(0L, p[0].getCompleted());
		assertEquals(2L, p[0].getTotal());
		assertEquals("0 of 2 entities", p[0].toString());

		setGetNextMockExpectations(basePath + "test2.html");
		replayAll();
		doGetNext(2);
		p = fileSystemReader.getProgress();

		assertEquals(1, p.length);
		assertEquals(2L, p[0].getCompleted());
		assertEquals(2L, p[0].getTotal());
		assertEquals("2 of 2 entities", p[0].toString());

	}

}
