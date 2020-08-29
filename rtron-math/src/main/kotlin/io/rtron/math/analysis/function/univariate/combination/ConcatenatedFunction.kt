/*
 * Copyright 2019-2020 Chair of Geoinformatics, Technical University of Munich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rtron.math.analysis.function.univariate.combination

import com.github.kittinunf.result.Result
import io.rtron.math.analysis.function.univariate.UnivariateFunction
import io.rtron.math.analysis.function.univariate.pure.ConstantFunction
import io.rtron.math.analysis.function.univariate.pure.LinearFunction
import io.rtron.math.analysis.function.univariate.pure.PolynomialFunction
import io.rtron.math.container.ConcatenationContainer
import io.rtron.math.range.Range
import io.rtron.std.*


/**
 * Represents the sequential concatenation of the provided member functions.
 *
 * @param memberFunctions functions to be concatenated
 * @param absoluteStarts absolute start of the first function
 */
class ConcatenatedFunction(
        memberFunctions: List<UnivariateFunction>,
        absoluteDomains: List<Range<Double>>,
        absoluteStarts: List<Double>
) : UnivariateFunction() {

    // Properties and Initializers
    private val container = ConcatenationContainer(memberFunctions, absoluteDomains, absoluteStarts)
    override val domain: Range<Double> get() = container.domain

    // Methods
    override fun valueUnbounded(x: Double): Result<Double, Exception> {
        val localMember = container.strictSelectMember(x)
                .handleFailure { return it }
        return localMember.member.valueUnbounded(localMember.localParameter)
    }

    override fun slopeUnbounded(x: Double): Result<Double, Exception> {
        val localMember = container.strictSelectMember(x)
                .handleFailure { return it }
        return localMember.member.slopeUnbounded(localMember.localParameter)
    }

    override fun valueInFuzzy(x: Double, tolerance: Double): Result<Double, Exception> {
        val localMember = container.fuzzySelectMember(x, tolerance)
                .handleFailure { return it }
        return localMember.member.valueUnbounded(localMember.localParameter)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConcatenatedFunction) return false

        if (container != other.container) return false

        return true
    }

    override fun hashCode(): Int {
        return container.hashCode()
    }


    companion object {

        /**
         * Creates a concatenated function of a list of linear functions, whereby the slopes are adjusted so that the
         * concatenated function is continuous.
         * For example:
         * f(x) = slope_1 * x + 0 for [0, 5)
         * f(x) = slope_2 * x - 5 for [5, ∞)
         * The [starts] would be listOf(0, 5) and the [intercepts] would be listOf(0, -5).
         *
         * @param starts absolute start value of the function member
         * @param intercepts local intercept of the linear function
         * @param prependConstant if true, the first linear function is preceded by a constant function
         * @param appendConstant if true, the last linear function is appended by a constant function
         */
        fun ofLinearFunctions(starts: List<Double>, intercepts: List<Double>, prependConstant: Boolean = false,
                              appendConstant: Boolean = true): UnivariateFunction {
            require(starts.isNotEmpty() && intercepts.isNotEmpty())
            { "List of starts and intercepts must not be empty." }
            require(starts.hasSameSizeAs(intercepts))
            { "Equally sized starts and intercepts required." }
            require(starts.isSorted())
            { "Start values must be sorted in ascending order." }

            // calculate slopes for continuous function
            val deltaIntercepts = intercepts.zipWithNext().map { it.second - it.first }
            val lengths = starts.zipWithNext().map { it.second - it.first }
            val slopes = deltaIntercepts.zip(lengths).map { it.first / it.second }

            // prepare linear functions
            val preparedStarts = starts.dropLast(1)
            val preparedLinearFunctions = slopes.zip(intercepts)
                    .map { LinearFunction(it.first, it.second) }
            val preparedAbsoluteDomains = starts
                    .zipWithNext()
                    .map { Range.closedOpen(it.first, it.second) }

            // prepend function, if necessary
            val prependedStart = if (prependConstant)
                listOf(Double.MIN_VALUE) else emptyList()
            val prependedFunction = if (prependConstant)
                listOf(ConstantFunction(intercepts.first())) else emptyList()
            val prependedAbsoluteDomain = if (prependConstant)
                listOf(Range.lessThan(starts.first())) else emptyList()
            // append function, if necessary
            val appendedStart = if (appendConstant)
                listOf(starts.last()) else emptyList()
            val appendedFunction = if (appendConstant)
                listOf(ConstantFunction(intercepts.last())) else emptyList()
            val appendedAbsoluteDomain = if (appendConstant)
                listOf(Range.atLeast(starts.last())) else emptyList()


            return ConcatenatedFunction(
                    prependedFunction + preparedLinearFunctions + appendedFunction,
                    prependedAbsoluteDomain + preparedAbsoluteDomains + appendedAbsoluteDomain,
                    prependedStart + preparedStarts + appendedStart)
        }

        /**
         * Creates a concatenated function with a list of polynomial function.
         * For example:
         * f(x) = 2 + 3*x + 4*x^2 + x^3 for [-2, 3)
         * f(x) = 1 + 2*x + 3*x^2 + 4* x^3  for [3, ∞)
         * The [starts] would be listOf(-2, 3) and the [coefficients] would be
         * listOf(arrayOf(2, 3, 4, 1), arrayOf(1, 2, 3, 4)).
         *
         * @param starts absolute start value of the function member
         * @param coefficients coefficients of the polynomial function members
         * @param prependConstant if true, the first linear function is preceded by a constant function
         */
        fun ofPolynomialFunctions(starts: List<Double>, coefficients: List<DoubleArray>, prependConstant: Boolean = false,
                                  prependConstantValue: Double = Double.NaN): ContextMessage<UnivariateFunction> {

            require(starts.isNotEmpty() && coefficients.isNotEmpty())
            { "List of starts and coefficients must not be empty." }
            require(starts.hasSameSizeAs(coefficients))
            { "Equally sized starts and coefficients required." }
            require(starts.isSorted())
            { "Polynomials must be sorted in ascending order." }

            // filter provided starts and coefficients with zero length
            val lengths = starts
                    .zipWithNext()
                    .map { it.second - it.first } + Double.POSITIVE_INFINITY
            val startsCoefficientsFiltered = lengths
                    .zip(starts.zip(coefficients))
                    .filter { it.first != 0.0 }
                    .map { it.second }
            val message = if (startsCoefficientsFiltered.hasSameSizeAs(starts)) ""
            else "Removed element(s) with length zero when building a concatenated polynomial."

            // prepare polynomial functions
            val preparedStarts = startsCoefficientsFiltered
                    .map { it.first }
            val preparedPolynomialFunctions = startsCoefficientsFiltered
                    .map { PolynomialFunction(it.second) }
            val preparedAbsoluteDomains = startsCoefficientsFiltered
                    .map { it.first }
                    .zipWithNext()
                    .map { Range.closedOpen(it.first, it.second) } + Range.atLeast(starts.last())

            // prepend function, if necessary
            val prependedStart = if (prependConstant)
                listOf(Double.MIN_VALUE) else emptyList()
            val prependedFunction = if (prependConstant) {
                val prependValue = if (prependConstantValue.isFinite()) prependConstantValue else
                    preparedPolynomialFunctions.first().value(0.0).handleFailure { throw it.error }

                listOf(ConstantFunction(prependValue))
            } else emptyList()
            val prependedAbsoluteDomain = if (prependConstant)
                listOf(Range.lessThan(starts.first())) else emptyList()


            val concatenatedFunction = ConcatenatedFunction(
                    prependedFunction + preparedPolynomialFunctions,
                    prependedAbsoluteDomain + preparedAbsoluteDomains,
                    prependedStart + preparedStarts)
            return ContextMessage(concatenatedFunction, message)
        }

    }
}
