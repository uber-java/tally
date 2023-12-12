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
import com.uber.m3.util.ImmutableMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of a {@link Histogram}.
 */
class HistogramImpl extends MetricBase implements Histogram, StopwatchRecorder {
    private final Type type;

    private final ImmutableMap<String, String> tags;

    private final ImmutableBuckets specification;

    // NOTE: Bucket counters are lazily initialized. Since ref updates are atomic in JMM,
    // no dedicated synchronization is used on the read path, only on the write path
    private final CounterImpl[] bucketCounters;

    private final ScopeImpl scope;

    HistogramImpl(
        ScopeImpl scope,
        String fqn,
        ImmutableMap<String, String> tags,
        Buckets buckets
    ) {
        super(fqn);

        this.scope = scope;
        this.type = buckets instanceof DurationBuckets ? Type.DURATION : Type.VALUE;
        this.tags = tags;
        this.specification = buckets;

        // Each bucket value, serves as a boundary de-marking upper bound
        // for the bucket to the left, and lower bound for the bucket to the right
        this.bucketCounters = new CounterImpl[buckets.asValues().length + 1];
    }

    @Override
    public void recordValue(double value) {
        int index = toBucketIndex(Collections.binarySearch(specification.getValueUpperBounds(), value));
        getOrCreateCounter(index).inc(1);
    }

    @Override
    public void recordDuration(Duration duration) {
        int index = toBucketIndex(Collections.binarySearch(specification.getDurationUpperBounds(), duration));
        getOrCreateCounter(index).inc(1);
    }

    private CounterImpl getOrCreateCounter(int index) {
        if (bucketCounters[index] != null) {
            return bucketCounters[index];
        }

        List<?> bucketsBounds =
                this.type == Type.VALUE
                        ? specification.getValueUpperBounds()
                        : specification.getDurationUpperBounds();

        // To maintain lock granularity we synchronize only on a
        // particular bucket leveraging bucket's boundary as a sync target
        synchronized (bucketsBounds.get(Math.min(index, bucketsBounds.size() - 1))) {
            // Check whether bucket has been already set,
            // while we were waiting for lock
            if (bucketCounters[index] != null) {
                return bucketCounters[index];
            }

            bucketCounters[index] = new HistogramBucketCounterImpl(scope, getQualifiedName(), index);
            return bucketCounters[index];
        }
    }

    static int toBucketIndex(int binarySearchResult) {
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

    private Duration getUpperBoundDurationForBucket(int bucketIndex) {
        return bucketIndex < specification.getDurationUpperBounds().size()
                ? specification.getDurationUpperBounds().get(bucketIndex)
                : Duration.MAX_VALUE;
    }

    private Duration getLowerBoundDurationForBucket(int bucketIndex) {
        return bucketIndex == 0
                ? Duration.MIN_VALUE
                : specification.getDurationUpperBounds().get(bucketIndex - 1);
    }

    private double getUpperBoundValueForBucket(int bucketIndex) {
        return bucketIndex < specification.getValueUpperBounds().size()
                ? specification.getValueUpperBounds().get(bucketIndex)
                : Double.POSITIVE_INFINITY;
    }

    private double getLowerBoundValueForBucket(int bucketIndex) {
        return bucketIndex == 0
                ? Double.NEGATIVE_INFINITY
                : specification.getValueUpperBounds().get(bucketIndex - 1);
    }

    private long getCounterValue(int index) {
        return bucketCounters[index] != null
                ? bucketCounters[index].value()
                : 0;
    }

    // NOTE: Only used in testing
    Map<Double, Long> snapshotValues() {
        if (type == Type.DURATION) {
            return null;
        }

        int length = bucketCounters.length;
        Map<Double, Long> values = new HashMap<>(length, 1);

        for (int i = 0; i < length; ++i) {
            values.put(getUpperBoundValueForBucket(i), getCounterValue(i));
        }

        return values;
    }

    Map<Duration, Long> snapshotDurations() {
        if (type == Type.VALUE) {
            return null;
        }

        int length = bucketCounters.length;
        Map<Duration, Long> durations = new HashMap<>(length, 1);

        for (int i = 0; i < length; ++i) {
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

    /**
     * Extension of the {@link CounterImpl} adjusting it's reporting procedure
     * to adhere to histogram format
     */
    class HistogramBucketCounterImpl extends CounterImpl {

        private final int bucketIndex;

        protected HistogramBucketCounterImpl(ScopeImpl scope, String fqn, int bucketIndex) {
            super(scope, fqn);

            this.bucketIndex = bucketIndex;
        }

        @Override
        public void report(ImmutableMap<String, String> tags, StatsReporter reporter) {
            long inc = value();
            if (inc == 0) {
                // Nothing to report
                return;
            }

            switch (type) {
                case VALUE:
                    reporter.reportHistogramValueSamples(
                        getQualifiedName(),
                        tags,
                        (Buckets) specification,
                        getLowerBoundValueForBucket(bucketIndex),
                        getUpperBoundValueForBucket(bucketIndex),
                        inc
                    );
                    break;
                case DURATION:
                    reporter.reportHistogramDurationSamples(
                        getQualifiedName(),
                        tags,
                        (Buckets) specification,
                        getLowerBoundDurationForBucket(bucketIndex),
                        getUpperBoundDurationForBucket(bucketIndex),
                        inc
                    );
                    break;
            }
        }
    }
}
