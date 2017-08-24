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

import java.util.HashMap;
import java.util.Map;

public class TestStatsReporter implements StatsReporter {
    public long prevCounter;
    public double prevGauge;
    public Duration prevTimer;
    public Buckets buckets;
    public Map<Double, Long> valueSamples = new HashMap<>();
    public Map<Duration, Long> durationSamples = new HashMap<>();

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        prevCounter = value;
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        prevGauge = value;
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        prevTimer = interval;
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
}
