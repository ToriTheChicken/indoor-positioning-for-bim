package com.omega365.wifirtt

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresFactory
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.DiagonalMatrix
import kotlin.math.sqrt

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
 * Solves a Trilateration problem with an instance of a
 * [LeastSquaresOptimizer]
 *
 * @author scott
 */
open class NonLinearLeastSquaresSolver(
    private val function: TrilaterationFunction,
    private val leastSquaresOptimizer: LeastSquaresOptimizer,
    private val weightsType: Int = 0
) {
    @JvmOverloads
    fun solve(
        target: DoubleArray?,
        weights: DoubleArray?,
        initialPoint: DoubleArray?,
        debugInfo: Boolean = false
    ): Optimum {
        if (debugInfo) {
            println("Max Number of Iterations : $MAXNUMBEROFITERATIONS")
        }
        val leastSquaresProblem = LeastSquaresFactory.create( // function to be optimized
            function,  // target values at optimal point in least square equation
            // (x0+xi)^2 + (y0+yi)^2 + ri^2 = target[i]
            ArrayRealVector(target, false),
            ArrayRealVector(initialPoint, false),
            DiagonalMatrix(weights),
            null,
            MAXNUMBEROFITERATIONS,
            MAXNUMBEROFITERATIONS
        )
        return leastSquaresOptimizer.optimize(leastSquaresProblem)
    }

    @JvmOverloads
    fun solve(debugInfo: Boolean = false): Optimum {
        val numberOfPositions = function.positions.size
        val positionDimension: Int = function.positions[0].size
        val initialPoint = DoubleArray(positionDimension)
        // initial point, use average of the vertices
        for (i in function.positions.indices) {
            val vertex = function.positions[i]
            for (j in vertex.indices) {
                initialPoint[j] += vertex[j]
            }
        }
        for (j in initialPoint.indices) {
            initialPoint[j] = initialPoint[j]/numberOfPositions
        }
        if (debugInfo) {
            val output = StringBuilder("initialPoint: ")
            for (i in initialPoint.indices) {
                output.append(initialPoint[i]).append(" ")
            }
            println(output.toString())
        }
        val target = DoubleArray(numberOfPositions)
        val distances = function.distances
        val stdDevDistance = function.sigmaDistances
        val stdDevPosition = function.sigmaPositions
        val weights = DoubleArray(target.size)
        for (i in target.indices) {
            target[i] = 0.0
            when (weightsType) {
                0 -> weights[i] = stdDevWeight(stdDevDistance[i], stdDevPosition[i])
                1 -> weights[i] = inverseSquareLaw(distances[i])
                else -> weights[i] = stdDevWeight(stdDevDistance[i], stdDevPosition[i]) * inverseSquareLaw(distances[i])
            }
        }
        return solve(target, weights, initialPoint, debugInfo)
    }

    private fun stdDevWeight(stdDevDistance: Double, stdDevPosition: Double): Double {
        return 1 / sqrt(stdDevDistance*stdDevDistance+stdDevPosition*stdDevPosition)
    }

    private fun inverseSquareLaw(distance: Double): Double {
        return 1 / (distance * distance)
    }

    companion object {
        private const val MAXNUMBEROFITERATIONS = 1000
    }
}