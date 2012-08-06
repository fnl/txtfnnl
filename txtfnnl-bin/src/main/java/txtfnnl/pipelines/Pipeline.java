package txtfnnl.pipelines;

import java.io.IOException;

import org.apache.uima.UIMAException;


public interface Pipeline {
	void run() throws UIMAException, IOException;
}
