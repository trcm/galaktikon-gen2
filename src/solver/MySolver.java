package solver;

import java.beans.Visibility;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import problem.Fridge;
import problem.Matrix;
import problem.ProblemSpec;
import java.math.*;


public class MySolver implements OrderingAgent {
	private final Double FAILURE = 10.0;
	private final Double SUCCESS = 0.0;
    private final Double CONSTANT = Math.sqrt(2);
	private final int ITERATION = 100;

	private int weeksRemaining;

	private Random random = new Random();

	private ProblemSpec spec = new ProblemSpec();
	private Fridge fridge;
    private List<Matrix> probabilities;
	private ArrayList<int[]> combs;
	private FridgeGraph stateGraph;
    ArrayList<visitedMemory> visitedGraph;
	
	public MySolver(ProblemSpec spec) throws IOException {
	    this.spec = spec;
		fridge = spec.getFridge();
        probabilities = spec.getProbabilities();
		combs = new ArrayList<>();
		generateFridgeStates();
        visitedGraph = new ArrayList<>();
		stateGraph = new FridgeGraph();
		generateFridgeGraph();
		weeksRemaining = spec.getNumWeeks();

	}
	
	public void doOfflineComputation() {
	    // TODO Write your own code here.

		// generate vi for all states
		ArrayList<FridgeState> states = stateGraph.getStates();

		for (FridgeState f : states) {
			if (f.vi != 1000) {
				f.v0 = f.vi;
			}

			Double vi = 0.0;
			ArrayList<Double> bellman = new ArrayList<>();

			List<FridgeState> children = f.getChildren();

			for (FridgeState c: children) {
				Double temp = transition(f.getInventory(), c.getInventory());
				bellman.add(transition(f.getInventory(), c.getInventory()) * c.v0);
			}

			Double sum = 0.0;
			for (Double b : bellman) {
				sum += b;
			}
			sum = spec.getDiscountFactor() * sum;

			vi = f.v0 + sum;
			f.vi = vi;
		}

	}
	
	public List<Integer> generateShoppingList(List<Integer> inventory,
	        int numWeeksLeft) {
		// Example code that buys one of each item type.
        // TODO Replace this with your own code.

		List<Integer> shopping = new ArrayList<Integer>();

		int[] inventoryArray = new int[inventory.size()];
		for (int x = 0; x < inventory.size(); x++) inventoryArray[x] = inventory.get(x);
		FridgeState current = stateGraph.getSpecific(inventoryArray);
		System.out.println(Arrays.toString(current.getInventory()));
        FridgeState next = mcst(current);
		for(int i = 0; i < inventory.size(); i++) {
			if(next.getInventory() == null){
				System.out.println("Yoda");
			}
			shopping.add(next.getInventory()[i] - inventoryArray[i]);
		}
		weeksRemaining--;
		return shopping;
	}

	private FridgeState mcst(FridgeState current) {

		if (IntStream.of(current.getInventory()).sum() == fridge.getCapacity())
			return current;

		List<FridgeState> children = current.getChildren();
		ArrayList<FridgeState> exploreMe = new ArrayList<FridgeState>();
		ArrayList<FridgeState> visited = new ArrayList<FridgeState>();
		FridgeState bestState = null;
		Double bestScore = 0.0;

		checkScores(current);

		HashMap<FridgeState, Double> stateScores = new HashMap<>();

		for(FridgeState x: children) {
			checkScores(x);

			visitedMemory memCur = getPreVent(current, x);
			double simScore = memCur.score;
			ArrayList<Double> weekScores = new ArrayList<Double>();


			for (int j = 0; j < ITERATION; j++) {
				FridgeState c = x;

				for (int i = 0; i < weeksRemaining; i++) {
					// simulate shopping
					Pair sim = getSimInventory(c);
					FridgeState b = stateGraph.getSpecific(sim.inventory);
					checkScores(b);

					List<FridgeState> bC = b.getChildren();

					if (bC == null)
						continue;

					for(FridgeState y: bC) {
						memCur = getPreVent(b, y);
						//Store scores in array
						memCur.incrementScore();

						y.incrementVisit();
						if(bestState == null) {
							bestState = y;
							bestScore = memCur.score;
						}
						if(memCur.score < bestScore) {
							bestState = y;
							bestScore = memCur.score;
						}
					}
					if (bestState == null) {
						continue;
					}
					simScore += sim.score * Math.pow(spec.getDiscountFactor(), i);
//					simScore += bestScore;
					c = bestState;
					x.incrementVisit();
				}
				weekScores.add(simScore);
			}
			double sum = 0.0;
			for(double y: weekScores)
				sum += y;
			sum = (sum / children.size()) * -1;
			sum = sum + CONSTANT * Math.sqrt(Math.log(x.visited) / memCur.visit);
			stateScores.put(x, sum);
		}

		Iterator it = stateScores.entrySet().iterator();
		double test = 0.0;
		bestState = null;
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();

			if (bestState == null) {
				bestState = (FridgeState)pair.getKey();
				test = (Double)pair.getValue();
			} else if ((Double)pair.getValue() > test) {
				bestState = (FridgeState)pair.getKey();
				test = (Double)pair.getValue();
			}
			// avoids a ConcurrentModificationException
		}

