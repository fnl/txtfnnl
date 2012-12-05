package txtfnnl.uima.utils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Useful functionality for handling recurrent patterns when working with UIMA
 * and UIMAfit.
 * 
 * @author Florian Leitner
 */
public class UIMAUtils {

	private UIMAUtils() {
		throw new AssertionError("non-instantiable");
	}

	/**
	 * Create a parameter array for constructing AE descriptors with UIMAfit.
	 * 
	 * Any value that is <code>null</code> or an empty string is not included
	 * in the parameter array.
	 * 
	 * @param params mapping of parameter names and values
	 * @return an Object array of those parameters
	 * @throws IOException 
	 */
	public static Object[] makeParameterArray(Map<String, Object> params) throws IOException {
		List<Object> list = new LinkedList<Object>();

		for (String key : params.keySet()) {
			Object value = params.get(key);

			if (value != null && !"".equals(value)) {
				list.add(key);
				
				if (value instanceof File)
					value = ((File) value).getCanonicalPath();
				
				list.add(value);
			}
		}

		return list.toArray(new Object[list.size()]);
	}
}
