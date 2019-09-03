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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of a {@link Histogram}.
 */
class HistogramImpl implements Histogram, StopwatchRecorder {
    private Type type;
    private String name;
    private ImmutableMap<String, String> tags;
    private Buckets specification;
    private List<HistogramBucket> buckets;
    private List<Double> lookupByValue;
    private List<Duration> lookupByDuration;

    HistogramImpl(
        String name,
        ImmutableMap<String, String> tags,
        StatsReporter reporter,
        Buckets buckets
    ) {
        if (buckets instanceof DurationBuckets) {
            type = Type.DURATION;
        } else {
            type = Type.VALUE;
        }

        BucketPair[] pairs = BucketPairImpl.bucketPairs(buckets);
        int pairsLen = pairs.length;

        this.name = name;
        this.tags = tags;
        specification = buckets;
        this.buckets = new ArrayList<>(pairsLen);
        lookupByValue = new ArrayList<>(pairsLen);
        lookupByDuration = new ArrayList<>(pairsLen);

        for (BucketPair pair : pairs) {
            addBucket(new HistogramBucket(
                pair.lowerBoundValue(),
                pair.upperBoundValue(),
                pair.lowerBoundDuration(),
                pair.upperBoundDuration()
            ));
        }
    }

    private void addBucket(HistogramBucket bucket) {
        buckets.add(bucket);
        lookupByValue.add(bucket.valueUpperBound);
        lookupByDuration.add(bucket.durationUpperBound);
    }

    @Override
    public void recordValue(double value) {
        int index = Collections.binarySearch(lookupByValue, value);

        if (index < 0) {
            // binarySearch returns the index of the search key if it is contained in the list;
            // otherwise, (-(insertion point) - 1).
            index = -(index + 1);
        }

        buckets.get(index).samples.inc(1);
    }

    @Override
    public void recordDuration(Duration duration) {
        int index = Collections.binarySearch(lookupByDuration, duration);

        if (index < 0) {
            // binarySearch returns the index of the search key if it is contained in the list;
            // otherwise, (-(insertion point) - 1).
            index = -(index + 1);
        }

        buckets.get(index).samples.inc(1);
    }

    @Override
    public Stopwatch start() {
        return new Stopwatch(System.nanoTime(), this);
    }

    String getName() {
        return name;
    }

    ImmutableMap<String, String> getTags() {
        return tags;
    }

    void report(String name, ImmutableMap<String, String> tags, StatsReporter reporter) {
        for (HistogramBucket bucket : buckets) {
            long samples = bucket.samples.value();

            if (samples == 0) {
                continue;
            }

            switch (type) {
                case VALUE:
                    reporter.reportHistogramValueSamples(
                        name, tags, specification, bucket.valueLowerBound, bucket.valueUpperBound, samples
                    );
                    break;
                case DURATION:
                    reporter.reportHistogramDurationSamples(
                        name, tags, specification, bucket.durationLowerBound, bucket.durationUpperBound, samples
                    );
                    break;
            }
        }
    }

    Map<Double, Long> snapshotValues() {
        if (type == Type.DURATION) {
            return null;
        }

        Map<Double, Long> values = new HashMap<>(buckets.size(), 1);

        for (HistogramBucket bucket : buckets) {
            values.put(bucket.valueUpperBound, bucket.samples.value());
        }

        return values;
    }

    Map<Duration, Long> snapshotDurations() {
        if (type == Type.VALUE) {
            return null;
        }

        Map<Duration, Long> durations = new HashMap<>(buckets.size(), 1);

        for (HistogramBucket bucket : buckets) {
            durations.put(bucket.durationUpperBound, bucket.samples.value());
        }

        return durations;
    }

    @Override
    public void recordStopwatch(long stopwatchStart) {
        recordDuration(Duration.between(stopwatchStart, System.nanoTime()));
    }

    static class HistogramBucket {
        CounterImpl samples;
        double valueLowerBound;
        double valueUpperBound;
        Duration durationLowerBound;
        Duration durationUpperBound;

        HistogramBucket(
            double valueLowerBound,
            double valueUpperBound,
            Duration durationLowerBound,
            Duration durationUpperBound
        ) {
            samples = new CounterImpl();
            this.valueLowerBound = valueLowerBound;
            this.valueUpperBound = valueUpperBound;
            this.durationLowerBound = durationLowerBound;
            this.durationUpperBound = durationUpperBound;
        }
    }

    enum Type {
        VALUE,
        DURATION
    }
}
