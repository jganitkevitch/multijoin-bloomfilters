package vectortree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class VectorTree {

	public static int max_bloom_level = 12;
	public static int bloom_size_offset = 2;
	
	private static int num_sets = 4;
	private static int set_size = 1000000;
	private static double set_density = 0.2;
	private static double set_overlap = 0.2;
	private static int repetitions = 10;
	
	private static int bloom_hits = 0;
	
	// static tree properties
	private static short bits_per_level = 4;
	private static short bits_per_key = Integer.SIZE;
	private static short height = (short)(bits_per_key / bits_per_level);
	private static short log_height = ((Double)(Math.log(height) / Math.log(2))).shortValue();

	private static ArrayList<Short> value_lookup;
	private static HashMap<Integer, ArrayList<Short>> survivors_lookup;
	private static HashMap<Short, ArrayList<Short>> survivors_lookup_short;

	// members	
	private VectorTreeNode _root;
	
	private HashMap<Long, VectorTreeNode> _nodes;
	private HashMap<Integer, ArrayList<Record>> _registrants;
	
	
	public VectorTree() {
		_nodes = new HashMap<Long, VectorTreeNode>();
		_registrants = new HashMap<Integer, ArrayList<Record>>();

		_root = getNode(0, 0);
		
		init();
	}
	
	private void init() {
		if (value_lookup == null) {
			initValueLookup();
		}
		
		if (survivors_lookup == null) {
			initSurvivorsLookup();
		}
	}
	
	private static void initValueLookup() {
		value_lookup = new ArrayList<Short>();
		
		int max = ((Double)Math.pow(2, bits_per_level)).intValue();
		for (int i = 0; i < max; i++) {
			value_lookup.add(i, ((Integer)(1 << i)).shortValue());
		}
	}
	
	private static void initSurvivorsLookup() {
		survivors_lookup = new HashMap<Integer, ArrayList<Short>>();
		survivors_lookup_short = new HashMap<Short, ArrayList<Short>>();
		
		// bit vectors must represent all 2^{num bits per level} possibilities
		int size_of_bitvector = (int)Math.pow(2, bits_per_level);
		
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
			survivors_lookup.put(combination, offsets);
			survivors_lookup_short.put(combination.shortValue(), offsets);
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
		
		insertAtLeafLevel(key, record);

		// we've already inserted at leaf level, (height - 1), so begin at (height - 2)
		short depth = (short)(height - 2);
		
		// records whether the previous vector was modified
		boolean change_occurred = false;
		
//		trace("INSERT key " + key);
		
		// now we'll work our way up the tree, to the root
		for (short shift = bits_per_level; shift < Integer.SIZE; shift += bits_per_level) {

			// extract subkey and bit-sequence
			
			// shifting occurs in two stages, since shift=32 is ignored
			int subkey = (key >> shift) >> bits_per_level;
			byte vector = (byte)((byte)(key >> shift) & 0x0f);

//			trace("   depth: " + ((bits_per_key - shift) / bits_per_level) + "(" + depth + "), subkey " + subkey + ", " + vector);

			// extract the node for this bit-sequence
			VectorTreeNode node = getNode(subkey, depth);
			
			// perform bitwise OR on existing vector and new value
			short new_vector = (short)(node.getVector() | value_lookup.get(vector));
			
			// increment leaves and ...
			node.increaseLeavesBy(1);

			// if a change took place, increment subnodes
			if (change_occurred) {
				node.increaseSubnodesBy(1);
			}
			
			// has there been a change?
			if (new_vector != node.getVector()) {
				// if so...
				change_occurred = true;
			} else {
				// otherwise...
				change_occurred = false;
			}

			// update vector
			node.setVector(new_vector);
			
			// update bloom filter
			node.getBloomFilter().add(key);
			
			depth--;
		}
	}
	
	private void insertAtLeafLevel(int key, Record record) {

		// perform insertion at leaf level
		int subkey = (key >> bits_per_level);
		int vector = key & 0x0f;

		// extract the node for this bit-sequence
		VectorTreeNode node = getNode(subkey, height - 1);

		// perform bitwise OR on existing vector and new value
		short new_vector = (short)(node.getVector() | value_lookup.get(vector));

		// is this the first record registered to this key?
		if (new_vector != node.getVector()) {
			// if so, increment leaves
			node.increaseLeavesBy(1);
		}

		// update vector
		node.setVector(new_vector);

		// extract all records registered for this key
		ArrayList<Record> registrants = _registrants.get(key);
		
		// if no records are registered, create a list
		if (registrants == null) {
			registrants = new ArrayList<Record>();
			_registrants.put(key, registrants);
		}

		// add record
		registrants.add(record);
	}

	public void remove(int key, Record record) {

		// did this removal affect any above layers?
		if (removeAtLeafLevel(key, record) == false) {
			return;
		}
		
		// move up the tree, stopping at the root
		for (short depth = (short)(height - 2); depth >= 0; depth--) {

			int current_shift = (height - depth) * bits_per_level;
			int current_key = key >> current_shift;

			int previous_shift = (height - depth - 1) * bits_per_level;
			int previous_key = key >> previous_shift;
			byte previous_vector = (byte)(previous_key & 0x0f);

			VectorTreeNode current_node = getExistingNode(current_key, depth);
			VectorTreeNode previous_node = getExistingNode(previous_key, depth + 1);

			// was previous node emptied?
			if (previous_node.getVector() == 0) {

				// if so, remove node
				_nodes.remove(previous_node);
				
				// and update current vector
				short updated_vector = (short)(current_node.getVector() & ~value_lookup.get(previous_vector));
				
				// update leaves and subnodes
				current_node.increaseLeavesBy(-1);
				if (current_node.getVector() != updated_vector) {
					current_node.increaseSubnodesBy(-1);
				}

				current_node.setVector(updated_vector);
			}
		}
	}
	
	/*
	 * removes the specified key from the leaf-level of the tree
	 * 
	 * @return Short - the new vector, -1 on failure to remove
	 */
	private boolean removeAtLeafLevel(int key, Record record) {

		// perform removal at leaf level
		int subkey = (key >> bits_per_level);
		int vector = key & 0x0f;

		// extract all records registered for this key
		ArrayList<Record> registrants = _registrants.get(key);

		// are any records registered?
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
		
		// at this point, a registrant has been successfully deleted,
		// and the list of remaining registrants has become empty
		
		// extract the node for this bit-sequence
		VectorTreeNode node = getExistingNode(subkey, height - 1);
		
		// perform bitwise AND on existing vector and inverted new value
		short new_vector = (short)(node.getVector() & ~value_lookup.get(vector));

		// did a deletion occur?
		if (new_vector != node.getVector()) {
			node.increaseLeavesBy(-1);
		}

		// update vector
		node.setVector(new_vector);
		
		return true;
	}

	private VectorTreeNode getExistingNode(int value, int level) {
		long node_id = (value << log_height) + level;
		
		// extract the node for this bit-sequence
		VectorTreeNode node = _nodes.get(node_id);

		// have we seen this key yet?
		if (node == null) {
			return null;
		}
		
		return node;
	}

	private VectorTreeNode getNode(int value, int depth) {

		long node_id = (value << log_height) + depth;
		
		// extract the node for this bit-sequence
		VectorTreeNode node = _nodes.get(node_id);

		// have we seen this key yet?
		if (node == null) {
			
			// if not, add it
			node = new VectorTreeNode(depth, bits_per_level, bits_per_key);
			_nodes.put(node_id, node);
		}
		
		return node;
	}


	private void decommissionNode(int value, int depth) {
		long node_id = (value << log_height) + depth;
		
		// remove node
		_nodes.remove(node_id);
	}
		
	protected static ArrayList<Short> getSurvivors(int key) {
		return survivors_lookup.get(key);
	}

	protected static ArrayList<Short> getSurvivors(short key) {
		return survivors_lookup_short.get(key);
	}

	public static VectorTreeIterator intersect(ArrayList<VectorTree> trees, boolean use_bloom_filter) {

		ArrayList<HashMap<Integer, ArrayList<Record>>> all_registrants =
				new ArrayList<HashMap<Integer,ArrayList<Record>>>();
		
		// extract all records registered for this intersection
		for (VectorTree tree : trees) {
			all_registrants.add(tree._registrants);
		}
		
		VectorTreeIterator iterator = new VectorTreeIterator(all_registrants, bits_per_level);

		// begin intersection at the root node
		if (use_bloom_filter) {
			intersectNodeWithBloomFilter(trees, 0, (short)0, iterator);
		} else {
			intersectNode(trees, 0, (short)0, iterator);
		}
		
		return iterator;
	}

	private static void intersectNode(
			ArrayList<VectorTree> trees,
			int prefix,
			short depth,
			VectorTreeIterator iterator) {

		// are we at leaf level?
		if (depth == height - 1) {
			// if so, perform actual value extraction
			intersectNodeAtLeafLevel(trees, prefix, iterator);
			return;
		}

		short vector_intersection = getIntersection(prefix, depth, trees);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		for (short offset : offsets) {
			int new_key = (prefix << bits_per_level) + offset;
			
			intersectNode(trees, new_key, (short)(depth + 1), iterator);
		}
	}

	private static void intersectNodeWithBloomFilter(
			ArrayList<VectorTree> trees,
			int prefix,
			short depth,
			VectorTreeIterator iterator) {

		// are we at leaf level?
		if (depth == height - 1) {
			// if so, perform actual value extraction
			intersectNodeAtLeafLevel(trees, prefix, iterator);
			return;
		}

		// before we perform the intersection over vectors, we intersect over
		// bloom filters, allowing ourselves to stop here if the intersection
		// reports no survivors
		
		// are there any survivors?
		int survivors = getBloomFilterSurvivors(prefix, depth, trees);
		if (survivors == 0) {
//			trace("***** Bloom Filter says 'no matches' at depth " + depth + " *****");
			// if not, stop descending
			bloom_hits++;
			return;
		} else {
//			trace("Depth: " + depth + " Survivors: " + survivors);
		}

		short vector_intersection = getIntersection(prefix, depth, trees);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		for (short offset : offsets) {
			int new_key = (prefix << bits_per_level) + offset;

			intersectNodeWithBloomFilter(trees, new_key, (short)(depth + 1), iterator);
		}
	}

	private static short getIntersection(
			int key,
			int depth,
			ArrayList<VectorTree> trees) {

		int intersection = ~0;
		for (VectorTree tree : trees) {
			intersection = intersection & tree.getNode(key, depth).getVector();
		}
		
		return (short)intersection;
	}

	private static int getBloomFilterSurvivors(
			int key,
			int depth,
			ArrayList<VectorTree> trees) {
		
		ArrayList<BloomFilter> bloom_filters = new ArrayList<BloomFilter>();

		for (VectorTree tree : trees) {
			bloom_filters.add(tree.getNode(key, depth).getBloomFilter());
		}
		
//		System.out.println("intersecting " + bloom_filters.size() + " bloom filters at depth " + depth);
		
		return BloomFilter.intersectMutiway(bloom_filters);
	}
	
	private static void intersectNodeAtLeafLevel(
			ArrayList<VectorTree> trees,
			int prefix,
			VectorTreeIterator iterator) {

		// get bitvectors of both nodes
		short vector_intersection = getIntersection(prefix, height - 1, trees);
		
		ArrayList<Short> offsets = getSurvivors(vector_intersection);
		
		VectorTreeIterator.Matches matches =
				new VectorTreeIterator.Matches(prefix, offsets);
		
		// were results produced?
		if (offsets.size() > 0) {
			// add matches to iterator
			iterator.enqueue(matches);
		}
	}

	
	private static void trace(String msg) {
		System.out.println(msg);
	}

	private static void test1() {
		
		int num_trees = 3;

		ArrayList<VectorTree> trees = new ArrayList<VectorTree>();
		for (int i = 0; i < num_trees; i++) {
			trees.add(new VectorTree());
		}

		// insertion

		System.out.println("inserting...");

		int cardinality = Integer.MAX_VALUE / 10000;

		for (int i = 0; i < cardinality; i++) {
			for (VectorTree tree : trees) {
				Record record = new Record();
				record.set("1", ((Double)(Math.random() * Integer.MAX_VALUE)).intValue());

				tree.insert(record.get("1"), record);
			}
		}

		System.out.println("done.");

		
		// intersection 

		System.out.println("intersecting...");
		VectorTreeIterator iterator1 = VectorTree.intersect(trees, false);
		System.out.println("done.");

		System.out.println("intersecting (with bloom filter)...");
		VectorTreeIterator iterator2 = VectorTree.intersect(trees, true);
		System.out.println("done.");

		int counter = 0;

		counter = 0;
		System.out.println("trial 1");
		while(iterator1.hasNext()) {
			System.out.println("    " + iterator1.next());
			counter++;
		}

		System.out.println("trial 2");
		counter = 0;
		while(iterator2.hasNext()) {
			System.out.println("    " + iterator2.next());
			counter++;
		}
		
		System.out.println(counter + " total");
	}

	public static void test2() {
		
		VectorTree vt1 = new VectorTree();
		VectorTree vt2 = new VectorTree();
		VectorTree vt3 = new VectorTree();

		LinkedList<Integer> vt1list = new LinkedList<Integer>();
		LinkedList<Integer> vt2list = new LinkedList<Integer>();
		LinkedList<Integer> vt3list = new LinkedList<Integer>();

		System.out.println("inserting...");

		int cardinality = 31;

		Record record1 = new Record();
		Record record2;
		Record record3;
		int i;
		for (i = 0; i <= cardinality; i++) {
			record1 = new Record();
			record2 = new Record();
			record3 = new Record();

			int value1 = i;//((Double)(Math.random() * cardinality)).intValue();
			int value2 = i;//((Double)(Math.random() * cardinality)).intValue();
			int value3 = i;//((Double)(Math.random() * cardinality)).intValue();
//			trace(i + " 1: " + value1);
//			trace(i + " 2: " + value2);
//			trace(i + " 3: " + value3);
//			trace("---------------");

			record1.set("1", value1);
			record2.set("1", value2);
			record3.set("1", value1);

			vt1list.add(record1.get("1"));
			vt2list.add(record2.get("1"));
			vt3list.add(record3.get("1"));

			vt1.insert(record1.get("1"), record1);
			vt2.insert(record2.get("1"), record2);
			vt3.insert(record3.get("1"), record3);
		}
		
		int v1 = 17185;
		int v2 = 124076833;
		int v0 = v2;
		
		Record deletion_record = new Record();
		vt1.insert(v0, deletion_record);
		vt1.remove(v0, deletion_record);
		
		trace("leaves/subnodes: " + vt1._root.getLeaves() + "/" + vt1._root.getSubnodes());

		ArrayList<VectorTree> trees = new ArrayList<VectorTree>();
		trees.add(vt1);
		trees.add(vt2);
		trees.add(vt3);
		System.out.println("done.");

		System.out.println("intersecting...");
		VectorTreeIterator iterator1 = VectorTree.intersect(trees, false);
		System.out.println("done.");

		System.out.println("intersecting...");
		VectorTreeIterator iterator2 = VectorTree.intersect(trees, true);
		System.out.println("done.");

		int counter = 0;

		counter = 0;
		System.out.println("trial 1");
		while(iterator1.hasNext()) {
			Integer q1 = iterator1.next();
			// System.out.println("    " + q1);
			counter++;
		}
		System.out.println(counter + " total");

		System.out.println("trial 2");
		counter = 0;
		while(iterator2.hasNext()) {
			Integer q2 = iterator2.next();
			// System.out.println("    " + q2);
			counter++;
		}

		System.out.println(counter + " total");
		System.out.println("\npreparing gold...");
		
		HashSet<Integer> s1 = new HashSet<Integer>(vt1list);		
		s1.retainAll(vt2list);
		s1.retainAll(vt3list);

		System.out.println(s1.size() + " gold");
	}

	public static void test4() {
		VectorTree vt1 = new VectorTree();
		VectorTree vt2 = new VectorTree();
		VectorTree vt3 = new VectorTree();

		vt1.insert(1, new Record());
		vt1.insert(17, new Record());
		vt1.insert(33, new Record());
		vt1.insert(39, new Record());
		vt1.insert(55, new Record());
//		vt1.insert(600, new Record());

		vt2.insert(2, new Record());
		vt2.insert(18, new Record());
		vt2.insert(34, new Record());
		vt2.insert(40, new Record());
		vt2.insert(56, new Record());
		vt2.insert(600, new Record());

		vt3.insert(4, new Record());
		vt3.insert(7, new Record());
		vt3.insert(9, new Record());
		vt3.insert(13, new Record());
		vt3.insert(17, new Record());
		vt3.insert(600, new Record());


		ArrayList<VectorTree> trees2 = new ArrayList<VectorTree>();
//		ArrayList<VectorTree> trees3 = new ArrayList<VectorTree>();

		trees2.add(vt1);
		trees2.add(vt2);

//		trees3.add(vt1);
//		trees3.add(vt2);
//		trees3.add(vt3);

		VectorTreeIterator iterator0 = VectorTree.intersect(trees2, false);
		VectorTreeIterator iterator1 = VectorTree.intersect(trees2, true);

		int counter = 0;

		counter = 0;
		System.out.println("trial 1");
		while(iterator1.hasNext()) {
			Integer q1 = iterator1.next();
			trace(" . . t1:" + q1);
			counter++;
		}
		System.out.println(counter + " total");
/*
		counter = 0;
		System.out.println("trial 2");
		while(iterator2.hasNext()) {
			Integer q2 = iterator2.next();
			trace(" . . t2:" + q2);
			counter++;
		}
		System.out.println(counter + " total");
*/		
		/*
		System.out.println("\npreparing gold...");
		
		HashSet<Integer> s1 = new HashSet<Integer>(vt1list);
		s1.retainAll(vt2list);
		s1.retainAll(vt3list);

		System.out.println(s1.size() + " gold");
		*/
	}

	public static void test3() {
		
		VectorTree vt1 = new VectorTree();
		VectorTree vt2 = new VectorTree();
		VectorTree vt3 = new VectorTree();

		LinkedList<Integer> vt1list = new LinkedList<Integer>();
		LinkedList<Integer> vt2list = new LinkedList<Integer>();
		LinkedList<Integer> vt3list = new LinkedList<Integer>();

		System.out.println("inserting...");

		
		int[] l1 = {1, 17, 35, 49, 51};
		int[] l2 = {1, 18, 36, 49, 51};
		int[] l3 = {1, 19, 37, 49, 51};

		int cardinality = l1.length;

		Record record1;
		Record record2;
		Record record3;
		
		for (int i = 0; i < cardinality; i++) {
			record1 = new Record();
			record2 = new Record();
			record3 = new Record();

			int value1 = l1[i];//((Double)(Math.random() * cardinality)).intValue();
			int value2 = l2[i];//((Double)(Math.random() * cardinality)).intValue();
			int value3 = l3[i];//((Double)(Math.random() * cardinality)).intValue();


			record1.set(Integer.toString(value1), value1);
			record2.set(Integer.toString(value2), value2);
			record3.set(Integer.toString(value3), value3);

			vt1list.add(record1.get(Integer.toString(value1)));
			vt2list.add(record2.get(Integer.toString(value2)));
			vt3list.add(record3.get(Integer.toString(value3)));

			vt1.insert(record1.get(Integer.toString(value1)), record1);
			vt2.insert(record2.get(Integer.toString(value2)), record2);
			vt3.insert(record3.get(Integer.toString(value3)), record3);
		}
				
		trace("leaves/subnodes: " + vt1._root.getLeaves() + "/" + vt1._root.getSubnodes());

		ArrayList<VectorTree> trees = new ArrayList<VectorTree>();
		trees.add(vt1);
		trees.add(vt2);
		trees.add(vt3);
		System.out.println("done.");

		System.out.println("intersecting...");
		VectorTreeIterator iterator1 = VectorTree.intersect(trees, false);
		System.out.println("done.");

		System.out.println("intersecting...");
		VectorTreeIterator iterator2 = VectorTree.intersect(trees, true);
		System.out.println("done.");

		int counter = 0;

		counter = 0;
		System.out.println("trial 1");
		while(iterator1.hasNext()) {
			Integer q1 = iterator1.next();
			// System.out.println("    " + q1);
			counter++;
		}
		System.out.println(counter + " total");

		System.out.println("trial 2");
		counter = 0;
		while(iterator2.hasNext()) {
			Integer q2 = iterator2.next();
			// System.out.println("    " + q2);
			counter++;
		}

		System.out.println(counter + " total");
		System.out.println("\npreparing gold...");
		
		HashSet<Integer> s1 = new HashSet<Integer>(vt1list);		
		s1.retainAll(vt2list);
		s1.retainAll(vt3list);

		System.out.println(s1.size() + " gold");
	}
	
	
	public static void testMultiIntersect() {

//		System.err.print("Initializing.. ");

		ArrayList<VectorTree> vector_trees = new ArrayList<VectorTree>(num_sets);
		int[][] key_sets = new int[num_sets][];
		
		for (int i=0; i<num_sets; i++)
			vector_trees.add(new VectorTree());

		key_sets[0] = DataGenerator.generateUniform(set_size, set_density);
		for (int i=1; i<num_sets; i++)
			key_sets[i] = DataGenerator.generateOverlapping(set_size, key_sets[0], set_overlap);

//		System.err.println("..done.");
//		System.err.print("Inserting.. ");

		for (int i=0; i<num_sets; i++) {
			VectorTree vt = vector_trees.get(i);
			for (int j=0; j<key_sets[i].length; j++) {
				Record record = new Record();
				record.set("1", key_sets[i][j]);

				vt.insert(record.get("1"), record);
			}
		}
//		System.err.println("..done.");

//		trace("leaves/subnodes: " + vector_trees.get(0)._root.getLeaves() + "/" + vector_trees.get(0)._root.getSubnodes());

//		System.err.println("Intersecting without bloom filters.. ");
		VectorTreeIterator iterator = null;

		long startTime = System.nanoTime();
		long endTime;
		try {
			for (int n=0; n<repetitions; n++)
				iterator = VectorTree.intersect(vector_trees, false);
		} finally {
		  endTime = System.nanoTime();
		}
		long no_bloom_duration = endTime - startTime;
		System.out.print((no_bloom_duration / 1000000.0) + "\t");

//		System.err.println("Intersecting with bloom filters.. ");
		
		startTime = System.nanoTime();
		try {
			for (int n=0; n<repetitions; n++) {
				bloom_hits = 0;
				iterator = VectorTree.intersect(vector_trees, true);
			}
		} finally {
		  endTime = System.nanoTime();
		}
		long bloom_duration = endTime - startTime;
		System.out.println((bloom_duration / 1000000.0) + "\t" + bloom_hits);
	}


	public static void main(String[] args) throws IOException {
		
		if (args.length < 7) {
			System.err.println("Arguments: max_bloom_lvl bloom_size_offset num_sets set_size set_density set_overlap repetitions");
			return;
		}
		
		max_bloom_level = Integer.parseInt(args[0]);
		bloom_size_offset =  Integer.parseInt(args[1]);
		num_sets = Integer.parseInt(args[2]);
		set_size = Integer.parseInt(args[3]);
		set_density = Double.parseDouble(args[4]);
		set_overlap = Double.parseDouble(args[5]);
		repetitions = Integer.parseInt(args[6]);
		
//		test4();
		testMultiIntersect();
	}
}
