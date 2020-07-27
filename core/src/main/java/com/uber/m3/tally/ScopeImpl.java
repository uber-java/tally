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

import com.uber.m3.util.ImmutableMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default {@link Scope} implementation.
 */
class ScopeImpl implements Scope {
    private StatsReporter reporter;
    private String prefix;
    private String separator;
    private ImmutableMap<String, String> tags;
    private Buckets defaultBuckets;

    private ScheduledExecutorService scheduler;
    private Registry registry;

    private final ConcurrentHashMap<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GaugeImpl> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HistogramImpl> histograms = new ConcurrentHashMap<>();

    // TODO validate if needs to be concurrent at all
    private final ConcurrentLinkedQueue<MetricBase> reportingQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<String, TimerImpl> timers = new ConcurrentHashMap<>();

    // Private ScopeImpl constructor. Root scopes should be built using the RootScopeBuilder class
    ScopeImpl(ScheduledExecutorService scheduler, Registry registry, ScopeBuilder builder) {
        this.scheduler = scheduler;
        this.registry = registry;

        this.reporter = builder.reporter;
        this.prefix = builder.prefix;
        this.separator = builder.separator;
        this.tags = builder.tags;
        this.defaultBuckets = builder.defaultBuckets;
    }

    @Override
    public Counter counter(String name) {
        return counters.computeIfAbsent(name, ignored ->
                // NOTE: This will called at most once
                addToReportingQueue(new CounterImpl(fullyQualifiedName(name)))
        );
    }

    @Override
    public Gauge gauge(String name) {
        return gauges.computeIfAbsent(name, ignored ->
                // NOTE: This will called at most once
                addToReportingQueue(new GaugeImpl(fullyQualifiedName(name))));
    }

    @Override
    public Timer timer(String name) {
        // Timers report directly to the {@code StatsReporter}, and therefore not added to reporting queue
        // i.e. they are not buffered
        return timers.computeIfAbsent(name, ignored -> new TimerImpl(fullyQualifiedName(name), tags, reporter));
    }

    @Override
    public Histogram histogram(String name, Buckets buckets) {
        return histograms.computeIfAbsent(name, ignored ->
                // NOTE: This will called at most once
                addToReportingQueue(new HistogramImpl(fullyQualifiedName(name), tags, buckets)));
    }

    @Override
    public Scope tagged(Map<String, String> tags) {
        return subScopeHelper(prefix, tags);
    }

    @Override
    public Scope subScope(String name) {
        return subScopeHelper(fullyQualifiedName(name), null);
    }

    @Override
    public Capabilities capabilities() {
        if (reporter != null) {
            return reporter.capabilities();
        }

        return CapableOf.NONE;
    }

    @Override
    public void close() {
        // First, stop periodic reporting of this scope
        scheduler.shutdown();

        // More metrics may have come in between the time of the last report by the
        // scheduler and the call to close this scope, so report once more to flush
        // all metrics.
        reportLoopIteration();

        if (reporter != null) {
            // Now that all metrics should be known to the reporter, close the reporter
            reporter.close();
        }
    }

    private <T extends MetricBase> T addToReportingQueue(T metric) {
        reportingQueue.add(metric);
        return metric;
    }

    /**
     * Reports using the specified reporter.
     * @param reporter the reporter to report
     */
    void report(StatsReporter reporter) {
        for (MetricBase metric : reportingQueue) {
            metric.report(metric.getQualifiedName(), tags, reporter);
        }
    }

    // Serializes a map to generate a key for a prefix/map combination
    // Non-generic EMPTY ImmutableMap will never contain any elements
    @SuppressWarnings("unchecked")
    static String keyForPrefixedStringMap(String prefix, ImmutableMap<String, String> stringMap) {
        if (prefix == null) {
            prefix = "";
        }

        if (stringMap == null) {
            stringMap = ImmutableMap.EMPTY;
        }

        Set<String> keySet = stringMap.keySet();
        String[] sortedKeys = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(sortedKeys);

        StringBuilder keyBuffer = new StringBuilder(prefix.length() + sortedKeys.length * 20);
        keyBuffer.append(prefix);
        keyBuffer.append("+");

        for (int i = 0; i < sortedKeys.length; i++) {
            keyBuffer.append(sortedKeys[i]);
            keyBuffer.append("=");
            keyBuffer.append(stringMap.get(sortedKeys[i]));

            if (i != sortedKeys.length - 1) {
                keyBuffer.append(",");
            }
        }

        return keyBuffer.toString();
    }

