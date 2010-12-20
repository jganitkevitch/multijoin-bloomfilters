package vectortree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;


public class VectorTree {

	// static tree properties
	private static short num_bits = 4;
	private static short key_size = Integer.SIZE;
	private static short height = (short)(key_size / num_bits);

	private VectorTreeNode _root;
	
	private HashMap<NodeKey, VectorTreeNode> _nodes;
	private HashMap<Integer, ArrayList<Record>> _registrants;
	
	private static ArrayList<Short> value_lookup;
	private static HashMap<Integer, ArrayList<Short>> survivors;
	private static HashMap<Short, ArrayList<Short>> survivors2;
	
	public VectorTree() {
		_nodes = new HashMap<NodeKey, VectorTreeNode>();
		_registrants = new HashMap<Integer, ArrayList<Record>>();

		_root = getNode(new NodeKey(0, (short)0));
		
		init();
	}
	
	private void init() {
		if (value_lookup == null) {
			initValueLookup();
		}
		
		if (survivors == null) {
			initSurvivors();
		}
	}
	
	private static void initValueLookup() {
		value_lookup = new ArrayList<Short>();
		
		int max = ((Double)Math.pow(2, num_bits)).intValue();
		for (int i = 0; i < max; i++) {
			value_lookup.add(i, ((Integer)(1 << i)).shortValue());
		}
	}
	
	private static void initSurvivors() {
		survivors = new HashMap<Integer, ArrayList<Short>>();
		survivors2 = new HashMap<Short, ArrayList<Short>>();
		
		// bit vectors must represent all 2^{num bits per depth} possibilities
		int size_of_bitvector = (int)Math.pow(2, num_bits);
		
		// survivors represent all possible combinations of bit vectors
		int size_of_survivors = (int)Math.pow(2, size_of_bitvector);
		
		for (Integer combination = 0; combination < size_of_survivors; combination++) {
			ArrayList<Short> offsets = new ArrayList<Short>();
			
			String bitstring = Integer.toBinaryString(combination);
			
			for (Integer index = bitstring.length() - 1; index >= 0; index--) {
				if (bitstring.charAt(index) == '1') {
					offsets.add((short)(bitstring.length() - index - 1));
				}
			}
						
			// map list to its combination
			survivors.put(combination, offsets);
			survivors2.put(combination.shortValue(), offsets);
		}
	}
	
	public static short lookupValue(byte i) {
		// has the array already been created?
		if (value_lookup == null) {
			initValueLookup();
		}
		
		return value_lookup.get(i);
	}
		
	public void insert(int key, Record record) {
		
		insertInBucket(key, record);

		// we've already inserted in bucket (height - 1), so begin at (height - 2)
		short depth = (short)(height - 2);
		
		for (short shift = num_bits; shift < Integer.SIZE; shift += num_bits) {

			// extract subkey and bit-sequence
			
			// shifting occurs in two stages, since shift=32 is ignored
			int subkey = (key >> shift) >> num_bits;
			byte vector = (byte)((byte)(key >> shift) & 0x0f);
					
			// extract the node for this bit-sequence
			VectorTreeNode node = getNode(new NodeKey(subkey, depth));
			
			// perform bitwise OR on existing vector and new value
			short new_vector = (short)(node.getVector() | value_lookup.get(vector));
			
			// TODO: Implement difference check
			// has there been a change?
			// if (new_vector == node.getVector()) { // if so... } else { // otherwise... }

			// update vector
			node.setVector(new_vector);
			
			depth--;
		}
	}
	
	private void insertInBucket(int key, Record record) {

		// perform insertion at bucket depth
		int subkey = (key >> num_bits);
		byte vector = (byte)(key & 0x0f);

		// extract the node for this bit-sequence
		VectorTreeNode node = getNode(new NodeKey(subkey, (short)(height - 1)));

		// perform bitwise OR on existing vector and new value
		short new_vector = (short)(node.getVector() | value_lookup.get(vector));

		// update vector
		node.setVector(new_vector);

		// extract all records registered for this key
		ArrayList<Record> registrants = _registrants.get(key);
		
		// if no records are registered, create a list
		if (registrants == null) {
			registrants = new ArrayList<Record>();			
		}

		// add record
		registrants.add(record);
	}
	
	public void remove(int key, Record record) {

		// did this removal affect any above layers?
		if (removeFromBucket(key, record) == false) {
			return;
		}
		
		for (short depth = (short)(height - 2); depth > 0; depth--) {
			
			short shift = (short)(depth * num_bits);
			// extract subkey and bit-sequence
			int subkey = (key >> shift);
			byte vector = (byte)((byte)(key >> shift) & 0x0f);
					
			// extract the node for this bit-sequence
			VectorTreeNode node = getNode(new NodeKey(subkey, depth));
			
			// perform bitwise OR on existing vector and new value
			short new_vector = (short)(node.getVector() | value_lookup.get(vector));
			
			// TODO: Implement difference check
			// has there been a change?
			// if (new_vector == node.getVector()) { // if so... } else { // otherwise... }
			
			// update vector
			node.setVector(new_vector);
			
			depth++;
		}
	}
	
