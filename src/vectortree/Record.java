package vectortree;
import java.util.HashMap;


public class Record {

	private HashMap<String, Integer> _values;

	public Record() {
		_values = new HashMap<String, Integer>();
	}
	
	public void set(String key, int value) {
		_values.put(key, value);
	}
	
	public int get(String key) {
		return _values.get(key);
	}
	
	public String toString() {
		String output = "";
		for (String key: _values.keySet()) {
			output += key + ":" + _values.get(key) + " ";
		}
		
		return output;
	}
}
