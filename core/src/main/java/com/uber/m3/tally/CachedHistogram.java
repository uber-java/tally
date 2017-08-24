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
 * Interface for reporting cached histogram samples to buckets.
 */
public interface CachedHistogram {
    /**
     * Returns a {@link CachedHistogramBucket} with the specificed bounds.
     * @param bucketLowerBound the {@link CachedHistogramBucket}'s lower bound
     * @param bucketUpperBound the {@link CachedHistogramBucket}'s upper bound
     * @return a {@link CachedHistogramBucket} with the specificed bounds
     */
    CachedHistogramBucket valueBucket(double bucketLowerBound, double bucketUpperBound);

    /**
     * Returns a {@link CachedHistogramBucket} with the specificed bounds.
     * @param bucketLowerBound the {@link CachedHistogramBucket}'s lower bound {@link Duration}
     * @param bucketUpperBound the {@link CachedHistogramBucket}'s upper bound {@link Duration}
     * @return a {@link CachedHistogramBucket} with the specificed bounds
     */
    CachedHistogramBucket durationBucket(Duration bucketLowerBound, Duration bucketUpperBound);
}
