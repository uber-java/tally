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

    private final BucketPair[] bucketBounds;

    HistogramImpl(
        String fqn,
        ImmutableMap<String, String> tags,
        Buckets bucketCounters
    ) {
        super(fqn);

        if (bucketCounters instanceof DurationBuckets) {
            type = Type.DURATION;
        } else {
            type = Type.VALUE;
        }

        this.tags = tags;
        this.specification = bucketCounters;

        this.bucketBounds = BucketPairImpl.bucketPairs(bucketCounters);

        this.bucketCounters = new CounterImpl[bucketBounds.length];

        this.lookupByValue = new double[bucketBounds.length];
        this.lookupByDuration = new Duration[bucketBounds.length];

        for (int i = 0; i < bucketBounds.length; ++i) {
            this.lookupByValue[i] = bucketBounds[i].upperBoundValue();
            this.lookupByDuration[i] = bucketBounds[i].upperBoundDuration();
        }
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
        // particular bucket leveraging bucket's bound object
        synchronized (bucketBounds[index]) {
            // Check whether bucket has been already set,
            // while we were waiting for lock
            if (bucketCounters[index] != null) {
                return bucketCounters[index];
            }

            return (bucketCounters[index] = new CounterImpl(getQualifiedName()));
        }
    }

    private int toBucketIndex(int binarySearchResult) {
        if (binarySearchResult < 0) {
            // binarySearch returns the index of the search key if it is contained in the list;
            // otherwise, (-(insertion point) - 1).
            binarySearchResult = -(binarySearchResult + 1);
        }

        // binarySearch can return collections.size(), guarding against that.
        // pointing to last bucket is fine in that case because it's [_,infinity).
        if (binarySearchResult >= bucketCounters.length) {
            binarySearchResult = bucketCounters.length - 1;
        }

        return binarySearchResult;
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
                        name, tags, specification, bucketBounds[i].lowerBoundValue(), bucketBounds[i].upperBoundValue(), samples
                    );
                    break;
                case DURATION:
                    reporter.reportHistogramDurationSamples(
                        name, tags, specification, bucketBounds[i].lowerBoundDuration(), bucketBounds[i].upperBoundDuration(), samples
                    );
                    break;
            }
        }
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

        for (int i = 0; i < bucketBounds.length; ++i) {
            values.put(bucketBounds[i].upperBoundValue(), getCounterValue(i));
        }

        return values;
    }

    Map<Duration, Long> snapshotDurations() {
        if (type == Type.VALUE) {
            return null;
        }

        Map<Duration, Long> durations = new HashMap<>(bucketCounters.length, 1);


        for (int i = 0; i < bucketBounds.length; ++i) {
            durations.put(bucketBounds[i].upperBoundDuration(), getCounterValue(i));
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
