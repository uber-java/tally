// Copyright (c) 2017 Uber Technologies, Inc.
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

/**
 * {@link Buckets} implementation backed by {@code Double} values.
 */
public class ValueBuckets extends AbstractBuckets<Double> {
    public ValueBuckets(Double[] values) {
        super(values);
    }

    public ValueBuckets() {
        super();
    }

    @Override
    public Double[] asValues() {
        return buckets.toArray(new Double[buckets.size()]);
    }

    @Override
    public Duration[] asDurations() {
        Duration[] durations = new Duration[buckets.size()];

        for (int i = 0; i < durations.length; i++) {
            durations[i] = Duration.ofNanos((long) (buckets.get(i) * Duration.NANOS_PER_SECOND));
        }

        return durations;
    }

    /**
     * Helper function to create {@link ValueBuckets} of linear spacing.
     * @param start      the starting bucket's value
     * @param width      the width of each bucket
     * @param numBuckets the number of buckets to create
     * @return {@link ValueBuckets} of the specified parameters
     */
    public static ValueBuckets linear(double start, double width, int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("Must have a positive number of buckets");
        }

        if (width <= 0) {
            throw new IllegalArgumentException("Bucket width must be positive");
        }

        Double[] buckets = new Double[numBuckets];

        for (int i = 0; i < numBuckets; i++) {
            buckets[i] = start + (i * width);
        }

        return new ValueBuckets(buckets);
    }

    /**
     * Helper function to create {@link ValueBuckets} of exponential spacing.
     * @param start      the starting bucket's value}
     * @param factor     the factor between each bucket
     * @param numBuckets the number of buckets to create
     * @return {@link ValueBuckets} of the specified paramters
     */
    public static ValueBuckets exponential(double start, double factor, int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("Must have a positive number of buckets");
        }

        if (factor <= 1) {
            throw new IllegalArgumentException("Factor must be greater than 1");
        }

        Double[] buckets = new Double[numBuckets];

        Double curDuration = start;

        for (int i = 0; i < numBuckets; i++) {
            buckets[i] = curDuration;

            curDuration *= factor;
        }

        return new ValueBuckets(buckets);
    }
}
