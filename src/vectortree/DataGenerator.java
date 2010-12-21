package vectortree;

import java.util.Arrays;
import java.util.Random;

public class DataGenerator {

	// random number generator
	private static Random r = new Random();
	
	// maximum number of distributions for generateRandom
	private static final int MAX_DIST = 5;

	// maximum key set size (sampled from if unspecified) and 
	// maximum key value (sampled from when unspecified), can 
	// play with these for some global density adjustment
	private static final int MAX_SIZE = 5000000;
	private static final int MAX_KEY_VALUE = 20000000;

	public static void seed(long seed) {
		r.setSeed(seed);
	}

	public static int[] generateUniform(int size, double density) {
		int[] key_set = new int[size];

		int scope_size = (int) (size / density);

		for (int i = 0; i < size; i++)
			key_set[i] = r.nextInt(scope_size);

		Arrays.sort(key_set);
		for (int i = 1; i < size; i++)
			if (key_set[i] == key_set[i - 1])
				key_set[i] += 1;

		return key_set;
	}

	public static int[] generateOverlapping(int size, int[] other_set, double overlap) {
		int[] key_set = new int[size];

		int overlap_size = (int) (size * overlap);

		for (int i = 0; i < overlap_size; i++)
			key_set[i] = other_set[r.nextInt(other_set.length)];
		for (int i = overlap_size; i < size; i++)
			key_set[i] = other_set[r.nextInt(MAX_KEY_VALUE)];

		Arrays.sort(key_set);
		for (int i = 1; i < size; i++)
			if (key_set[i] == key_set[i - 1])
				key_set[i] += 1;

		return key_set;
	}

	public static int[] generateRandom() {
		ScopedGaussian size_distribution = new ScopedGaussian(r, 0, MAX_SIZE);
		return generateRandom(size_distribution.nextSample());
	}

	public static int[] generateRandom(int size) {
		int[] key_set = new int[size];
		int index = 0;
		int remaining = size;

		System.err.println("Generating key set of size " + size + ".");

		// draw the number of distributions
		int num_distributions = r.nextInt(MAX_DIST) + 1;

		System.err.println("Using " + num_distributions + " distributions.");

		for (int d = 0; d < num_distributions - 1; d++) {
			// draw how many samples come from this distribution
			ScopedGaussian chunk_distribution = new ScopedGaussian(r, 0,
					remaining - (num_distributions - 1) + d);
			int num_samples = chunk_distribution.nextSample();

			System.err.println("Distribution " + d + ": drawing " + num_samples
					+ " values.");

			addSamples(key_set, index, num_samples);
			remaining -= num_samples;
			index += num_samples;

			System.err.println("Have " + (index + 1) + " samples, " + remaining
					+ " remaining.");
		}
		addSamples(key_set, index, remaining);

		// sort and flatten out duplicates
		Arrays.sort(key_set);
		for (int i = 1; i < size; i++)
			if (key_set[i] == key_set[i - 1])
				key_set[i] += 1;

		return key_set;
	}

	private static void addSamples(int[] key_set, int index, int num_samples) {
		// draw whether this one will be gaussian or uniform
		boolean is_gaussian = r.nextBoolean();

		// draw scope of samples for this distribution
		int from = r.nextInt(MAX_KEY_VALUE - (num_samples + 1));
		int to = r.nextInt(MAX_KEY_VALUE - (num_samples + 1 + from)) + from;

		System.err.println("Distribution is "
				+ (is_gaussian ? "Gaussian" : "uniform") + " over [" + from
				+ ", " + to + "].");

		ScopedGaussian sample_distribution = null;
		if (is_gaussian)
			sample_distribution = new ScopedGaussian(r, from, to);

		for (int i = index; i < index + num_samples; i++)
			key_set[i] = (is_gaussian ? sample_distribution.nextSample() : r
					.nextInt(to - from) + from);
	}

	public static void main(String[] args) {
		int[] test = DataGenerator.generateRandom(50);

		for (int t : test)
			System.out.print(t + "  ");
		System.out.println();
	}
}

class ScopedGaussian {

	private Random r;

	private double mean;
	private double std_dev;

	private int from;
	private int to;

	public ScopedGaussian(Random r, int from, int to) {
		this.r = r;
		this.from = from;
		this.to = to;

		mean = (double) from + (to - from) / 2.0;
		std_dev = (double) (to - from) / 8;
	}

	public int nextSample() {
		int sample;
		// about one in 16,000 will require re-sampling.
		do {
			sample = (int) ((r.nextGaussian() * std_dev + mean));
		} while ((sample <= from) || (sample >= to));
		return sample;
	}
}