package vectortree;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;

public class BloomFilter {

	private BitSet bitSet;

	// expected number of objects to be stored in the filter
	private int expectedNumberOfObjects;

	// a prime number that should be bigger than the size of the bit set.
	private long bigPrime;

	// the size of the bit set, in bits.
	private int filterSize;

	// random number generator
	private Random r = new Random();

	// hash function factors
	private long [][] hashFunctions;


	public BloomFilter(long seed, int filter_size, int expected_number_of_objects) {
		r.setSeed(seed);

		bitSet = new BitSet(filter_size);
		this.filterSize = filter_size;
		this.expectedNumberOfObjects = expected_number_of_objects;
		bigPrime = getPrimeLargerThan(filter_size);

		initializeHashFunctions();
	}

	public void add(int key) {
		
//		System.err.println("BEFORE: " + this.bitSet);
		
//		System.err.println("ADDING: " + key + " (FUNCTIONS: " + hashFunctions.length + ")");
		
		for (long [] h : hashFunctions) {
			int i = hash(h, (long) key);
			bitSet.set(i);
		}
		
//		System.err.println("AFTER: " + this.bitSet);
	}

	public boolean query(int key) {
		for (long [] h : hashFunctions) {
			int i = hash(h, (long) key);
			if (!bitSet.get(i))
				return false;
		}
		return true;
	}

	public int intersect(BloomFilter other) {
		BitSet intersection = (BitSet) this.bitSet.clone();
		intersection.and(other.bitSet);
		return (intersection.cardinality() / hashFunctions.length);
	}

	private void initializeHashFunctions() {
		int number_of_hash_functions;

		int big_prime_int = (int) bigPrime;

		number_of_hash_functions = (int) Math.floor(Math.log(2) * filterSize / expectedNumberOfObjects);

//		trace("a: " + Math.log(2) + ", fs: " + filterSize + ", bss: " + bitSet.size() + ", eno: " 
//				+ expectedNumberOfObjects + ", number_of_hash_functions: " + number_of_hash_functions);

		if (number_of_hash_functions == 0) number_of_hash_functions = 1;

		hashFunctions = new long[number_of_hash_functions][2];
		for (long [] h : hashFunctions) {
			h[0] = (long) r.nextInt(big_prime_int) + 1;
			h[1] = (long) r.nextInt(big_prime_int) + 1;
		}
	}

	private int hash(long [] h, long key) {
		long obj = (key < Integer.MAX_VALUE) ? key : key - bigPrime;
		long h0 = h[0];
		long h1 = (h[1] < (Long.MAX_VALUE / 2)) ? h[1] : h[1] - bigPrime;
		long ret = (obj * h0) % bigPrime;
		ret = (ret < (Long.MAX_VALUE / 2)) ? ret : ret - bigPrime;
		return (int) (((ret + h1) % bigPrime) % (long) filterSize);
	}

	private long getPrimeLargerThan(int n) {
		BigInteger ret;
		BigInteger maxLong = BigInteger.valueOf(Long.MAX_VALUE);
		int numBits = BigInteger.valueOf(n).bitLength() + 1;
		do {
			ret = BigInteger.probablePrime(numBits, r);
		} while (ret.compareTo(maxLong) > 1);
		return ret.longValue();
	}

	public static int intersectMutiway(ArrayList<BloomFilter> bloom_filters) {
		
		// if we are passed no bloom filters, indicate no overlap
		if (bloom_filters.size() == 0) {
			trace("No bloom-filters were passed");
			return 0;
		}
		
		BloomFilter initial_filter = bloom_filters.get(0);

		BitSet intersection = (BitSet) initial_filter.bitSet.clone();

		for (BloomFilter bloom_filter : bloom_filters) {
			intersection.and(bloom_filter.bitSet);
			
//			System.out.println("mway:   " + intersection.cardinality());
		}
//		trace("Results in: " + intersection.cardinality()  + " / " + initial_filter.hashFunctions.length +
//				" = " + (intersection.cardinality() / initial_filter.hashFunctions.length));
		
		return (intersection.cardinality() / initial_filter.hashFunctions.length);
	}

	public static void trace(String msg) {
		System.out.println(msg);
	}

	
	public static void main(String[] args) {

		BloomFilter bf1 = new BloomFilter(1l, 1 << 20, 100000);
		BloomFilter bf2 = new BloomFilter(1l, 1 << 20, 100000);
		BloomFilter bf3 = new BloomFilter(1l, 1 << 20, 100000);
		
		bf1.add(1);
		bf1.add(2);
		bf1.add(3);
		bf1.add(3);
		bf1.add(4);
		bf1.add(12);
		bf1.add(14);
		bf1.add(16);

		bf2.add(1);
		bf2.add(3);
		bf2.add(6);
		bf2.add(16);

		bf3.add(4);
		bf3.add(7);
		bf3.add(9);
		bf3.add(13);
		bf3.add(17);

		System.out.println(bf1.intersect(bf2));
		
		ArrayList<BloomFilter> bloom_filters1 = new ArrayList<BloomFilter>();
		ArrayList<BloomFilter> bloom_filters2 = new ArrayList<BloomFilter>();
		bloom_filters1.add(bf1);
		bloom_filters1.add(bf2);
		System.out.println(BloomFilter.intersectMutiway(bloom_filters1));
		
		bloom_filters2.add(bf1);
		bloom_filters2.add(bf2);
		bloom_filters2.add(bf3);
		System.out.println(BloomFilter.intersectMutiway(bloom_filters2));

	}
}