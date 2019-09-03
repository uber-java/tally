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
 * A BucketPair describes the lower and upper bounds
 * for a derived bucket from a buckets set.
 */
public interface BucketPair {

    static BucketPair[] create(Buckets buckets) {
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

        Collections.sort(buckets);

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

    /**
     * Returns the lower bound as a {@code double}
     * @return the lower bound as a {@code double}
     */
    double lowerBoundValue();

    /**
     * Returns the upper bound as a {@code double}
     * @return the upper bound as a {@code double}
     */
    double upperBoundValue();

    /**
     * Returns the lower bound as a {@link Duration}
     * @return the lower bound as a {@code Duration}
     */
    Duration lowerBoundDuration();

    /**
     * Returns the upper bound as a {@link Duration}
     * @return the upper bound as a {@code Duration}
     */
    Duration upperBoundDuration();
}
