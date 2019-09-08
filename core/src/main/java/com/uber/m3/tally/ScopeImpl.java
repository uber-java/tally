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

import com.uber.m3.tally.sanitizers.Sanitizer;
import com.uber.m3.util.ImmutableMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Default {@link Scope} implementation.
 */
class ScopeImpl implements Scope, TestScope {
    private final StatsReporter reporter;
    private final String prefix;
    private final String separator;
    private final ImmutableMap<String, String> tags;
    private final Buckets defaultBuckets;
    private final Sanitizer sanitizer;

    private final ScheduledExecutorService scheduler;
    private final Registry registry;

    // ConcurrentHashMap nearly always allowing read operations seems like a good
    // performance upside to the consequence of reporting a newly-made metric in
    // the middle of looping and reporting through all metrics. Therefore, we only
    // synchronize on these maps when having to allocate new metrics.
    private final Map<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final Map<String, GaugeImpl> gauges = new ConcurrentHashMap<>();
    private final Map<String, TimerImpl> timers = new ConcurrentHashMap<>();
    private final Map<String, HistogramImpl> histograms = new ConcurrentHashMap<>();

    // Private ScopeImpl constructor. Root scopes should be built using the RootScopeBuilder class
    ScopeImpl(ScheduledExecutorService scheduler, Registry registry, ScopeBuilder builder) {
        this.scheduler = scheduler;
        this.registry = registry;

        this.sanitizer = builder.sanitizer;
        this.reporter = builder.reporter;
        this.prefix = this.sanitizer.name(builder.prefix);
        this.separator = this.sanitizer.name(builder.separator);
        this.tags = copyAndSanitizeMap(builder.tags);
        this.defaultBuckets = builder.defaultBuckets;
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

    @Override
    public Counter counter(String name) {
        name = sanitizer.name(name);
        CounterImpl counter = counters.get(name);

        if (counter != null) {
            return counter;
        }

        counters.putIfAbsent(name, new CounterImpl());
        return counters.get(name);
    }

    @Override
    public Gauge gauge(String name) {
        name = sanitizer.name(name);
        GaugeImpl gauge = gauges.get(name);

        if (gauge != null) {
            return gauge;
        }

        gauges.putIfAbsent(name, new GaugeImpl());
        return gauges.get(name);

    }

    @Override
    public Timer timer(String name) {
        name = sanitizer.name(name);
        TimerImpl timer = timers.get(name);

        if (timer != null) {
            return timer;
        }

        timers.putIfAbsent(name, new TimerImpl(fullyQualifiedName(name), tags, reporter));
        return timers.get(name);

    }

    @Override
    public Histogram histogram(String name, Buckets buckets) {
        name = sanitizer.name(name);
        if (buckets == null) {
            buckets = defaultBuckets;
        }

        HistogramImpl histogram = histograms.get(name);

        if (histogram != null) {
            return histogram;
        }

        histograms.putIfAbsent(name, new HistogramImpl(fullyQualifiedName(name), tags, reporter, buckets));
        return histograms.get(name);
    }

    @Override
    public Scope tagged(Map<String, String> tags) {
        return subScopeHelper(prefix, copyAndSanitizeMap(tags));
    }

    @Override
    public Scope subScope(String name) {
        name = sanitizer.name(name);
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

    /**
     * Reports using the specified reporter.
     *
     * @param reporter the reporter to report
     */
    private void report(StatsReporter reporter) {
        for (Map.Entry<String, CounterImpl> counter : counters.entrySet()) {
            counter.getValue().report(fullyQualifiedName(counter.getKey()), tags, reporter);
        }

        for (Map.Entry<String, GaugeImpl> gauge : gauges.entrySet()) {
            gauge.getValue().report(fullyQualifiedName(gauge.getKey()), tags, reporter);
        }

        // No operations on timers required here; they report directly to the StatsReporter
        // i.e. they are not buffered

        for (Map.Entry<String, HistogramImpl> histogram : histograms.entrySet()) {
            histogram.getValue().report(fullyQualifiedName(histogram.getKey()), tags, reporter);
        }

        reporter.flush();
    }

    private String fullyQualifiedName(String name) {
        if (prefix == null || prefix.length() == 0) {
            return name;
        }

        return String.format("%s%s%s", prefix, separator, name);
    }

    /**
     * Returns a {@link Snapshot} of this {@link Scope}.
     *
     * @return a {@link Snapshot} of this {@link Scope}
     */
    @Override
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

    private ImmutableMap<String, String> copyAndSanitizeMap(Map<String, String> tags) {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        if (tags != null) {
            tags.forEach((key, value) -> builder.put(sanitizer.key(key), sanitizer.value(value)));
        }
        return builder.build();
    }

    // Helper function used to create subscopes
    private Scope subScopeHelper(String prefix, ImmutableMap<String, String> tags) {
        ImmutableMap<String, String> mergedTags = this.tags;
        if (tags != null) {
            ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
            builder.putAll(this.tags);
            // New tags override old tags
            builder.putAll(tags);
            mergedTags = builder.build();
        }

        String key = keyForPrefixedStringMap(prefix, mergedTags);

        registry.subscopes.putIfAbsent(
            key,
            new ScopeBuilder(scheduler, registry)
                .reporter(reporter)
                .prefix(prefix)
                .separator(separator)
                .tags(mergedTags)
                .defaultBuckets(defaultBuckets)
                .sanitizer(sanitizer)
                .build()
        );
        return registry.subscopes.get(key);
    }

    // One iteration of reporting this scope and all its subscopes
    private void reportLoopIteration() {
        Collection<ScopeImpl> subscopes = registry.subscopes.values();

        if (reporter != null) {
            for (ScopeImpl subscope : subscopes) {
                subscope.report(reporter);
            }
        }
    }

    static class Registry {
        Map<String, ScopeImpl> subscopes = new ConcurrentHashMap<>();
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
}
