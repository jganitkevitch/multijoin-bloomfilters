package vectortree;

public class VectorTreeNode {
	private short _vector;
	private int _leaves;
	private int _subnodes;

	public VectorTreeNode() {
		_vector = 0;
		_leaves = 0;
		_subnodes = 0;
	}

	public short getVector() {
		return _vector;
	}

	public void setVector(short vector) {
		_vector = vector;
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
}
