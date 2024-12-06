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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class ScopeImplTest {
    private static final double EPSILON = 1e-10;
    private static final int REPORT_INTERVAL_MILLIS = 10;
    private static final int SLEEP_MILLIS = 20;

    @Test
    public void metricCreation() {
        Map<String, String> commonTags = new HashMap<>(1, 1);
        commonTags.put("key", "val");

        Scope scope = new RootScopeBuilder()
            .tags(commonTags)
            .reporter(new TestStatsReporter())
            .reportEvery(Duration.ofMinutes(1));

        Counter counter = scope.counter("new-counter");
        assertNotNull(counter);

        Counter sameCounter = scope.counter("new-counter");
        // Should be the same Counter object and not a new instance
        assertTrue(counter == sameCounter);

        Gauge gauge = scope.gauge("new-gauge");
        assertNotNull(gauge);

        Gauge sameGauge = scope.gauge("new-gauge");
        // Should be the same Gauge object and not a new instance
        assertTrue(gauge == sameGauge);

        Timer timer = scope.timer("new-timer");
        assertNotNull(timer);

        Timer sameTimer = scope.timer("new-timer");
        // Should be the same Timer object and not a new instance
        assertTrue(timer == sameTimer);

        Histogram histogram = scope.histogram(
            "new-histogram",
            DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(100), 6)
        );
        assertNotNull(histogram);

        Histogram histogramDefaultBuckets = scope.histogram(
            "new-histogram-default-buckets",
            null
        );
        assertNotNull(histogramDefaultBuckets);

        Histogram sameHistogram = scope.histogram("new-histogram", null);

        // Should be the same Histogram object and not a new instance
        assertTrue(histogram == sameHistogram);
    }

    @Test
    public void capabilities() {
        TestStatsReporter reporter = new TestStatsReporter();
        Scope scope = new RootScopeBuilder()
            .reporter(new TestStatsReporter())
            .reportEvery(Duration.ofMinutes(1));

        assertEquals(reporter.capabilities(), scope.capabilities());

        scope = new RootScopeBuilder()
            .reportEvery(Duration.ofMinutes(1));

        assertEquals(CapableOf.NONE, scope.capabilities());
    }

    @Test
    public void close() {
        TestStatsReporter reporter = new TestStatsReporter();

        // Construct scope using test reporter reporting every minute
        try (Scope scope = new RootScopeBuilder().reporter(reporter).reportEvery(Duration.ofMinutes(1))) {
            // Create a gauge, update it, and let the AutoCloseable interface
            // functionality close the scope right away.
            Gauge shortLifeGauge = scope.gauge("shortLifeGauge");
            shortLifeGauge.update(123);
        } catch (ScopeCloseException e) {
            System.err.println("Error closing scope: " + e.getMessage());
        }

        // Make sure the reporter received gauge update
        assertEquals(123, reporter.nextGaugeVal(), EPSILON);
    }

    @Test
    public void closeWithoutReporter() throws ScopeCloseException {
        try (Scope scope = new RootScopeBuilder().reportEvery(Duration.ofMinutes(1))) {
            // Create a gauge, update it, and let the AutoCloseable interface
            // functionality close the scope right away.
            Gauge shortLifeGauge = scope.gauge("shortLifeGauge");
            shortLifeGauge.update(123);
        }
    }

    @Test
    public void subscopes() {
        TestStatsReporter reporter = new TestStatsReporter();

        ImmutableMap<String, String> tags = new ImmutableMap.Builder<String, String>(2)
            .put("custom_tag", "some_val")
            .put("foo", "bar")
            .build();

        Scope rootScope = new RootScopeBuilder()
            .tags(tags)
            .reporter(reporter)
            .reportEvery(Duration.ofMillis(REPORT_INTERVAL_MILLIS));

        Counter rootCounter = rootScope.counter("root_counter");
        rootCounter.inc(20);

        Scope subscope = rootScope.subScope("inner");
        Counter subCounter = subscope.counter("sub_counter");
        subCounter.inc(25);

        Scope subsubscope = subscope.subScope("deeper");
        Gauge subsubGauge = subsubscope.gauge("sub_sub_gauge");
        subsubGauge.update(12.34);

        ImmutableMap<String, String> additionalTags =
            new ImmutableMap.Builder<String, String>(2)
                .put("new_key", "new_val")
                .put("baz", "quz")
                .build();
        Scope taggedSubscope = rootScope.tagged(additionalTags);
        Timer taggedTimer = taggedSubscope.timer("tagged_timer");
        taggedTimer.record(Duration.ofSeconds(6));

        try {
            Thread.sleep(SLEEP_MILLIS);
        } catch (InterruptedException e) {
            System.err.println("Interrupted while sleeping! Let's continue anyway...");
        }
        final Set<TestStatsReporter.MetricStruct<Long>> actualCounters = new HashSet<>();
        actualCounters.add(reporter.nextCounter());
        actualCounters.add(reporter.nextCounter());

        final Set<TestStatsReporter.MetricStruct<Long>> expectedCounters = new HashSet<>();
        expectedCounters.add(new TestStatsReporter.MetricStruct<>("root_counter", tags, 20L));
        expectedCounters.add(new TestStatsReporter.MetricStruct<>("inner.sub_counter", tags, 25L));

        assertEquals(expectedCounters, actualCounters);

        TestStatsReporter.MetricStruct<Double> gauge = reporter.nextGauge();
        assertEquals("inner.deeper.sub_sub_gauge", gauge.getName());
        assertEquals(tags, gauge.getTags());

        TestStatsReporter.MetricStruct<Duration> timer = reporter.nextTimer();
        assertEquals("tagged_timer", timer.getName());
        ImmutableMap<String, String> expectedTags =
            new ImmutableMap.Builder<String, String>(4)
                .putAll(tags)
                .putAll(additionalTags)
                .build();
        assertEquals(expectedTags, timer.getTags());
    }

    @Test
    public void snapshot() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        Scope rootScope = new RootScopeBuilder()
            .clock(clock)
            .reporter(new SnapshotBasedStatsReporter())
            .build();

        Counter counter = rootScope.counter("snapshot-counter");
        counter.inc(110);

        rootScope.gauge("snapshot-gauge").update(120);
        rootScope.gauge("snapshot-gauge2").update(220);
        rootScope.gauge("snapshot-gauge3").update(320);

        Timer timer = rootScope.timer("snapshot-timer");
        timer.record(Duration.ofMillis(130));

        Stopwatch stopwatch = timer.start();
        clock.addNanos(50);
        stopwatch.stop();

        Snapshot snapshot = ((ScopeImpl) rootScope).snapshot();

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertEquals(1, counters.size());
        CounterSnapshot counterSnapshotActual = counters.get(ScopeImpl.keyForPrefixedStringMap("snapshot-counter", null));
        assertEquals("snapshot-counter", counterSnapshotActual.name());
        assertEquals(ImmutableMap.EMPTY, counterSnapshotActual.tags());

        Map<ScopeKey, GaugeSnapshot> gauges = snapshot.gauges();
        assertEquals(3, gauges.size());
        GaugeSnapshot gaugeSnapshotActual = gauges.get(ScopeImpl.keyForPrefixedStringMap("snapshot-gauge", null));
        assertEquals("snapshot-gauge", gaugeSnapshotActual.name());
        assertEquals(ImmutableMap.EMPTY, gaugeSnapshotActual.tags());
        assertEquals(120, gaugeSnapshotActual.value(), EPSILON);

        GaugeSnapshot gaugeSnapshot2Actual = gauges.get(ScopeImpl.keyForPrefixedStringMap("snapshot-gauge2", null));
        assertEquals("snapshot-gauge2", gaugeSnapshot2Actual.name());
        assertEquals(ImmutableMap.EMPTY, gaugeSnapshot2Actual.tags());
        assertEquals(220, gaugeSnapshot2Actual.value(), EPSILON);

        GaugeSnapshot gaugeSnapshot3Actual = gauges.get(ScopeImpl.keyForPrefixedStringMap("snapshot-gauge3", null));
        assertEquals("snapshot-gauge3", gaugeSnapshot3Actual.name());
        assertEquals(ImmutableMap.EMPTY, gaugeSnapshot3Actual.tags());
        assertEquals(320, gaugeSnapshot3Actual.value(), EPSILON);

        Map<ScopeKey, TimerSnapshot> timers = snapshot.timers();
        assertEquals(1, timers.size());
        TimerSnapshot timerSnapshotActual = timers.get(ScopeImpl.keyForPrefixedStringMap("snapshot-timer", null));
        assertEquals("snapshot-timer", timerSnapshotActual.name());
        assertEquals(ImmutableMap.EMPTY, timerSnapshotActual.tags());
        assertEquals(Arrays.asList(Duration.ofMillis(130), Duration.ofNanos(50)), Arrays.asList(timerSnapshotActual.values()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveReportInterval() {
        new RootScopeBuilder().reportEvery(Duration.ofSeconds(-10));
    }

    @Test
    public void exceptionInReportLoop() throws ScopeCloseException, InterruptedException {
        AtomicInteger uncaughtExceptionReported = new AtomicInteger();
        ThrowingStatsReporter reporter = new ThrowingStatsReporter();
        UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> uncaughtExceptionReported.incrementAndGet();

        try (Scope scope = new RootScopeBuilder()
            .reporter(reporter)
            .reportEvery(Duration.ofMillis(REPORT_INTERVAL_MILLIS),
                uncaughtExceptionHandler)) {
            scope.counter("hi").inc(1);
            Thread.sleep(SLEEP_MILLIS);

            assertEquals(1, uncaughtExceptionReported.get());
            assertEquals(1, reporter.getNumberOfReportedMetrics());

            // Run again to verify it reports again.
            scope.counter("hi").inc(1);
            Thread.sleep(SLEEP_MILLIS);

            assertEquals(2, uncaughtExceptionReported.get());
            assertEquals(2, reporter.getNumberOfReportedMetrics());
        }
    }

    private static class ThrowingStatsReporter implements StatsReporter {
        private final AtomicInteger reported = new AtomicInteger();

        public int getNumberOfReportedMetrics() {
            return reported.get();
        }

        @Override
        public void reportCounter(String name, Map<String, String> tags, long value) {
            reported.incrementAndGet();
            throw new RuntimeException();
        }

        @Override
        public void reportGauge(String name, Map<String, String> tags, double value) {
            reported.incrementAndGet();
            throw new RuntimeException();
        }

        @Override
        public void reportTimer(String name, Map<String, String> tags, Duration interval) {
            reported.incrementAndGet();
            throw new RuntimeException();
        }

        @Override
        public void reportHistogramValueSamples(String name, Map<String, String> tags,
                                                Buckets buckets, double bucketLowerBound,
                                                double bucketUpperBound, long samples) {
            reported.incrementAndGet();
            throw new RuntimeException();
        }

        @Override
        public void reportHistogramDurationSamples(String name, Map<String, String> tags,
                                                   Buckets buckets, Duration bucketLowerBound,
                                                   Duration bucketUpperBound, long samples) {
            reported.incrementAndGet();
            throw new RuntimeException();
        }

        @Override
        public Capabilities capabilities() {
            return CapableOf.NONE;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
