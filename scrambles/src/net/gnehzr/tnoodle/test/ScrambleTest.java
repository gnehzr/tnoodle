package net.gnehzr.tnoodle.test;

import net.gnehzr.tnoodle.scrambles.*;
import net.gnehzr.tnoodle.scrambles.Puzzle.PuzzleState;
import net.gnehzr.tnoodle.scrambles.AlgorithmBuilder.MungingMode;
import net.gnehzr.tnoodle.utils.BadClassDescriptionException;
import net.gnehzr.tnoodle.utils.LazyInstantiator;
import net.gnehzr.tnoodle.utils.Utils;
import puzzle.CubePuzzle;
import puzzle.CubePuzzle.CubeState;
import puzzle.PyraminxPuzzle;
import puzzle.PyraminxPuzzle.PyraminxState;
import puzzle.PyraminxSolver;
import puzzle.PyraminxSolver.PyraminxSolverState;
import puzzle.TwoByTwoSolver;
import puzzle.TwoByTwoSolver.TwoByTwoState;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.SortedMap;
import java.util.HashMap;
import java.util.Random;

import static net.gnehzr.tnoodle.utils.Utils.azzert;
import static net.gnehzr.tnoodle.utils.Utils.azzertEquals;

public class ScrambleTest {

	static class LockHolder extends Thread {
		public LockHolder() {
			setDaemon(true);
		}

