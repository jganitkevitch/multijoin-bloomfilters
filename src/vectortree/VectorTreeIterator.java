package vectortree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;


public class VectorTreeIterator implements Iterator<Integer> {

	public static class Matches {
		protected int _prefix;
		protected ArrayList<Short> _offsets;
		
		public Matches(
				int prefix,
				ArrayList<Short> offsets) {

			_prefix = prefix;
			_offsets = offsets;
		}
	}

	// members
	
	private LinkedList<Matches> _queue;
	private ArrayList<HashMap<Integer, ArrayList<Record>>> _all_registrants;
	private int _current_matchset;
	private short _position_in_current_matchset;
	private short _num_bits;

	
	public VectorTreeIterator(
			ArrayList<HashMap<Integer, ArrayList<Record>>> all_registrants,
			short num_bits) {
		_all_registrants = all_registrants;
		_num_bits = num_bits;
		
		_queue = new LinkedList<Matches>();
		
		_current_matchset = 0;
		_position_in_current_matchset = 0;
	}


	
	public void enqueue(Matches matches) {
		_queue.add(matches);
	}
	
	
	public boolean hasNext() {
		// is queue empty?
		if (_queue.size() == 0){ 
			return false;
		}

		// are we on the last matchset in the list?
		if (_current_matchset == _queue.size() - 1) {
			// if so, are there still matches to process?
			Matches current_matches = _queue.get(_current_matchset);
			
			if ((current_matches._offsets.isEmpty()) ||
					(_position_in_current_matchset >= current_matches._offsets.size())) {
				return false;
			}
		}
		
		return true;
	}

	public Integer next() {

		// is there anything left to process?
		if (hasNext() == false) {
			return null;
		}
		
		// we aren't at the end of the queue, so load whatever is waiting
		Matches current_matches = _queue.get(_current_matchset);

		if ((current_matches._offsets.isEmpty()) ||
			(_position_in_current_matchset >= current_matches._offsets.size())) {

			moveToNextMatchset();
			current_matches = _queue.get(_current_matchset);
		}

		int offset = current_matches._offsets.get(_position_in_current_matchset);
		int prefix = current_matches._prefix;
		
		int key = (prefix << _num_bits) + offset;
		
		
		_position_in_current_matchset += 1;

		return key;
	}
	
	private void moveToNextMatchset() {
		_current_matchset += 1;
		_position_in_current_matchset = 0;
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		
	}

}
