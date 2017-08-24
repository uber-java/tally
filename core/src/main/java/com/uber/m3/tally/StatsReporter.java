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

import java.util.Map;

/**
 * A backend for {@link Scope}s to report metrics to.
 */
public interface StatsReporter extends BaseStatsReporter {
    /**
     * Reports a {@link Counter}.
     * @param name  name of {@link Counter} to report
     * @param tags  tags to report on
     * @param value value to report
     */
    void reportCounter(
            String name,
            Map<String, String> tags,
            long value
    );

    /**
     * Reports a {@link Gauge}.
     * @param name  name of {@link Gauge} to report
     * @param tags  tags to report on
     * @param value value to report
     */
    void reportGauge(
            String name,
            Map<String, String> tags,
            double value
    );

    /**
     * Report a {@link Timer}.
     * @param name     name of {@link Timer} to report
     * @param tags     tags to report on
     * @param interval interval to report
     */
    void reportTimer(
            String name,
            Map<String, String> tags,
            Duration interval
    );

    /**
     * Report a {@link Histogram}.
     * @param name             name of {@link Histogram} to report
     * @param tags             tags to report on
     * @param buckets          {@link Buckets} of the {@link Histogram}
     * @param bucketLowerBound lower bound of the bucket to report
     * @param bucketUpperBound upper bound of the bucket to report
     * @param samples          samples to report
     */
    void reportHistogramValueSamples(
            String name,
            Map<String, String> tags,
            Buckets buckets,
            double bucketLowerBound,
            double bucketUpperBound,
            long samples
    );

    /**
     * Report a {@link Histogram}.
     * @param name             name of {@link Histogram} to report
     * @param tags             tags to report on
     * @param buckets          {@link Buckets} of the {@link Histogram}
     * @param bucketLowerBound lower bound of the bucket to report
     * @param bucketUpperBound upper bound of the bucket to report
     * @param samples          samples to report
     */
    void reportHistogramDurationSamples(
            String name,
            Map<String, String> tags,
            Buckets buckets,
            Duration bucketLowerBound,
            Duration bucketUpperBound,
            long samples
    );
}
