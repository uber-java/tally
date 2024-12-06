// Copyright (c) 2021 Uber Technologies, Inc.
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

import java.util.Collections;
import java.util.List;

/**
 * {@link Buckets} implementation backed by {@code Double} values.
 */
public class ValueBuckets extends AbstractBuckets<Double> {
    public ValueBuckets(Double[] values) {
        super(values);
    }

    @Override
    public double getValueLowerBoundFor(int bucketIndex) {
        return bucketIndex == 0 ? Double.MIN_VALUE : buckets.get(bucketIndex - 1);
    }

    @Override
    public double getValueUpperBoundFor(int bucketIndex) {
        return bucketIndex < buckets.size() ? buckets.get(bucketIndex) : Double.MAX_VALUE;
    }

    @Override
    public int getBucketIndexFor(double value) {
        return HistogramImpl.toBucketIndex(Collections.binarySearch(buckets, value));
    }

    @Override
    public List<Double> getValueUpperBounds() {
        return Collections.unmodifiableList(buckets);
    }

    @Override
    public Duration getDurationLowerBoundFor(int bucketIndex) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public Duration getDurationUpperBoundFor(int bucketIndex) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public int getBucketIndexFor(Duration value) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public List<Duration> getDurationUpperBounds() {
        throw new UnsupportedOperationException("not supported");
    }

    /**
     * @deprecated DO NOT USE
     */
    @Deprecated
    @Override
    public Double[] asValues() {
        return buckets.toArray(new Double[buckets.size()]);
    }

    /**
     * @deprecated DO NOT USE
     */
    @Deprecated
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
     * @param start      the starting bucket's value
     * @param factor     the factor between each bucket
     * @param numBuckets the number of buckets to create
     * @return {@link ValueBuckets} of the specified parameters
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

    /**
     * Helper function to create {@link ValueBuckets} with custom buckets.
     *
     * @param sortedBucketUpperValues sorted (ascending order) values of bucket's upper bound
     * @return {@link ValueBuckets} of the specified parameters
     */
    public static ValueBuckets custom(double... sortedBucketUpperValues) {
        if (sortedBucketUpperValues == null || sortedBucketUpperValues.length == 0) {
            throw new IllegalArgumentException("Must have a positive number of buckets");
        }
        for (int i = 0; i < sortedBucketUpperValues.length - 1; i++) {
            if (sortedBucketUpperValues[i] >= sortedBucketUpperValues[i + 1]) {
                throw new IllegalArgumentException("bucketUpperValues has to be sorted and unique values in ascending order");
            }
        }

        Double[] buckets = new Double[sortedBucketUpperValues.length];
        for (int i = 0; i < sortedBucketUpperValues.length; i++) {
            buckets[i] = sortedBucketUpperValues[i];
        }
        return new ValueBuckets(buckets);
    }
}