		private Object o;
		public void setObjectToLock(Object o) {
			synchronized(this) {
				this.o = o;
				if(isAlive()) {
					notify();
				} else {
					start();
				}
			}
			try {
				Thread.sleep(100); // give the locker thread a chance to grab the lock
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		@Override
		public synchronized void run() {
			while(o != null) {
				synchronized(o) {
					System.out.println("GOT LOCK " + o);
					Object locked = o;
					while(o == locked) {
						try {
							wait();
						} catch (InterruptedException e) {}
					}
				}
			}
		}
	}

	public static void main(String[] args) throws BadClassDescriptionException, IOException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InvalidScrambleException, InvalidMoveException {

		System.out.println("Testing names.");
		testNames();

		System.out.println("Testing specific CubePuzzle issues.");
		testCubePuzzle();
		System.out.println("CubePuzzle tests passed!");

		System.out.println("Testing specific PyraminxPuzzle issues.");
		testPyraConverter();
		System.out.println("PyraminxPuzzle tests passed!");

		System.out.println("Testing solveIn method");
		testSolveIn();

		testThreads();
	}

	private static void testSolveIn() throws BadClassDescriptionException, IOException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InvalidScrambleException, InvalidMoveException {
		int SCRAMBLE_COUNT = 10;
		int SCRAMBLE_LENGTH = 2;
		Random r = new Random();

		SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = Puzzle.getScramblers();

		for(String puzzle : lazyScramblers.keySet()) {
			LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
			final Puzzle scrambler = lazyScrambler.cachedInstance();

			System.out.println("Testing " + puzzle);

			for(int count = 0; count < SCRAMBLE_COUNT; count++){
				System.out.print("Scramble with:");
				PuzzleState state = scrambler.getSolvedState();
				for(int i = 0; i < SCRAMBLE_LENGTH; i++){
					HashMap<String, ? extends PuzzleState> successors = state.getSuccessors();
					String move = Utils.choose(r, successors.keySet());
					System.out.print(" "+move);
					state = successors.get(move);
				}
				azzert(state.solveIn(SCRAMBLE_LENGTH) != null, "Puzzle "+scrambler.getShortName()+" solveIn method failed!");
				System.out.println(". Found.");
			}
		}
	}

	private static void testThreads() throws BadClassDescriptionException, IOException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InvalidScrambleException, InvalidMoveException {
		LockHolder lh = new LockHolder();

		int SCRAMBLE_COUNT = 10;
		boolean drawScramble = true;

		SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = Puzzle.getScramblers();

		for(String puzzle : lazyScramblers.keySet()) {
			LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
			final Puzzle scrambler = lazyScrambler.cachedInstance();

			System.out.println("Testing " + puzzle);

			// It's easy to get this wrong (read about Arrays.hashCode vs Arrays.deepHashCode).
			// This is just a sanity check.
			Utils.azzert(scrambler.getSolvedState().hashCode() == scrambler.getSolvedState().hashCode());

			// Generating a scramble
			System.out.println("Generating a " + puzzle + " scramble");
			String scramble;
			lh.setObjectToLock(scrambler);
			scramble = scrambler.generateScramble();

			// Drawing that scramble
			System.out.println("Drawing " + scramble);
			BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
			Dimension size = new Dimension(image.getWidth(), image.getHeight());
			scrambler.drawScramble(image.createGraphics(), size, scramble, null);

			// Scramblers should support "null" as the empty scramble
			scrambler.drawScramble(image.createGraphics(), size, null, null);

			System.out.println("Generating & drawing 2 sets of " + SCRAMBLE_COUNT + " scrambles simultaneously." +
								" This is meant to shake out threading problems in scramblers.");
			final ScrambleCacher c1 = new ScrambleCacher(scrambler, SCRAMBLE_COUNT, drawScramble);
			final ScrambleCacher c2 = new ScrambleCacher(scrambler, SCRAMBLE_COUNT, drawScramble);
			ScrambleCacherListener cacherStopper = new ScrambleCacherListener() {
				@Override
				public void scrambleCacheUpdated(ScrambleCacher src) {
					System.out.println(Thread.currentThread() + " " + src.getAvailableCount() + " / " + src.getCacheSize());
					if(src.getAvailableCount() == src.getCacheSize()) {
						src.stop();
						synchronized(c1) {
							c1.notify();
						}
					}
				}
			};
			c1.addScrambleCacherListener(cacherStopper);
			c2.addScrambleCacherListener(cacherStopper);
			while(c1.isRunning() || c2.isRunning()) {
				synchronized(c1) {
					try {
						c1.wait();
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		}
		lh.setObjectToLock(null);
		System.out.println("Test passed!");
	}

	private static void testNames() throws BadClassDescriptionException, IOException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InvalidScrambleException, InvalidMoveException {
		SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = Puzzle.getScramblers();

		// Check that the names by which the scramblers refer to themselves
		// is the same as the names by which we refer to them in the plugin definitions file.
		for(String shortName : lazyScramblers.keySet()) {
			String longName = Puzzle.getScramblerLongName(shortName);
			LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(shortName);
			Puzzle scrambler = lazyScrambler.cachedInstance();

			System.out.println(shortName + " ==? " + scrambler.getShortName());
			Utils.azzert(shortName.equals(scrambler.getShortName()));

			System.out.println(longName + " ==? " + scrambler.getLongName());
			Utils.azzert(longName.equals(scrambler.getLongName()));
		}
	}

	private static void testCubePuzzle() throws InvalidScrambleException, InvalidMoveException {
		testMisc();
		testTwosConverter();
		testTwosSolver();
	}

	private static void testMisc() throws InvalidScrambleException, InvalidMoveException {
		CubePuzzle fours = new CubePuzzle(4);
		CubeState solved = fours.getSolvedState();

		CubeState state = (CubeState) solved.applyAlgorithm("Rw Lw'");
		azzert(state.equals(solved));

		state = (CubeState) solved.applyAlgorithm("Uw Dw'");
		azzert(state.equals(solved));

		CubePuzzle threes = new CubePuzzle(3);

		AlgorithmBuilder ab3 = new AlgorithmBuilder(threes, MungingMode.MUNGE_REDUNDANT_MOVES);
		ab3.appendAlgorithm("D2 U' L2 B2 F2 D B2 U' B2 F D' F U' R F2 L2 D' B D F'");
		azzert(ab3.toString().equals("D2 U' L2 B2 F2 D B2 U' B2 F D' F U' R F2 L2 D' B D F'"));
	}

	private static void testTwosConverter() throws InvalidMoveException {
		int orient = 0;
		int permute = 0;

		int MOVE_R = 3;
		orient = TwoByTwoSolver.moveOrient[orient][MOVE_R];
		permute = TwoByTwoSolver.movePerm[permute][MOVE_R];

		CubePuzzle twos = new CubePuzzle(2);
		CubeState state = (CubeState) twos.getSolvedState().apply("R");
		TwoByTwoState twoByTwoState = state.toTwoByTwoState();

		azzertEquals(twoByTwoState.orientation, orient);
		azzertEquals(twoByTwoState.permutation, permute);

		TwoByTwoSolver twoByTwoSolver = new TwoByTwoSolver();
		azzert(twoByTwoSolver.solveIn(twoByTwoState, 1).equals("R'"));

		int MOVE_R_PRIME = 5;
		orient = TwoByTwoSolver.moveOrient[orient][MOVE_R_PRIME];
		permute = TwoByTwoSolver.movePerm[permute][MOVE_R_PRIME];
		azzert(orient == 0);
		azzert(permute == 0);
	}

	private static void testTwosSolver() throws InvalidScrambleException {
		CubePuzzle twos = new CubePuzzle(2);
		CubeState state = (CubeState) twos.getSolvedState();
		String solution = state.solveIn(0);
		System.out.println(solution);
		azzert(solution.equals(""));

		String scrambleString = "R2 B2 F2";
		try {
			state = (CubeState) state.applyAlgorithm(scrambleString);
		} catch (InvalidScrambleException e) {
			azzert(false, e);
		}

		solution = state.solveIn(1);
		azzert(solution != null);
		System.out.println("Found a solution! " + solution);
		state = (CubeState) state.applyAlgorithm(solution);
		azzert(state.isSolved());
	}

	private static void testPyraConverter() throws InvalidMoveException {

		int SCRAMBLE_COUNT = 1000;
		int SCRAMBLE_LENGTH = 20;
		Random r = new Random();

		int edgePerm = 0;
		int edgeOrient = 0;
		int cornerOrient = 0;
		int tips = 0;
		final String[] moveToString = {"U", "U'", "L", "L'", "R", "R'", "B", "B'"};

		PyraminxPuzzle pyra = new PyraminxPuzzle();
		PyraminxState state = (PyraminxState) pyra.getSolvedState();
		PyraminxSolverState sstate = state.toPyraminxSolverState();
		azzertEquals(sstate.edgePerm, edgePerm);
		azzertEquals(sstate.edgeOrient, edgeOrient);
		azzertEquals(sstate.cornerOrient, cornerOrient);
		azzertEquals(sstate.tips, tips);

		for (int i = 0; i < SCRAMBLE_COUNT; i++){
			System.out.print(" Scramble ["+i+"/"+SCRAMBLE_COUNT+"]\r");
			edgePerm = 0;
			edgeOrient = 0;
			cornerOrient = 0;
			state = (PyraminxState) pyra.getSolvedState();
			for (int j = 0; j < SCRAMBLE_LENGTH; j++){
				int move = r.nextInt(moveToString.length);
				edgePerm = PyraminxSolver.moveEdgePerm[edgePerm][move];
				edgeOrient = PyraminxSolver.moveEdgeOrient[edgeOrient][move];
				cornerOrient = PyraminxSolver.moveCornerOrient[cornerOrient][move];
				state = (PyraminxState) state.apply(moveToString[move]);
			}
			sstate = state.toPyraminxSolverState();

			azzertEquals(sstate.edgePerm, edgePerm);
			azzertEquals(sstate.edgeOrient, edgeOrient);
			azzertEquals(sstate.cornerOrient, cornerOrient);
		}
		System.out.println("");
	}
}
