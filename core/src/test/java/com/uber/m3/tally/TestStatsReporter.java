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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestStatsReporter implements StatsReporter {
    private final Queue<MetricStruct<Long>> counters = new ConcurrentLinkedQueue<>();
    private final Queue<MetricStruct<Double>> gauges = new ConcurrentLinkedQueue<>();
    private final Queue<MetricStruct<Duration>> timers = new ConcurrentLinkedQueue<>();
    private final Map<Double, Long> valueSamples = new HashMap<>();
    private final Map<Duration, Long> durationSamples = new HashMap<>();
    private final Map<Double, Long> cumulativeValueSamples = new HashMap<>();

    private Buckets buckets;

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        counters.add(new MetricStruct<>(name, tags, value));
    }

    public MetricStruct<Long> nextCounter() {
        return counters.remove();
    }

    public long nextCounterVal() {
        return counters.remove().getValue();
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        gauges.add(new MetricStruct<>(name, tags, value));
    }

    public MetricStruct<Double> nextGauge() {
        return gauges.remove();
    }

    public double nextGaugeVal() {
        return gauges.remove().getValue();
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        timers.add(new MetricStruct<>(name, tags, interval));
    }

    public MetricStruct<Duration> nextTimer() {
        return timers.remove();
    }

    public Duration nextTimerVal() {
        return timers.remove().getValue();
    }

    @Override
    public void reportHistogramValueSamples(
        String name, Map<String, String> tags,
        Buckets buckets,
        double bucketLowerBound,
        double bucketUpperBound,
        long samples
    ) {
        valueSamples.put(bucketUpperBound, samples);
        cumulativeValueSamples.put(
                bucketUpperBound,
                cumulativeValueSamples.getOrDefault(bucketUpperBound, 0L) + samples
        );
        this.buckets = buckets;
    }

    @Override
    public void reportHistogramDurationSamples(
        String name,
        Map<String, String> tags,
        Buckets buckets,
        Duration bucketLowerBound,
        Duration bucketUpperBound,
        long samples
    ) {
        durationSamples.put(bucketUpperBound, samples);
        this.buckets = buckets;
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING;
    }

    @Override
    public void flush() {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }

    public Map<Duration, Long> getDurationSamples() {
        return durationSamples;
    }

    public Map<Double, Long> getValueSamples() {
        return valueSamples;
    }

    public Map<Double, Long> getCumulativeValueSamples() {
        return cumulativeValueSamples;
    }

    public Buckets getBuckets() {
        return buckets;
    }

    static class MetricStruct<T> {
        private final String name;
        private final Map<String, String> tags;
        private final T value;

        MetricStruct(String name, Map<String, String> tags, T value) {
            this.name = name;
            this.tags = tags;
            this.value = value;
        }

        String getName() {
            return name;
        }

        Map<String, String> getTags() {
            return tags;
        }

        T getValue() {
            return value;
        }

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            }
            if (otherObj == null || getClass() != otherObj.getClass()) {
                return false;
            }
            MetricStruct<?> other = (MetricStruct<?>) otherObj;
            return Objects.equals(name, other.name) && Objects.equals(tags, other.tags) && Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags, value);
        }
    }
}