    String fullyQualifiedName(String name) {
        if (prefix == null || prefix.length() == 0) {
            return name;
        }

        return String.format("%s%s%s", prefix, separator, name);
    }

    /**
     * Returns a {@link Snapshot} of this {@link Scope}.
     * @return a {@link Snapshot} of this {@link Scope}
     */
    public Snapshot snapshot() {
        Snapshot snap = new SnapshotImpl();

        for (ScopeImpl subscope : registry.subscopes.values()) {
            for (Map.Entry<String, CounterImpl> counter : subscope.counters.entrySet()) {
                String name = subscope.fullyQualifiedName(counter.getKey());

                String id = keyForPrefixedStringMap(name, tags);

                snap.counters().put(
                    id,
                    new CounterSnapshotImpl(
                        name,
                        tags,
                        counter.getValue().snapshot()
                    )
                );
            }

            for (Map.Entry<String, GaugeImpl> gauge : subscope.gauges.entrySet()) {
                String name = subscope.fullyQualifiedName(gauge.getKey());

                String id = keyForPrefixedStringMap(name, tags);

                snap.gauges().put(
                    id,
                    new GaugeSnapshotImpl(
                        name,
                        tags,
                        gauge.getValue().snapshot()
                    )
                );
            }

            for (Map.Entry<String, TimerImpl> timer : subscope.timers.entrySet()) {
                String name = subscope.fullyQualifiedName(timer.getKey());

                String id = keyForPrefixedStringMap(name, tags);

                snap.timers().put(
                    id,
                    new TimerSnapshotImpl(
                        name,
                        tags,
                        timer.getValue().snapshot()
                    )
                );
            }

            for (Map.Entry<String, HistogramImpl> histogram : subscope.histograms.entrySet()) {
                String name = subscope.fullyQualifiedName(histogram.getKey());

                String id = keyForPrefixedStringMap(name, tags);

                snap.histograms().put(
                    id,
                    new HistogramSnapshotImpl(
                        name,
                        tags,
                        histogram.getValue().snapshotValues(),
                        histogram.getValue().snapshotDurations()
                    )
                );
            }
        }

        return snap;
    }

    // Helper function used to create subscopes
    private Scope subScopeHelper(String prefix, Map<String, String> tags) {
        ImmutableMap.Builder<String, String> mapBuilder = new ImmutableMap.Builder<>();

        if (this.tags != null) {
            mapBuilder.putAll(this.tags);
        }
        if (tags != null) {
            // New tags override old tag buckets
            mapBuilder.putAll(tags);
        }

        ImmutableMap<String, String> mergedTags = mapBuilder.build();

        String key = keyForPrefixedStringMap(prefix, mergedTags);

        return registry.subscopes.computeIfAbsent(
            key,
            (k) -> new ScopeBuilder(scheduler, registry)
                .reporter(reporter)
                .prefix(prefix)
                .separator(separator)
                .tags(mergedTags)
                .defaultBuckets(defaultBuckets)
                .build()
        );
    }

    // One iteration of reporting this scope and all its subscopes
    void reportLoopIteration() {
        Collection<ScopeImpl> subscopes = registry.subscopes.values();

        if (reporter != null) {
            for (ScopeImpl subscope : subscopes) {
                subscope.report(reporter);
            }

            reporter.flush();
        }
    }

    class ReportLoop implements Runnable {
        private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

        ReportLoop(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
            this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        }

        public void run() {
            try {
                reportLoopIteration();
            } catch (Exception uncaughtException) {
                if (uncaughtExceptionHandler != null) {
                    reportUncaughtException(uncaughtException);
                }
            }
        }

        private void reportUncaughtException(Exception uncaughtException) {
            try {
                uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), uncaughtException);
            } catch (Exception ignored) {
                // ignore exception
            }
        }
    }

    static class Registry {
        Map<String, ScopeImpl> subscopes = new ConcurrentHashMap<>();
    }
}
