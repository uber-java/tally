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

import java.util.Collections;

/**
 * Default implementation of a {@link BucketPair}
 *
 * @deprecated DO NOT USE, WILL BE REMOVED IN THE NEXT VERSION
 */
@Deprecated
public class BucketPairImpl implements BucketPair {
    private double lowerBoundValue;
    private double upperBoundValue;
    private Duration lowerBoundDuration;
    private Duration upperBoundDuration;

    public BucketPairImpl(
        double lowerBoundValue,
        double upperBoundValue,
        Duration lowerBoundDuration,
        Duration upperBoundDuration
    ) {
        this.lowerBoundValue = lowerBoundValue;
        this.upperBoundValue = upperBoundValue;
        this.lowerBoundDuration = lowerBoundDuration;
        this.upperBoundDuration = upperBoundDuration;
    }

    public static BucketPair[] bucketPairs(Buckets buckets) {
        if (buckets == null || buckets.size() < 1) {
            return new BucketPair[]{
                new BucketPairImpl(
                    -Double.MAX_VALUE,
                    Double.MAX_VALUE,
                    Duration.MIN_VALUE,
                    Duration.MAX_VALUE
                )
            };
        }

        Double[] asValueBuckets = buckets.asValues();
        Duration[] asDurationBuckets = buckets.asDurations();
        BucketPair[] pairs = new BucketPair[buckets.size() + 1];

        // Add lower bound
        pairs[0] = new BucketPairImpl(
            -Double.MAX_VALUE,
            asValueBuckets[0],
            Duration.MIN_VALUE,
            asDurationBuckets[0]
        );

        double prevValueBucket = asValueBuckets[0];
        Duration prevDurationBucket = asDurationBuckets[0];

        for (int i = 1; i < buckets.size(); i++) {
            pairs[i] = new BucketPairImpl(
                prevValueBucket,
                asValueBuckets[i],
                prevDurationBucket,
                asDurationBuckets[i]
            );

            prevValueBucket = asValueBuckets[i];
            prevDurationBucket = asDurationBuckets[i];
        }

        // Add upper bound
        pairs[pairs.length - 1] = new BucketPairImpl(
            prevValueBucket,
            Double.MAX_VALUE,
            prevDurationBucket,
            Duration.MAX_VALUE
        );

        return pairs;
    }

    @Override
    public double lowerBoundValue() {
        return lowerBoundValue;
    }

    @Override
    public double upperBoundValue() {
        return upperBoundValue;
    }

    @Override
    public Duration lowerBoundDuration() {
        return lowerBoundDuration;
    }

    @Override
    public Duration upperBoundDuration() {
        return upperBoundDuration;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof BucketPairImpl)) {
            return false;
        }

        BucketPairImpl otherBucketPair = (BucketPairImpl) other;

        return lowerBoundValue == otherBucketPair.lowerBoundValue
            && upperBoundValue == otherBucketPair.upperBoundValue
            && lowerBoundDuration.equals(otherBucketPair.lowerBoundDuration)
            && upperBoundDuration.equals(otherBucketPair.upperBoundDuration);
    }

    @Override
    public int hashCode() {
        return ((new Double(lowerBoundValue).hashCode() * 33) ^ new Double(upperBoundValue).hashCode())
            ^ ((lowerBoundDuration.hashCode() * 33) ^ upperBoundDuration.hashCode());
    }

    @Override
    public String toString() {
        return String.format("[%s, %s]", lowerBoundDuration, upperBoundDuration);
    }
}
