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

import com.uber.m3.util.ImmutableMap;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final MonotonicClock clock;

    private final ScheduledExecutorService scheduler;
    private final Registry registry;

    private final ConcurrentHashMap<String, CounterImpl> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GaugeImpl> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HistogramImpl> histograms = new ConcurrentHashMap<>();

    private final CopyOnWriteArrayList<Reportable> reportingList = new CopyOnWriteArrayList<>();

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
        this.clock = builder.clock;
    }

    @Override
    public Counter counter(String name) {
        return counters.computeIfAbsent(name, ignored ->
            // NOTE: This will be called at most once
            new CounterImpl(this, fullyQualifiedName(name))
        );
    }

    @Override
    public Gauge gauge(String name) {
        return gauges.computeIfAbsent(name, ignored ->
            // NOTE: This will be called at most once
            new GaugeImpl(this, fullyQualifiedName(name)));
    }

    @Override
    public Timer timer(String name) {
        // Timers report directly to the {@code StatsReporter}, and therefore not added to reporting queue
        // i.e. they are not buffered
        return timers.computeIfAbsent(name, ignored -> new TimerImpl(clock, fullyQualifiedName(name), tags, reporter));
    }

    @Override
    public Histogram histogram(String name, @Nullable Buckets buckets) {
        return histograms.computeIfAbsent(name, ignored ->
            // NOTE: This will be called at most once
            new HistogramImpl(
                clock,
                this,
                fullyQualifiedName(name),
                tags,
                buckets != null ? buckets : defaultBuckets
            )
        );
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

    <T extends Reportable> void addToReportingQueue(T metric) {
        reportingList.add(metric);
    }

    /**
     * Reports using the specified reporter.
     *
     * @param reporter the reporter to report
     */
    void report(StatsReporter reporter) {
        for (Reportable metric : reportingList) {
            metric.report(tags, reporter);
        }
    }

    // Serializes a map to generate a key for a prefix/map combination
    // Non-generic EMPTY ImmutableMap will never contain any elements
    static ScopeKey keyForPrefixedStringMap(String prefix, ImmutableMap<String, String> stringMap) {
        return new ScopeKey(prefix, stringMap);
    }

    String fullyQualifiedName(String name) {
        if (prefix == null || prefix.isEmpty()) {
            return name;
        }

        return String.format("%s%s%s", prefix, separator, name);
    }

    /**
     * Snapshot returns a copy of all values since the last report execution.
     * This is an expensive operation and should only be used for testing purposes.
     *
     * @return a {@link Snapshot} of this {@link Scope}
     */
    @Override
    public Snapshot snapshot() {
        if (!(reporter instanceof SnapshotBasedStatsReporter)) {
            throw new IllegalStateException("Snapshots can only be computed when using a SnapshotBasedStatsReporter");
        }
        // We will create snapshots leveraging the reporting mechanism.
        // NOTE: Timers report directly to the reporter, so they are not handled by the report methods here.
        report(reporter);
        reportLoopIteration();
        return ((SnapshotBasedStatsReporter) reporter).getFlushedSnapshot();
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

        ScopeKey key = keyForPrefixedStringMap(prefix, mergedTags);

        return computeSubscopeIfAbsent(prefix, key, mergedTags);
    }

    // This method must only be called on unit tests or benchmarks
    protected Scope computeSubscopeIfAbsent(String prefix, ScopeKey key, ImmutableMap<String, String> mergedTags) {
        Scope scope = registry.subscopes.get(key);
        if (scope != null) {
            return scope;
        }

        return registry.subscopes.computeIfAbsent(
            key,
            (k) -> new ScopeBuilder(scheduler, registry)
                .clock(clock)
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
        final Map<ScopeKey, ScopeImpl> subscopes = new ConcurrentHashMap<>();
    }

}
