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

package com.uber.m3.tally.prometheus;

import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.util.Duration;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class PrometheusReporter implements StatsReporter {

    private static final String PREFIX_SPLITTER = "+";
    private static final String KEY_PAIR_SPLITTER = ",";
    private static final String KEY_NAME_SPLITTER = "=";
    private static final String KEY_PAIR_TEMPLATE = "%s" + KEY_NAME_SPLITTER + "%s";
    static final String METRIC_ID_KEY_VALUE = "1";
    private static final TimerType DEFAULT_TIMER_TYPE = TimerType.SUMMARY;

    private final CollectorRegistry registry;
    private final TimerType timerType;
    // TODO: 07/01/2021 add default buckets
    private ConcurrentMap<String, Counter> registeredCounters;
    private ConcurrentMap<String, Gauge> registeredGauges;
    private ConcurrentMap<String, Histogram> registeredHistograms;
    private ConcurrentMap<String, Summary> registeredSummaries;


    public PrometheusReporter(TimerType defaultTimerType, CollectorRegistry registry) {
        this.registry = registry;
        this.timerType = defaultTimerType;
        this.registeredCounters = new ConcurrentHashMap<>();
        this.registeredGauges = new ConcurrentHashMap<>();
        this.registeredSummaries = new ConcurrentHashMap<>();
        this.registeredHistograms = new ConcurrentHashMap<>();
    }

    public PrometheusReporter(CollectorRegistry registry) {
        this(DEFAULT_TIMER_TYPE, registry);
    }

    public PrometheusReporter() {
        this(CollectorRegistry.defaultRegistry);
    }

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredCounters.containsKey(collectorName)) {
            Counter counter = Counter.build()
                    .name(name)
                    .help(String.format("%s counter", name))
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredCounters.put(collectorName, counter);
        }
        registeredCounters.get(collectorName).labels(collectionToStringArray(ttags.values())).inc(value);
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredGauges.containsKey(collectorName)) {
            Gauge gauge = Gauge.build()
                    .name(name)
                    .help(String.format("%s gauge", name))
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredGauges.put(collectorName, gauge);
        }
        registeredGauges.get(collectorName).labels(collectionToStringArray(ttags.values())).set(value);
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        switch (timerType) {
            case HISTOGRAM:
                reportTimerHistogram(name, tags, interval);
                break;
            case SUMMARY:
            default:
                reportTimerSummary(name, tags, interval);
        }
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
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredHistograms.containsKey(collectorName)) {
            Histogram histogram = Histogram.build()
                    .name(name)
                    .help(String.format("%s gauge", name))
                    // TODO: 07/01/2021 add buckets
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredHistograms.put(collectorName, histogram);
        }
        Histogram.Child histogram = registeredHistograms.get(collectorName).labels(collectionToStringArray(ttags.values()));
        for (int i = 0; i < samples; i++) {
            histogram.observe(bucketUpperBound);
        }
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
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredHistograms.containsKey(collectorName)) {
            Histogram histogram = Histogram.build()
                    .name(name)
                    .help(String.format("%s gauge", name))
                    // TODO: 07/01/2021 add buckets
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredHistograms.put(collectorName, histogram);
        }
        Histogram.Child histogram = registeredHistograms.get(collectorName)
                .labels(collectionToStringArray(ttags.values()));
        double bucketUpperBoundValue = bucketUpperBound.getSeconds();
        for (int i = 0; i < samples; i++) {
            histogram.observe(bucketUpperBoundValue);
        }
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING_TAGGING;
    }

    @Override
    public void flush() {
        // no-op flush does nothing for Prometheus reporter.
    }

    @Override
    public void close() {
        // TODO: 07/01/2021 deregister collectors from {@code registry}.
    }

    private void reportTimerSummary(String name, Map<String, String> tags, Duration interval) {
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredSummaries.containsKey(collectorName)) {
            Summary summary = Summary.build()
                    .name(name)
                    .help(String.format("%s summary", name))
                    // TODO: 07/01/2021 add quantiles and ageBuckets
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredSummaries.put(collectorName, summary);
        }
        registeredSummaries.get(collectorName)
                .labels(collectionToStringArray(ttags.values()))
                .observe(interval.getSeconds());
    }

    private void reportTimerHistogram(String name, Map<String, String> tags, Duration interval) {
        Map<String, String> ttags = tags;
        if (tags == null) {
            ttags = Collections.emptyMap();
        }
        String collectorName = canonicalMetricId(name, ttags.keySet());
        if (!registeredHistograms.containsKey(collectorName)) {
            Histogram histogram = Histogram.build()
                    .name(name)
                    .help(String.format("%s histogram", name))
                    // TODO: 07/01/2021 add buckets
                    .labelNames(collectionToStringArray(ttags.keySet()))
                    .register(registry);
            registeredHistograms.put(collectorName, histogram);
        }
        registeredHistograms.get(collectorName)
                .labels(collectionToStringArray(ttags.values()))
                .observe(interval.getSeconds());
    }

    /**
     * Generates a canonical MetricID for a given name+label keys, not values.
     *
     * @param name    metric name.
     * @param tagKeys label keys.
     * @return canonical metric ID in a form foo+tag1=1,tag2=1.
     */
    static String canonicalMetricId(String name, Set<String> tagKeys) {
        if (name == null) {
            throw new IllegalArgumentException("metric name cannot be null");
        }
        Map<String, String> tags;
        if (tagKeys == null) {
            tags = Collections.emptyMap();
        } else {
            tags = new HashMap<>(tagKeys.size());
            for (String key : tagKeys) {
                tags.put(key, METRIC_ID_KEY_VALUE);
            }
        }
        return keyForPrefixedStringMaps(name, Collections.singletonList(tags));
    }

    /**
     * Generates a unique key for a prefix and a list of maps containing tags.
     * <p>
     * If a key occurs in multiple maps, keys on the right take precedence.
     *
     * @param prefix prefix for unique key.
     * @param tags   list of maps containing tags.
     * @return unique key in a format prefix+key1=value1,key2=value2
     * @throws IllegalArgumentException when {@code prefix} is null.
     */
    static String keyForPrefixedStringMaps(String prefix, List<Map<String, String>> tags) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix cannot be null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(PREFIX_SPLITTER);
        if (tags != null) {
            Set<String> keys = new HashSet<>();
            for (Map<String, String> curTags : tags) {
                keys.addAll(curTags.keySet());
            }
            String tagsString = keys.stream()
                    .sorted()
                    .map(key -> {
                        for (int i = tags.size() - 1; i >= 0; i--) {
                            if (tags.get(i).containsKey(key)) {
                                return String.format(KEY_PAIR_TEMPLATE, key, tags.get(i).get(key));
                            }
                        }
                        // should never happen
                        return "";
                    })
                    .collect(Collectors.joining(KEY_PAIR_SPLITTER));
            sb.append(tagsString);
        }
        return sb.toString();
    }

    /**
     * Transforms a {@link Collection} of {@link String}s to an array of {@link String}s.
     *
     * @param values collection of {@link String}s.
     * @return an array of {@link String}s.
     */
    static String[] collectionToStringArray(Collection<String> values) {
        return values.stream().toArray(String[]::new);
    }
}
