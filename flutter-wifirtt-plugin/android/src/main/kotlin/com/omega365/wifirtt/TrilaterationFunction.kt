package com.omega365.wifirtt

import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.util.Pair

/**
 * Altered version of https://github.com/lemmingapex/trilateration
 * Copied and altered with permission under the MIT Licence

The MIT License (MIT)

Copyright (c) 2017 Scott Wiedemann

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

 */


/**
 * Models the Trilateration problem. This is a formulation for a nonlinear least
 * squares optimizer.
 *
 * @author scott
 */
open class TrilaterationFunction(positions: Array<DoubleArray>, distances: DoubleArray, sigmaPositions: DoubleArray? = null, sigmaDistances: DoubleArray? = null) :
    MultivariateJacobianFunction {
    /**
     * Known positions of static nodes
     */
    val positions: Array<DoubleArray>

    /**
     * Euclidean distances from static nodes to mobile node
     */
    val sigmaPositions: DoubleArray
    /**
     * Euclidean distances from static nodes to mobile node
     */
    val distances: DoubleArray

    /**
     * Euclidean distances from static nodes to mobile node
     */
    val sigmaDistances: DoubleArray

    /**
     * Calculate and return Jacobian function Actually return initialized function
     *
     * Jacobian matrix, [i][j] at
     * J[i][0] = delta_[(x0-xi)^2 + (y0-yi)^2 - ri^2]/delta_[x0] at
     * J[i][1] = delta_[(x0-xi)^2 + (y0-yi)^2 - ri^2]/delta_[y0] partial derivative with respect to the parameters passed to value() method
     *
     * @param point for which to calculate the slope
     * @return Jacobian matrix for point
     */
    private fun jacobian(point: RealVector): RealMatrix {
        val pointArray = point.toArray()
        val jacobian = Array(distances.size) {
            DoubleArray(
                pointArray.size
            )
        }
        for (i in jacobian.indices) {
            for (j in pointArray.indices) {
                jacobian[i][j] = 2 * pointArray[j] - 2 * positions[i][j]
            }
        }
        return Array2DRowRealMatrix(jacobian)
    }

    override fun value(point: RealVector): Pair<RealVector, RealMatrix> {

        // input
        val pointArray = point.toArray()

        // output
        val resultPoint = DoubleArray(distances.size)

        // compute least squares
        for (i in resultPoint.indices) {
            resultPoint[i] = 0.0
            // calculate sum, add to overall
            for (j in pointArray.indices) {
                resultPoint[i] += (pointArray[j] - positions[i][j]) * (pointArray[j] - positions[i][j])
            }
            resultPoint[i] -= distances[i] * distances[i]
        }
        val jacobian = jacobian(point)
        return Pair(ArrayRealVector(resultPoint), jacobian)
    }

    companion object {
        private const val epsilon = 1E-7
    }

    init {
        require(positions.size >= 2) { "Need at least two positions." }
        require(positions.size == distances.size) { "The number of positions you provided, " + positions.size + ", does not match the number of distances, " + distances.size + "." }
        if(sigmaPositions == null) this.sigmaPositions = Array<Double>(positions.size){0.0}.toDoubleArray()
        else this.sigmaPositions = sigmaPositions
        if(sigmaDistances == null) this.sigmaDistances = Array<Double>(positions.size){0.0}.toDoubleArray()
        else this.sigmaDistances = sigmaDistances
        // bound distances to strictly positive domain
        for (i in distances.indices) {
            distances[i] = distances[i].coerceAtLeast(epsilon)
            this.sigmaPositions[i] = this.sigmaPositions[i].coerceAtLeast(epsilon)
            this.sigmaDistances[i] = this.sigmaDistances[i].coerceAtLeast(epsilon)
        }
        val positionDimension: Int = positions[0].size
        for (i in 1 until positions.size) {
            require(positionDimension == positions[i].size) { "The dimension of all positions should be the same." }
        }
        this.positions = positions
        this.distances = distances
    }
}