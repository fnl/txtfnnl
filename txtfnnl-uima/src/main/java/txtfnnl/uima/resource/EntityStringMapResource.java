package txtfnnl.uima.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

/**
 * 
 * 
 * @author Florian Leitner
 */
public class EntityStringMapResource implements
        StringMapResource<List<Entity>>, SharedResourceObject {

	private Map<String, List<Entity>> entityMap = new HashMap<String, List<Entity>>();

	/**
	 * @see org.apache.uima.resource.SharedResourceObject#load(org.apache.uima.resource.DataResource)
	 */
	public void load(DataResource data) throws ResourceInitializationException {
		InputStream inStr = null;
		try {
			// open input stream to data
			inStr = data.getInputStream();
			// read each line
			BufferedReader reader = new BufferedReader(new InputStreamReader(
			    inStr));
			String line;

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0)
					continue;

				String[] items = line.split("\\t");
				Entity entity;
				try {
					entity = new Entity(items[1], items[2], items[3]);

					if (!entityMap.containsKey(items[0]))
						entityMap.put(items[0], new LinkedList<Entity>());
				} catch (IndexOutOfBoundsException e) {
					throw new ResourceInitializationException(
					    new RuntimeException("illegal line: '" + line +
					                         "' with " + items.length +
					                         " fields"));
				}
				entityMap.get(items[0]).add(entity);
			}
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		} finally {
			if (inStr != null) {
				try {
					inStr.close();
				} catch (IOException e) {}
			}
		}
	}

	public List<Entity> get(String key) {
		return entityMap.get(key);
	}

}
