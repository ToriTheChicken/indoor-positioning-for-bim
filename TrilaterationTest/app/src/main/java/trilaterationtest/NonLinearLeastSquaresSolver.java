package trilaterationtest;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresFactory;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;

/**
 * Solves a Trilateration problem with an instance of a
 * {@link LeastSquaresOptimizer}
 *
 * @author scott
 *
 */
public class NonLinearLeastSquaresSolver {

	protected final TrilaterationFunction function;
	protected final LeastSquaresOptimizer leastSquaresOptimizer;
	protected final int weightsType;

	protected final static int MAXNUMBEROFITERATIONS = 1000;

	public NonLinearLeastSquaresSolver(TrilaterationFunction function, LeastSquaresOptimizer leastSquaresOptimizer) {
		this.function = function;
		this.leastSquaresOptimizer = leastSquaresOptimizer;
		this.weightsType = 0;
	}

	public NonLinearLeastSquaresSolver(TrilaterationFunction function, LeastSquaresOptimizer leastSquaresOptimizer, int weightsType) {
		this.function = function;
		this.leastSquaresOptimizer = leastSquaresOptimizer;
		this.weightsType = weightsType;
	}

	public Optimum solve(double[] target, double[] weights, double[] initialPoint, boolean debugInfo) {
		if (debugInfo) {
			System.out.println("Max Number of Iterations : " + MAXNUMBEROFITERATIONS);
		}

		LeastSquaresProblem leastSquaresProblem = LeastSquaresFactory.create(
				// function to be optimized
				function,
				// target values at optimal point in least square equation
				// (x0+xi)^2 + (y0+yi)^2 + ri^2 = target[i]
				new ArrayRealVector(target, false), new ArrayRealVector(initialPoint, false), new DiagonalMatrix(weights), null, MAXNUMBEROFITERATIONS, MAXNUMBEROFITERATIONS);

		return leastSquaresOptimizer.optimize(leastSquaresProblem);
	}

	public Optimum solve(double[] target, double[] weights, double[] initialPoint) {
		return solve(target, weights, initialPoint, false);
	}

	public Optimum solve(boolean debugInfo) {
		int numberOfPositions = function.getPositions().length;
		int positionDimension = function.getPositions()[0].length;

		double[] initialPoint = new double[positionDimension];
		// initial point, use average of the vertices
		for (int i = 0; i < function.getPositions().length; i++) {
			double[] vertex = function.getPositions()[i];
			for (int j = 0; j < vertex.length; j++) {
				initialPoint[j] += vertex[j];
			}
		}
		for (int j = 0; j < initialPoint.length; j++) {
			initialPoint[j] /= numberOfPositions;
		}

		if (debugInfo) {
			StringBuilder output = new StringBuilder("initialPoint: ");
			for (int i = 0; i < initialPoint.length; i++) {
				output.append(initialPoint[i]).append(" ");
			}
			System.out.println(output.toString());
		}

		double[] target = new double[numberOfPositions];
		double[] distances = function.getDistances();
		double[] stdDevDistance = function.sigmaDistances;
        double[] stdDevPosition = function.sigmaPositions;
		double[] weights = new double[target.length];
		for (int i = 0; i < target.length; i++) {
			target[i] = 0.0;
			switch (this.weightsType) {
				case 0:
					weights[i] = stdDevWeight(stdDevDistance[i], stdDevPosition[i]);
					break;
				case 1:
					weights[i] = inverseSquareLaw(distances[i]);
					break;
				default:
					weights[i] = stdDevWeight(stdDevDistance[i], stdDevPosition[i]) * inverseSquareLaw(distances[i]);
			}
		}

		return solve(target, weights, initialPoint, debugInfo);
	}

	private double inverseSquareLaw(double distance) {
		return 1 / (distance * distance);
	}
	private double stdDevWeight(double stdDevDistance, double stdDevPosition) {
        return 1 / Math.sqrt(stdDevDistance*stdDevDistance+stdDevPosition*stdDevPosition);
    }

	public Optimum solve() {
		return solve(false);
	}
}