	private Boolean removeFromBucket(int key, Record record) {

		// perform removal at bucket depth

		// extract all records registered for this key
		ArrayList<Record> registrants = _registrants.get(key);

		// if no records are registered, create a list
		if (registrants == null) {
			// TODO: Throw error
			return false;
		}

		// remove record and check to see whether a deletion occurred
		if (registrants.remove(record) == false) {
			// if not, indicate no modification
			return false; 
		}
		
		// a deletion occurred, but are any records still registered?
		if (registrants.isEmpty() == false) {
			// if so, indicate no modification
			return false;
		}
		
		// Cut 32-bit integer down to 8-bits and set the most
		// significant 4-bits to 0
		byte vector = (byte)(key & 0x0f);

		// extract the node for this bit-sequence
		VectorTreeNode node = getNode(new NodeKey(key, (short)(height - 1)));
		
		// perform bitwise AND on existing vector and inverted new value
		short new_vector = (short)(node.getVector() & ~value_lookup.get(vector));
		
		// update vector
		node.setVector(new_vector);
		
		return true;
	}
	
	private VectorTreeNode getNode(NodeKey node_key) {

		// extract the node for this bit-sequence
		VectorTreeNode node = _nodes.get(node_key);
		
		// have we seen this key yet?
		if (node == null) {
			
			// if not, add it
			node = new VectorTreeNode();
			_nodes.put(node_key, node);
		}
		
		return node;
	}
	
	protected static ArrayList<Short> getSurvivors(int key) {
		return survivors.get(key);
	}

	protected static ArrayList<Short> getSurvivors(short key) {
		return survivors2.get(key);
	}

	public static VectorTreeIterator intersect(VectorTree vt1, VectorTree vt2) {
	
		ArrayList<HashMap<Integer, ArrayList<Record>>> all_registrants = new ArrayList<HashMap<Integer,ArrayList<Record>>>();
		
		// extract all records registered for this intersection
		all_registrants.add(vt1._registrants);
		all_registrants.add(vt2._registrants);
		
		VectorTreeIterator iterator = new VectorTreeIterator(all_registrants, num_bits);

		// begin intersection at the root node
		intersectNode(vt1, vt2, 0, (short)0, iterator);
		
		return iterator;

	}
	public static VectorTreeIterator intersect(ArrayList<VectorTree> trees) {

		ArrayList<HashMap<Integer, ArrayList<Record>>> all_registrants = new ArrayList<HashMap<Integer,ArrayList<Record>>>();
		
		// extract all records registered for this intersection
		for (VectorTree tree : trees) {
			all_registrants.add(tree._registrants);
		}
		
		VectorTreeIterator iterator = new VectorTreeIterator(all_registrants, num_bits);

		// begin intersection at the root node
		intersectNode(trees, 0, (short)0, iterator);
		
		return iterator;
	}
	
	private static void intersectNode(
			VectorTree vt1,
			VectorTree vt2,
			int prefix,
			short depth,
			VectorTreeIterator iterator) {

		// are we at leaf level?
		if (depth == height - 1) {
			// if so, perform actual value extraction
			intersectNodeBucket(vt1, vt2, prefix, iterator);
			return;
		}

		// get bitvectors of both nodes
		NodeKey node_key = new NodeKey(prefix, depth);

		short vector1 = vt1.getNode(node_key).getVector();
		short vector2 = vt2.getNode(node_key).getVector();
		
		short vector_intersection = (short)((int)vector1 & (int)vector2);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		for (short offset : offsets) {
			int new_key = (prefix << num_bits) + offset;
			
			intersectNode(vt1, vt2, new_key, (short)(depth + 1), iterator);
		}
	}

	private static void intersectNode(
			ArrayList<VectorTree> trees,
			int prefix,
			short depth,
			VectorTreeIterator iterator) {

		// are we at leaf level?
		if (depth == height - 1) {
			// if so, perform actual value extraction
			intersectNodeBucket(trees, prefix, iterator);
			return;
		}

		short vector_intersection = getIntersection(new NodeKey(prefix, depth), trees);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		for (short offset : offsets) {
			int new_key = (prefix << num_bits) + offset;
			
			intersectNode(trees, new_key, (short)(depth + 1), iterator);
		}
	}

	private static short getIntersection(NodeKey node_key,
			ArrayList<VectorTree> trees) {

		int intersection = ~0;
		for (VectorTree tree : trees) {
			intersection = intersection & tree.getNode(node_key).getVector();
		}
		
		return (short)intersection;
	}

