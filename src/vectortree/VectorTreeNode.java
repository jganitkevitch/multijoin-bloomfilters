package vectortree;

import java.io.IOException;

public class VectorTreeNode {
	private short _vector;
	private int _leaves;
	private int _subnodes;
	private int _depth;

	private BloomFilter _bloom_filter;

	public VectorTreeNode(int depth, short bits_per_level, short bits_per_key) {
		_vector = 0;
		_leaves = 0;
		_subnodes = 0;
		_depth = depth;
		
		int filter_size = 1 << (20 - depth);
		int expected_number_of_objects = new Double(Math.pow(2, bits_per_key - bits_per_level * depth)).intValue();
		
		System.err.println("Expecting: " + expected_number_of_objects + " at depth " + depth);
		
		_bloom_filter = new BloomFilter(depth, filter_size, expected_number_of_objects);
	}

	public short getVector() {
		return _vector;
	}

	public void setVector(short vector) {
		_vector = vector;
	}
	
	public BloomFilter getBloomFilter() {
		return _bloom_filter;
	}

	public int getLeaves() {
		return _leaves;
	}

	public void increaseLeavesBy(int amount) {
		_leaves += amount;
	}

	public int getSubnodes() {
		return _subnodes;
	}

	public void increaseSubnodesBy(int amount) {
		_subnodes += amount;
	}

	public static void main(String[] args) throws IOException {
		int i = 0;
		long d = 0L;
		int depth = 0;
		int offset_size = 4;
		int max_depth = 8;
		int half_depth = max_depth / 2;
		
		// root (full range)
		depth = 0;
		System.out.println("\nroot - full range");
		System.out.println(depth * offset_size);

		i = (1 << ((half_depth - depth) * offset_size));
		i = (i << (half_depth * 4)) - 1;
		System.out.println("a: " + i);
		i = new Double(Math.pow(2, offset_size * (max_depth -  depth))).intValue();
		System.out.println("b: " + i);
		d = new Double(Math.pow(2, offset_size * (max_depth -  depth))).longValue();
		System.out.println("c: " + d);

		depth = 2;
		System.out.println("\ndepth 2 - full range");
		System.out.println(depth * offset_size);
		i = 1 << ((max_depth - depth) * offset_size);
		System.out.println(i);
		i = new Double(Math.pow(2, 32 - 4 * depth)).intValue();
		System.out.println(i);

		// leaf
		depth = 7;
		System.out.println("\nleaf - 16");
		System.out.println(depth * offset_size);
		i = 1 << ((max_depth - depth) * offset_size);
		System.out.println(i);
		i = new Double(Math.pow(2, 32 - 4 * depth)).intValue();
		System.out.println(i);
	}
}
