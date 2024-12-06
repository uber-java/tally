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
import java.util.List;

/**
 * Default implementation of a {@link Histogram}.
 */
class HistogramImpl extends MetricBase implements Histogram, StopwatchRecorder {
    private final MonotonicClock clock;
    private final ScopeImpl scope;
    private final Type type;
    private final ImmutableMap<String, String> tags;
    private final ImmutableBuckets specification;

    // NOTE: Bucket counters are lazily initialized. Since ref updates are atomic in JMM,
    // no dedicated synchronization is used on the read path, only on the write path
    private final CounterImpl[] bucketCounters;

    HistogramImpl(
        MonotonicClock clock,
        ScopeImpl scope,
        String fqn,
        ImmutableMap<String, String> tags,
        Buckets buckets
    ) {
        super(fqn);
        this.clock = clock;
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
        return new Stopwatch(clock.nowNanos(), this);
    }

    @Override
    public void recordStopwatch(long stopwatchStart) {
        recordDuration(Duration.between(stopwatchStart, clock.nowNanos()));
    }

    /**
     * Returns the tags associated with this histogram.
     */
    ImmutableMap<String, String> getTags() {
        return tags;
    }

    private enum Type {
        VALUE,
        DURATION
    }

    /**
     * Extension of the {@link CounterImpl} adjusting its reporting procedure to adhere to histogram format.
     */
    private class HistogramBucketCounterImpl extends CounterImpl {

        private final int bucketIndex;

        private HistogramBucketCounterImpl(ScopeImpl scope, String fqn, int bucketIndex) {
            super(scope, fqn);

            this.bucketIndex = bucketIndex;
        }

        @Override
        public void report(ImmutableMap<String, String> tags, StatsReporter reporter) {
            long inc = snapshot();
            if (reporter instanceof SnapshotBasedStatsReporter) {
                // Always report snapshots.
                reportBucket(tags, reporter, inc);
            } else if (inc != 0) {
                // Only report when there is a change in the counter. NOTE: we call value() here to
                // update the previous value.
                reportBucket(tags, reporter, value());
            }
        }

        private void reportBucket(ImmutableMap<String, String> tags, StatsReporter reporter, long inc) {
            switch (type) {
                case VALUE:
                    reporter.reportHistogramValueSamples(
                        getQualifiedName(),
                        tags,
                        (Buckets) specification,
                        specification.getValueLowerBoundFor(bucketIndex),
                        specification.getValueUpperBoundFor(bucketIndex),
                        inc
                    );
                    break;
                case DURATION:
                    reporter.reportHistogramDurationSamples(
                        getQualifiedName(),
                        tags,
                        (Buckets) specification,
                        specification.getDurationLowerBoundFor(bucketIndex),
                        specification.getDurationUpperBoundFor(bucketIndex),
                        inc
                    );
                    break;
            }
        }
    }
}