	private static void intersectNodeBucket(
			VectorTree vt1,
			VectorTree vt2,
			int prefix,
			VectorTreeIterator iterator) {

		// get bitvectors of both nodes
		NodeKey node_key = new NodeKey(prefix, (short)(height - 1));

		short vector1 = vt1.getNode(node_key).getVector();
		short vector2 = vt2.getNode(node_key).getVector();

		short vector_intersection = (short)((int)vector1 & (int)vector2);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		VectorTreeIterator.Matches matches = new VectorTreeIterator.Matches(
				prefix, offsets);
		
		// add matches to iterator
		iterator.enqueue(matches);
	}

	private static void intersectNodeBucket(
			ArrayList<VectorTree> trees,
			int prefix,
			VectorTreeIterator iterator) {

		// get bitvectors of both nodes
		NodeKey node_key = new NodeKey(prefix, (short)(height - 1));

		short vector_intersection = getIntersection(node_key, trees);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		VectorTreeIterator.Matches matches = new VectorTreeIterator.Matches(
				prefix, offsets);
		
		if (offsets.size() == 0) {
			return;
		}
		
		// add matches to iterator
		iterator.enqueue(matches);
	}

	
	private static void trace(String msg) {
		System.out.println(msg);
	}


	public static void shiftDemo() {
		// hard-coded bit shift
		
		Integer t = 0 * ((Double)Math.pow(2, 12)).shortValue() +
					1 * ((Double)Math.pow(2, 8)).shortValue() +
					4 * ((Double)Math.pow(2, 4)).shortValue() +
					11 * ((Double)Math.pow(2, 0)).shortValue();

		trace("t " + t);
		trace("int   t " + Integer.SIZE);
		trace("short t " + Short.SIZE);
		trace("12: " + ((byte)(t >> 12) & 0x0f));
		trace(" 8: " + ((byte)(t >>  8) & 0x0f));
		trace(" 4: " + ((byte)(t >>  4) & 0x0f));
		trace(" 0: " + ((byte)(t >>  0) & 0x0f));
		
		trace("");
		short shift = 0;
		while (shift < t.SIZE) {
			trace(shift + ": " + ((byte)(t >> shift) & 0x0f));
			
			shift += num_bits;
		}

		trace("");
		shift = 0;
		while (shift < t.SIZE) {
			trace(shift + ": " + (t >> shift));
			
			shift += num_bits;
		}


		int a = 20;
		trace(a + " " + (a << 3) + " " + (a >> 3));
		trace(a + " " + (a << 31) + " " + (a >> 31));
		trace(a + " " + (a << 32) + " " + (a >> 32));
		trace(a + " " + (a << 33) + " " + (a >> 33));
	}
	
	private static void populateList(ArrayList<Integer> values,
			int[] midpoints,
			int[] densities,
			int[] widths) {

		for (int index = 0; index < midpoints.length; index++) {
			for (int count = 0; count < densities[index]; count++) {
				Integer new_value = midpoints[index] + ((Double)(Math.random() * widths[index])).intValue();
				values.add(new_value);
			}
		}
	}

	public static void main(String[] args) throws IOException {

		VectorTree vt1 = new VectorTree();
		VectorTree vt2 = new VectorTree();
		VectorTree vt3 = new VectorTree();

		LinkedList<Integer> vt1list = new LinkedList<Integer>();
		LinkedList<Integer> vt2list = new LinkedList<Integer>();
		LinkedList<Integer> vt3list = new LinkedList<Integer>();

		System.out.println("inserting...");

		int cardinality = Integer.MAX_VALUE / 1000;
		trace("cardinality " + cardinality);
		for (int i = 0; i < cardinality; i++) {
			Record record1 = new Record();
			Record record2 = new Record();
			Record record3 = new Record();

			record1.set("1", ((Double)(Math.random() * Integer.MAX_VALUE)).intValue());
			record2.set("1", ((Double)(Math.random() * Integer.MAX_VALUE)).intValue());
			record3.set("1", ((Double)(Math.random() * Integer.MAX_VALUE)).intValue());

			vt1.insert(record1.get("1"), record1);
			vt2.insert(record2.get("1"), record2);
			vt3.insert(record3.get("1"), record3);
		}

		ArrayList<VectorTree> trees = new ArrayList<VectorTree>();
		trees.add(vt1);
		trees.add(vt2);
		trees.add(vt3);
		System.out.println("done.");

		System.out.println("intersecting...");
		VectorTreeIterator iterator = VectorTree.intersect(trees);
		System.out.println("done.");
		
		System.out.println("trial");
		int counter = 0;
		while(iterator.hasNext()) {
			System.out.println("    " + iterator.next());
			counter++;
		}
		
		System.out.println(counter + " total");
		System.out.println("\npreparing gold...");
		
		HashSet<Integer> s1 = new HashSet<Integer>(vt1list);		
		s1.retainAll(vt2list);
		s1.retainAll(vt3list);

		System.out.println(s1.size() + " gold");
	}
}
