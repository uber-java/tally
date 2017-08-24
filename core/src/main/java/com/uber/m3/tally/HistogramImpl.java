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
class HistogramImpl implements Histogram {
    private Type type;
    private String name;
    private ImmutableMap<String, String> tags;
    private StatsReporter reporter;
    private Buckets specification;
    private List<HistogramBucket> buckets;
    private List<Double> lookupByValue;
    private List<Duration> lookupByDuration;

    HistogramImpl(
        String name,
        ImmutableMap<String, String> tags,
        StatsReporter reporter,
        Buckets buckets,
        CachedHistogram cachedHistogram
    ) {
        if (buckets instanceof DurationBuckets) {
            type = Type.DURATION;
        } else {
            type = Type.VALUE;
        }

        BucketPair[] pairs = bucketPairs(buckets);
        int pairsLen = pairs.length;

        this.name = name;
        this.tags = tags;
        this.reporter = reporter;
        specification = buckets;
        this.buckets = new ArrayList<>(pairsLen);
        lookupByValue = new ArrayList<>(pairsLen);
        lookupByDuration = new ArrayList<>(pairsLen);

        for (BucketPair pair : pairs) {
            addBucket(new HistogramBucket(
                pair.lowerBoundValue(),
                pair.upperBoundValue(),
                pair.lowerBoundDuration(),
                pair.upperBoundDuration(),
                cachedHistogram
            ));
        }
    }

    HistogramImpl(
        String name,
        ImmutableMap<String, String> tags,
        StatsReporter reporter,
        Buckets buckets
    ) {
        this(name, tags, reporter, buckets, null);
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
        return null;
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

    void cachedReport() {
        for (HistogramBucket bucket : buckets) {
            long samples = bucket.samples.value();

            if (samples == 0) {
                continue;
            }

            switch (type) {
                case VALUE:
                    bucket.cachedValueBucket.reportSamples(samples);
                    break;
                case DURATION:
                    bucket.cachedDurationBucket.reportSamples(samples);
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

    static BucketPair[] bucketPairs(Buckets buckets) {
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

        if (buckets instanceof DurationBuckets) {
            // If using duration buckets separating negative times and
            // positive times is very much desirable as depending on the
            // reporter will create buckets "-infinity,0" and "0,{first_bucket}"
            // instead of just "-infinity,{first_bucket}" which for time
            // durations is not desirable nor pragmatic
            boolean hasZero = false;

            for (Duration duration : buckets.asDurations()) {
                if (duration.equals(Duration.ZERO)) {
                    hasZero = true;
                    break;
                }
            }

            if (!hasZero) {
                ((DurationBuckets) buckets).add(Duration.ZERO);
            }
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

    class HistogramBucket {
        CounterImpl samples;
        double valueLowerBound;
        double valueUpperBound;
        Duration durationLowerBound;
        Duration durationUpperBound;
        CachedHistogramBucket cachedValueBucket;
        CachedHistogramBucket cachedDurationBucket;

        HistogramBucket(
            double valueLowerBound,
            double valueUpperBound,
            Duration durationLowerBound,
            Duration durationUpperBound,
            CachedHistogram cachedHistogram
        ) {
            samples = new CounterImpl();
            this.valueLowerBound = valueLowerBound;
            this.valueUpperBound = valueUpperBound;
            this.durationLowerBound = durationLowerBound;
            this.durationUpperBound = durationUpperBound;

            if (cachedHistogram != null) {
                cachedValueBucket = cachedHistogram.valueBucket(valueLowerBound, valueUpperBound);
                cachedDurationBucket = cachedHistogram.durationBucket(durationLowerBound, durationUpperBound);
            }
        }
    }

    enum Type {
        VALUE,
        DURATION
    }
}
