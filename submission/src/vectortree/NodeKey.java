package vectortree;
import java.util.HashMap;

public class NodeKey {
	
	// must be an integer, because it must be able
	// to represent full range of values
	int _value;

	short _level;

	public NodeKey(int value, short level) {
		_value = value;
		_level = level;
	}
	
	public String toString() {
		return "(" + _value + "," + _level + ")";
	}
	
	public boolean equals(Object other) {
		if ((_value == ((NodeKey)other)._value) &&
			(_level == ((NodeKey)other)._level)) {

			return true;
		} else {
			return false;
		}
	}
	
	public int hashCode() {
		return _value;
	}

	private static void trace(String msg) {
		System.out.println(msg);
	}

	public static void main(String[] args) {
		NodeKey key11a	= new NodeKey(1, (short)1);
		NodeKey key11b	= new NodeKey(1, (short)1);
		NodeKey key12	= new NodeKey(1, (short)2);
		NodeKey key22	= new NodeKey(2, (short)1);

		HashMap<NodeKey, String> myMap = new HashMap<NodeKey, String>();
		
		myMap.put(key11a, "Key11");
		
		trace("key11a " + key11a + ": " + myMap.get(key11a));
		trace("key11b " + key11b + ": " + myMap.get(key11b));
		trace("key12  " + key12  + ": " + myMap.get(key12));
		trace("key22  " + key22  + ": " + myMap.get(key22));
	}
}