		return bestState;
	}

    private visitedMemory getPreVent(FridgeState a, FridgeState b) {
        for(visitedMemory x: visitedGraph) {
            if(x.getStates(a,b))
                return x;
        }
        return null;
    }

	private double getSimProb(FridgeState x) {
		double[] allScores = new double[ITERATION];
		for(int y = 0; y < ITERATION; y++) {
			//Perform simulations on how well current child will perform on average
			//This is the same function the simulator uses to eat food
			List<Integer> eaten = sampleUserWants(x.getInventory());
			//Compute score based on failures
			double scoreCurrent = 0.0;
			for(int z = 0; z < eaten.size(); z++) {
				int diff = x.getInventory()[z] - eaten.get(z);
				if(diff < 0) {
					scoreCurrent += Math.abs(diff)*FAILURE;
				}
			}
			allScores[y] = scoreCurrent;
		}
		//Find average score
		double sum = 0.0;
		for(double y: allScores)
			sum += y;
		sum = (sum / ITERATION);
		return sum;
	}

	private Pair getSimInventory(FridgeState x) {
		//Perform simulations on how well current child will perform on average
		//This is the same function the simulator uses to eat food
		if (x == null)
			System.out.println();
		List<Integer> eaten = sampleUserWants(x.getInventory());
		//Compute score based on failures
		double scoreCurrent = 0.0;
		int[] diff = new int[fridge.getMaxTypes()];
		for(int z = 0; z < eaten.size(); z++) {
			diff[z] = x.getInventory()[z] - eaten.get(z);

			if(diff[z] < 0) {
				scoreCurrent += Math.abs(diff[z])*FAILURE;
				diff[z] = 0;
			}
		}

		return new Pair(diff, scoreCurrent);
	}

	private void checkScores(FridgeState current) {

		List<FridgeState> children = current.getChildren();

        for(FridgeState x: children) {

            if(getPreVent(current, x) == null) {
				// generate score
				double prob = getSimProb(x);
				visitedMemory memCur = new visitedMemory(current, x, prob);
				visitedGraph.add(memCur);
            }
        }
	}

    /**
	 * Generate the list of states for policy generation. Yo.
	 */
	public void generateFridgeStates() {

		int[] n = new int[fridge.getMaxTypes()];
		ArrayList<Integer> s = new ArrayList<>();
		for (int i = 0; i < fridge.getCapacity(); i++) {
			s.add(fridge.getCapacity());
		}
		genStates(n, s, 0);

//		System.out.println(combs.size() + "\n\n\n");
//		for (int[] x : combs) {
//			System.out.println(Arrays.toString(x));
////			System.out.println("State score " + getStateProb(x));
//		}
	}


	public int[] genStates(int[] n, ArrayList<Integer> Nr, int idx) {

		if (idx == n.length) {
			if (IntStream.of(n).sum() <= fridge.getCapacity()) {
				int[] t = n.clone();
				if (!arrContains(combs, t)) {
					combs.add(t);
				}
			}
			return null;
		}
		for (int i = 0; i <= Nr.get(idx); i++) {
			n[idx] = i;
			genStates(n, Nr, idx + 1);
		}

		return null;
	}


	public boolean arrContains(ArrayList<int[]> arr, int[] x) {
		for (int[] i : arr) {
			for (int j = 0; j <= i.length; j++) {
				if (j == i.length) {
					return true;
				} else if (i[j] != x[j]) {
					break;
				}
			}
		}
		return false;
	}

	public Double getStateProb(int[] state) {
		ArrayList<Double> sum = new ArrayList<Double>();
		int y = 0;
		for(int x: state) {
			double probF = 0.0;
			List<Double> m = probabilities.get(y).getRow(x);
			for(int k = 0;k < fridge.getMaxItemsPerType() + 1; k++) {
				int diff = x - k;
				if(diff <= 0) {
					probF += Math.abs(diff)*FAILURE*m.get(k);
				}
				else{
					probF += SUCCESS;
				}
			}
			sum.add(probF);
		y++;
		}
		double totalSum = 0;
		for(double x: sum)
			totalSum += x;
		return totalSum * -1;
	}

	/**
	 * Copy of sampleUserWants from simulator for our own simulations
	 * @param state
	 * @return
	 */
	public List<Integer> sampleUserWants(int[] state) {
		List<Integer> wants = new ArrayList<Integer>();
		for (int k = 0; k < fridge.getMaxTypes(); k++) {
			int i = state[k];
			List<Double> prob = probabilities.get(k).getRow(i);
			wants.add(sampleIndex(prob));
		}
		return wants;
	}

	/**
	 * Takes a list of probability and returns how many items eaten
	 * @param prob
	 * @return
	 */
	public int sampleIndex(List<Double> prob) {
		double sum = 0;
		double r = random.nextDouble();
		for (int i = 0; i < prob.size(); i++) {
			sum += prob.get(i);
			if (sum >= r) {
				return i;
			}
		}
		return -1;
	}

	public Double transition(int[] state, int[] finalState) {

		ArrayList<Double> probTable = new ArrayList<>();
		for (int i = 0; i < state.length; i++) {
			if(state[i] == 0)
				probTable.add(1.0);
			else
				probTable.add(0.0);
		}
		for (int i = 0; i < state.length; i++) {
			if(state[i] == 0)
				continue;
			int stateDiff = state[i] - finalState[i];

			List<Double> pr = probabilities.get(i).getRow(state[i]);

			for (int l = 0; l <= fridge.getMaxItemsPerType(); l++) {
				if (state[i] - l <= finalState[i] && state[i] != 0) {
					// this probability will take us to at least the final state inventory
					probTable.set(i, probTable.get(i) + pr.get(l));
				}
			}
		}
		double multi = 1;
		for(double x: probTable)
			multi *= x;

		return multi;
	}

	public void generateFridgeGraph() {
		for (int i = 0; i < combs.size(); i++) {
			stateGraph.addFridge(new FridgeState(combs.get(i)));
		}

		for (int i = 0; i < combs.size(); i++) {
			FridgeState current = stateGraph.getNode(combs.get(i));
			current.v0 = getStateProb(current.getInventory());
			current.vi = 1000.0;

			for (int j = 0; j < combs.size(); j++) {
				FridgeState inner = stateGraph.getNode(combs.get(j));
				if (current.equals(inner) || inner.capacity() > current.capacity())
					continue;


				boolean t = false;
				for (int k = 0; k < fridge.getMaxTypes(); k++) {
					int diff = current.getInventory()[k] - inner.getInventory()[k];
					if (diff < 0) {
						t = true;
						break;
					}
				}

				if (t) {
					continue;
				}

				Double p = transition(current.getInventory(), inner.getInventory());
				// add the inner as a child of the parent
				current.addEatChildren(inner, p);
				//add the current as a parent of the inner
				inner.addParent(current);
			}
			if (current.capacity() == fridge.getCapacity()) {
				continue;
			}

			for (int j = 0; j < combs.size(); j++) {
				FridgeState inner = stateGraph.getNode(combs.get(j));
				if (current.equals(inner))
					continue;
				int maxDiff = 0;
				boolean t = false;
				for (int k = 0; k < fridge.getMaxTypes(); k++) {
					int diff = inner.getInventory()[k] - current.getInventory()[k];
					if (diff < 0) {
						t = true;
						break;
					}
					maxDiff += diff;
				}

				if (maxDiff > fridge.getMaxPurchase() || maxDiff < 0 || t) {
					continue;
				}

				Double p = transition(current.getInventory(), inner.getInventory());
				// add the inner as a child of the parent
				current.addChildren(inner);
				//add the current as a parent of the inner
				inner.addParent(current);
			}

		}

	}


	public ArrayList<FridgeState> viSort(ArrayList<FridgeState> toSort) {

		if(toSort.size() == 0)
			return toSort;
		for (int i = 0; i < toSort.size(); i++) {
			if (i == toSort.size()-1)  {
				return toSort;
			} else {
				Double viA = toSort.get(i).vi;
				Double viB = toSort.get(i+1).vi;
				if (viA > viB) {
					// swap
					FridgeState temp = toSort.get(i);
					toSort.set(i, toSort.get(i + 1));
					toSort.set(i + 1, temp);
					viSort(toSort);
				} else if (viA == viB) {
					continue;
				}
			}
		}
		return viSort(toSort);


	}

}
