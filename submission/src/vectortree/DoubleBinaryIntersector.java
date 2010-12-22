package vectortree;


public class DoubleBinaryIntersector {

	int[][] keySets;
	BloomFilter[] filters;
	
	public DoubleBinaryIntersector(int[][] key_sets) {
		this.keySets = key_sets;
		filters = new BloomFilter[keySets.length];
		
		int max_length = 0;
		for (int i=0; i<keySets.length; i++)
			max_length = (max_length < keySets[i].length) ? keySets[i].length : max_length; 
			
		long seed = 1234567890l;
		for (int i=0; i<keySets.length; i++) {
			filters[i] = new BloomFilter(seed, 1 << 20, max_length);
			for (int j=0; j<keySets[i].length; j++)
				filters[i].add(keySets[i][j]);
		}
		
	}
	
}
