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

package com.uber.m3.tally.statsd;

import com.timgroup.statsd.StatsDClient;
import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.util.Duration;
import com.uber.m3.tally.StatsReporter;

import java.util.Map;

/**
 * A StatsD reporter
 */
public class StatsdReporter implements StatsReporter {
    private static final int DEFAULT_SAMPLE_RATE = 1;
    private static final int DEFAULT_HISTOGRAM_BUCKET_NAME_PRECISION = 6;

    private StatsDClient statsdClient;
    private double sampleRate;
    private String bucketFmt;

    /**
     * Create a StatsD reporter
     * @param statsd                       a DogStatsD client
     * @param sampleRate                   the sample rate
     * @param histogramBucketNamePrecision precision of histogram bucket
     */
    public StatsdReporter(StatsDClient statsd, double sampleRate, int histogramBucketNamePrecision) {
        statsdClient = statsd;

        this.sampleRate = sampleRate;
        bucketFmt = String.format("%%.%df", histogramBucketNamePrecision);
    }

    /**
     * Create a StatsD reporter using default option values
     * @param statsd a DogStatsD client
     */
    public StatsdReporter(StatsDClient statsd) {
        this(statsd, DEFAULT_SAMPLE_RATE, DEFAULT_HISTOGRAM_BUCKET_NAME_PRECISION);
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
        statsdClient.stop();
    }

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        statsdClient.count(name, value, sampleRate, adaptTags(tags));
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        statsdClient.gauge(name, value, sampleRate, adaptTags(tags));
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        // We don't support tags for StatsD
        statsdClient.time(name, interval.toMillis(), sampleRate, adaptTags(tags));
    }

    @Override
    public void reportHistogramValueSamples(
        String name,
        Map<String, String> tags,
        Buckets buckets,
        double bucketLowerBound,
        double bucketUpperBound,
        long samples
    ) {
        statsdClient.count(
            bucketString(
                name,
                valueBucketString(bucketLowerBound),
                valueBucketString(bucketUpperBound)
            ),
            samples,
            sampleRate,
            adaptTags(tags)
        );
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
        // We don't support tags for StatsD
        statsdClient.count(
            bucketString(
                name,
                durationBucketString(bucketLowerBound),
                durationBucketString(bucketUpperBound)
            ),
            samples,
            sampleRate,
            adaptTags(tags)
        );
    }

    private String bucketString(String name, String lowerBound, String upperBound) {
        return String.format("%s.%s-%s", name, lowerBound, upperBound);
    }

    private String valueBucketString(double bucketBound) {
        if (bucketBound == Double.MAX_VALUE) {
            return "infinity";
        }

        if (bucketBound == -Double.MAX_VALUE) {
            return "-infinity";
        }

        return String.format(bucketFmt, bucketBound);
    }

    private String durationBucketString(Duration bucketBound) {
        if (Duration.MAX_VALUE.equals(bucketBound)) {
            return "infinity";
        }

        if (Duration.MIN_VALUE.equals(bucketBound)) {
            return "-infinity";
        }

        return bucketBound.toString();
    }

    private String[] adaptTags(Map<String, String> tags) {
        if (tags == null) {
            return null;
        }
        return tags.entrySet()
          .stream()
          .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
          .toArray(String[]::new);
    }
}
