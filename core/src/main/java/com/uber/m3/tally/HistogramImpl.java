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
import com.uber.m3.util.ImmutableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Default implementation of a {@link Histogram}.
 */
class HistogramImpl extends MetricBase implements Histogram, StopwatchRecorder {
    private final Type type;
    private final ImmutableMap<String, String> tags;
    private final Buckets specification;

    // NOTE: Bucket counters are lazily initialized. Since ref updates are atomic in JMM,
    // no dedicated synchronization is used on the read path, only on the write path
    private final CounterImpl[] bucketCounters;

    private final double[] lookupByValue;
    private final Duration[] lookupByDuration;

    HistogramImpl(
        String fqn,
        ImmutableMap<String, String> tags,
        Buckets buckets
    ) {
        super(fqn);

        if (buckets instanceof DurationBuckets) {
            type = Type.DURATION;
        } else {
            type = Type.VALUE;
        }

        this.tags = tags;
        this.specification = buckets;

        // Each bucket value, serves as a boundary de-marking upper bound
        // for the bucket to the left, and lower bound for the bucket to the right
        this.bucketCounters = new CounterImpl[buckets.asValues().length + 1];

        this.lookupByValue =
                Arrays.stream(buckets.asValues())
                    .mapToDouble(x -> x)
                    .toArray();

        this.lookupByDuration =
                Arrays.copyOf(buckets.asDurations(), buckets.asDurations().length);
    }

    @Override
    public void recordValue(double value) {
        int index = toBucketIndex(Arrays.binarySearch(lookupByValue, value));
        getOrCreateCounter(index).inc(1);
    }

    @Override
    public void recordDuration(Duration duration) {
        int index = toBucketIndex(Arrays.binarySearch(lookupByDuration, duration));
        getOrCreateCounter(index).inc(1);
    }

    private CounterImpl getOrCreateCounter(int index) {
        if (bucketCounters[index] != null) {
            return bucketCounters[index];
        }

        // To maintain lock granularity we synchronize only on a
        // particular bucket leveraging bucket's boundary as a sync target
        synchronized (lookupByDuration[Math.min(index, lookupByDuration.length - 1)]) {
            // Check whether bucket has been already set,
            // while we were waiting for lock
            if (bucketCounters[index] != null) {
                return bucketCounters[index];
            }

            return (bucketCounters[index] = new CounterImpl(getQualifiedName()));
        }
    }

    private int toBucketIndex(int binarySearchResult) {
        // Buckets are defined in the following way:
        //      - Each bucket is inclusive of its lower bound, and exclusive of the upper: [lower, upper)
        //      - All buckets are defined by upper bounds: [2, 4, 8, 16, 32, ...]: therefore i
        //      in this case [-inf, 2) will be the first bucket, [2, 4) -- the second and so on
        //
        // Given that our buckets are designated as [lower, upper), and
        // that the binary search is performed over upper bounds, if binary
        // search found the exact match we need to shift it by 1 to index appropriate bucket in the
        // array of (bucket's) counters
        if (binarySearchResult >= 0) {
            return binarySearchResult + 1;
        }

        // Otherwise, binary search will return {@code (-(insertion point) - 1)} where
        // "insertion point" designates first element that is _greater_ than the key, therefore
        // we simply use this an index in the array of counters
        //
        // NOTE: {@code ~binarySearchResult} is equivalent to {@code -(binarySearchResult) - 1}
        return ~binarySearchResult;
    }

    @Override
    public Stopwatch start() {
        return new Stopwatch(System.nanoTime(), this);
    }

    ImmutableMap<String, String> getTags() {
        return tags;
    }

    @Override
    void report(String name, ImmutableMap<String, String> tags, StatsReporter reporter) {
        for (int i = 0; i < bucketCounters.length; ++i) {
            long samples = getCounterValue(i);
            if (samples == 0) {
                continue;
            }

            switch (type) {
                case VALUE:
                    reporter.reportHistogramValueSamples(
                        name, tags, specification, getLowerBoundValueForBucket(i), getUpperBoundValueForBucket(i), samples
                    );
                    break;
                case DURATION:
                    reporter.reportHistogramDurationSamples(
                        name, tags, specification, getLowerBoundDurationForBucket(i), getUpperBoundDurationForBucket(i), samples
                    );
                    break;
            }
        }
    }

    private Duration getUpperBoundDurationForBucket(int bucketIndex) {
        return bucketIndex < lookupByDuration.length ? lookupByDuration[bucketIndex] : Duration.MAX_VALUE;
    }

    private double getUpperBoundValueForBucket(int bucketIndex) {
        return bucketIndex < lookupByValue.length ? lookupByValue[bucketIndex] : Double.MAX_VALUE;
    }

    private Duration getLowerBoundDurationForBucket(int bucketIndex) {
        return bucketIndex == 0 ? Duration.MIN_VALUE : lookupByDuration[bucketIndex - 1];
    }

    private double getLowerBoundValueForBucket(int bucketIndex) {
        return bucketIndex == 0 ? Double.MIN_VALUE : lookupByValue[bucketIndex - 1];
    }

    private long getCounterValue(int index) {
        return bucketCounters[index] != null ? bucketCounters[index].value() : 0;
    }

    // NOTE: Only used in testing
    Map<Double, Long> snapshotValues() {
        if (type == Type.DURATION) {
            return null;
        }

        Map<Double, Long> values = new HashMap<>(bucketCounters.length, 1);

        for (int i = 0; i < bucketCounters.length; ++i) {
            values.put(getUpperBoundValueForBucket(i), getCounterValue(i));
        }

        return values;
    }

    Map<Duration, Long> snapshotDurations() {
        if (type == Type.VALUE) {
            return null;
        }

        Map<Duration, Long> durations = new HashMap<>(bucketCounters.length, 1);


        for (int i = 0; i < bucketCounters.length; ++i) {
            durations.put(getUpperBoundDurationForBucket(i), getCounterValue(i));
        }

        return durations;
    }

    @Override
    public void recordStopwatch(long stopwatchStart) {
        recordDuration(Duration.between(stopwatchStart, System.nanoTime()));
    }

    enum Type {
        VALUE,
        DURATION
    }
}
