// Copyright (c) 2020 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.m3.tally;

import com.uber.m3.util.Duration;

import java.util.List;

/**
 * Abstracts buckets used in {@link Histogram} metrics,
 *
 * Buckets are defined by the list of upper-bounds in the following way:
 *
 *  <blockquote>
 *  <pre>
 *      double bounds[] = new double[] { 1, 2, 4, 8, 16 };
 *
 *      // For the given set of bounds:
 *      //    first bucket      [-inf, 1)
 *      //    second bucket     [1, 2)
 *      //    ...
 *      //    last bucket       [16, +inf)
 *  <pre/>
 *  </blockquote>
 */
public interface ImmutableBuckets {

    /**
     * Gets corresponding bucket lower bound
     */
    double getValueLowerBoundFor(int bucketIndex);

    /**
     * Gets corresponding bucket upper bound
     */
    double getValueUpperBoundFor(int bucketIndex);

    /**
     * Gets corresponding bucket lower bound
     */
    Duration getDurationLowerBoundFor(int bucketIndex);

    /**
     * Gets corresponding bucket upper bound
     */
    Duration getDurationUpperBoundFor(int bucketIndex);

    /**
     * Gets index of the corresponding bucket this value would fall under
     */
    int getBucketIndexFor(double value);

    /**
     * Gets index of the corresponding bucket this value would fall under
     */
    int getBucketIndexFor(Duration value);

    /**
     * Returns defined buckets' upper-bound values as {@link Double}s.
     * @return an immutable list of {@code double}s representing these buckets
     */
    List<Double> getValueUpperBounds();

    /**
     * Returns defined buckets' upper-bound values as {@link Duration}s.
     * @return an immutable list of {@link Duration}s representing these buckets
     */
    List<Duration> getDurationUpperBounds();
}
