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

public interface ImmutableBuckets {

    double getValueLowerBoundFor(int bucketIndex);
    double getValueUpperBoundFor(int bucketIndex);

    Duration getDurationLowerBoundFor(int bucketIndex);
    Duration getDurationUpperBoundFor(int bucketIndex);

    int getBucketIndexFor(double value);
    int getBucketIndexFor(Duration value);

    /**
     * Returns these buckets as {@code double}s.
     * @return an immutable list of {@code double}s representing these buckets
     */
    List<Double> getValueBuckets();

    /**
     * Returns these buckets as {@link Duration}s.
     * @return an immutable list of {@link Duration}s representing these buckets
     */
    List<Duration> getDurationBuckets();
}
