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
 * {@link Buckets} implementation backed by {@link Duration}s.
 */
public class DurationBuckets extends AbstractBuckets<Duration> {
    public DurationBuckets(Duration[] durations) {
        super(durations);
    }

    public DurationBuckets() {
        super();
    }

    @Override
    public Double[] asValues() {
        Double[] values = new Double[buckets.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = buckets.get(i).getSeconds();
        }

        return values;
    }

    @Override
    public Duration[] asDurations() {
        return buckets.toArray(new Duration[buckets.size()]);
    }

    /**
     * Helper function to create {@link DurationBuckets} of linear spacing.
     * @param start      the starting bucket's {@link Duration}
     * @param width      the width of each bucket
     * @param numBuckets the number of buckets to create
     * @return {@link DurationBuckets} of the specified parameters
     */
    public static DurationBuckets linear(Duration start, Duration width, int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("Must have a positive number of buckets");
        }

        if (width.compareTo(Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("Bucket width must be positive");
        }

        Duration[] buckets = new Duration[numBuckets];

        for (int i = 0; i < numBuckets; i++) {
            buckets[i] = start.add(width.multiply(i));
        }

        return new DurationBuckets(buckets);
    }

    /**
     * Helper function to create {@link DurationBuckets} of exponential spacing.
     * @param start      the starting bucket's {@link Duration}
     * @param factor     the factor between each bucket
     * @param numBuckets the number of buckets to create
     * @return {@link DurationBuckets} of the specified paramters
     */
    public static DurationBuckets exponential(Duration start, double factor, int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("Must have a positive number of buckets");
        }

        if (factor <= 1) {
            throw new IllegalArgumentException("Factor must be greater than 1");
        }

        Duration[] buckets = new Duration[numBuckets];

        Duration curDuration = start;

        for (int i = 0; i < numBuckets; i++) {
            buckets[i] = curDuration;

            curDuration = curDuration.multiply(factor);
        }

        return new DurationBuckets(buckets);
    }

    /**
     * Allows to create bucket with finer bucket creation control
     *
     * @param bucketUpperMillis sorted values (ascending) of upper bound of the buckets
     * @return {@link DurationBuckets} of the specified parameters
     */
    public static DurationBuckets custom(int... bucketUpperMillis) {
        if (bucketUpperMillis == null || bucketUpperMillis.length == 0) {
            throw new IllegalArgumentException("at least one upper bucket value has to be specified");
        }

        for (int i = 0; i < bucketUpperMillis.length - 1; i++) {
            if (bucketUpperMillis[i] > bucketUpperMillis[i + 1]) {
                throw new IllegalArgumentException("bucketUpperMillis has to be sorted in ascending order");
            }
        }

        Duration[] buckets = new Duration[bucketUpperMillis.length];
        for (int i = 0; i < bucketUpperMillis.length; i++) {
            buckets[i] = Duration.ofMillis(bucketUpperMillis[i]);
        }
        return new DurationBuckets(buckets);

    }
}
