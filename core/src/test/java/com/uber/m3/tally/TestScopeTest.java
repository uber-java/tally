// Copyright (c) 2023 Uber Technologies, Inc.
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
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class TestScopeTest {

    @Test
    public void testCreate() {
        TestScope testScope = TestScope.create();
        assertNotNull(testScope);
        assertThat(testScope, instanceOf(Scope.class));

        assertNotNull(testScope.capabilities());
        assertFalse(testScope.capabilities().reporting());
        assertFalse(testScope.capabilities().tagging());

        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");

        testScope.tagged(tags).counter("counter").inc(1);

        testScope.counter("untagged_counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(2, counters.size());

        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("counter", tags));
        assertNotNull(counterSnapshot);

        assertEquals("counter", counterSnapshot.name());
        assertEquals(tags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());

        counterSnapshot = counters.get(new ScopeKey("untagged_counter", ImmutableMap.EMPTY));
        assertNotNull(counterSnapshot);
        assertEquals("untagged_counter", counterSnapshot.name());
        assertEquals(ImmutableMap.EMPTY, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void createWithPrefixAndTags() {
        Map<String, String> tags = ImmutableMap.of("key", "value");
        TestScope testScope = TestScope.create("prefix", tags);
        testScope.tagged(ImmutableMap.of("other_key", "other_value")).counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value", "other_key", "other_value");
        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("prefix.counter", totalTags));

        assertNotNull(counterSnapshot);
        assertEquals("prefix.counter", counterSnapshot.name());
        assertEquals(totalTags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void testCreateWithTagsAndSubscope() {
        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");
        TestScope testScope = TestScope.create("", tags);

        ImmutableMap<String, String> subScopeTags = ImmutableMap.of("key", "other_value");
        testScope.tagged(subScopeTags).subScope("subscope").counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("subscope.counter", subScopeTags));
        assertNotNull(counterSnapshot);

        assertEquals("subscope.counter", counterSnapshot.name());
        assertEquals(subScopeTags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void testCreateWithClock() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        TestScope testScope = TestScope.create(clock);

        Stopwatch stopwatch = testScope.histogram("histogram", buckets).start();
        clock.addDuration(Duration.ofMillis(15));
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, HistogramSnapshot> histograms = snapshot.histograms();
        assertNotNull(histograms);
        assertEquals(1, histograms.size());

        HistogramSnapshot histogramSnapshot = histograms.get(new ScopeKey("histogram", ImmutableMap.EMPTY));

        assertNotNull(histogramSnapshot);
        assertEquals("histogram", histogramSnapshot.name());
        assertEquals(1, histogramSnapshot.durations().get(Duration.ofMillis(20)).longValue());
    }

    @Test
    public void testCreateWithTagsAndClock() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);
        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        TestScope testScope = TestScope.create("prefix", tags, clock);

        Stopwatch stopwatch = testScope.histogram("histogram", buckets).start();
        clock.addDuration(Duration.ofMillis(25));
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, HistogramSnapshot> histograms = snapshot.histograms();
        assertNotNull(histograms);
        assertEquals(1, histograms.size());

        HistogramSnapshot histogramSnapshot = histograms.get(new ScopeKey("prefix.histogram", tags));

        assertNotNull(histogramSnapshot);
        assertEquals("prefix.histogram", histogramSnapshot.name());
        assertEquals(1, histogramSnapshot.durations().get(Duration.ofMillis(30)).longValue());
    }

    @Test
    public void testCreateWithClockAndTimer() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        TestScope testScope = TestScope.create(clock);

        Stopwatch stopwatch = testScope.timer("timer").start();
        clock.addNanos(10);
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, TimerSnapshot> timers = snapshot.timers();
        assertNotNull(timers);
        assertEquals(1, timers.size());

        TimerSnapshot timerSnapshot = timers.get(new ScopeKey("timer", ImmutableMap.EMPTY));

        assertNotNull(timerSnapshot);
        assertEquals("timer", timerSnapshot.name());
        assertEquals(1, timerSnapshot.values().length);
        assertEquals(Duration.ofNanos(10), timerSnapshot.values()[0]);
    }

    @Test
    public void testCreateWithTagsAndClockAndTimer() {
        ImmutableMap<String, String> tags = ImmutableMap.of("key", "value");
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        TestScope testScope = TestScope.create("prefix", tags, clock);
        Scope tagged = testScope.tagged(ImmutableMap.of("other_key", "other_value"));

        Stopwatch stopwatch = tagged.timer("timer").start();
        clock.addNanos(10);
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, TimerSnapshot> timers = snapshot.timers();
        assertNotNull(timers);
        assertEquals(1, timers.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value", "other_key", "other_value");
        TimerSnapshot timerSnapshot = timers.get(new ScopeKey("prefix.timer", totalTags));

        assertNotNull(timerSnapshot);
        assertEquals("prefix.timer", timerSnapshot.name());
        assertEquals(totalTags, timerSnapshot.tags());
        assertEquals(1, timerSnapshot.values().length);
        assertEquals(Duration.ofNanos(10), timerSnapshot.values()[0]);
    }

    @Test
    public void testCounterSnapshot() {
        TestScope testScope = TestScope.create("prefix", ImmutableMap.EMPTY);
        testScope.tagged(ImmutableMap.of("key", "value")).counter("counter").inc(1);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, CounterSnapshot> counters = snapshot.counters();
        assertNotNull(counters);
        assertEquals(1, counters.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value");
        CounterSnapshot counterSnapshot = counters.get(new ScopeKey("prefix.counter", totalTags));

        assertNotNull(counterSnapshot);
        assertEquals("prefix.counter", counterSnapshot.name());
        assertEquals(totalTags, counterSnapshot.tags());
        assertEquals(1, counterSnapshot.value());
    }

    @Test
    public void testTimerSnapshot() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        TestScope testScope = TestScope.create("prefix", ImmutableMap.EMPTY, clock);
        Scope subscope = testScope.tagged(ImmutableMap.of("key", "value"));

        Timer timer = subscope.timer("timer");

        // Test record directly.
        timer.record(Duration.ofNanos(100));

        // Test record via stopwatch.
        Stopwatch stopwatch = timer.start();
        clock.addNanos(10);
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, TimerSnapshot> timers = snapshot.timers();
        assertNotNull(timers);
        assertEquals(1, timers.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value");
        TimerSnapshot timerSnapshot = timers.get(new ScopeKey("prefix.timer", totalTags));

        assertNotNull(timerSnapshot);
        assertEquals("prefix.timer", timerSnapshot.name());
        assertEquals(totalTags, timerSnapshot.tags());
        assertArrayEquals(new Duration[]{Duration.ofNanos(100), Duration.ofNanos(10)}, timerSnapshot.values());
    }

    @Test
    public void testHistogramSnapshot() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 5);
        TestScope testScope = TestScope.create("prefix", ImmutableMap.EMPTY);
        Scope subscope = testScope.tagged(ImmutableMap.of("key", "value"));

        Histogram histogram = subscope.histogram("histogram", buckets);
        // Test record directly.
        histogram.recordDuration(Duration.ofMillis(15));

        // Test record via stopwatch.
        Stopwatch stopwatch = histogram.start();
        clock.addDuration(Duration.ofMillis(25));
        stopwatch.stop();

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, HistogramSnapshot> histogramSnapshots = snapshot.histograms();
        assertNotNull(histogramSnapshots);
        assertEquals(1, histogramSnapshots.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value");
        HistogramSnapshot histogramSnapshot = histogramSnapshots.get(new ScopeKey("prefix.histogram", totalTags));

        assertNotNull(histogramSnapshot);
        assertEquals("prefix.histogram", histogramSnapshot.name());
        assertEquals(totalTags, histogramSnapshot.tags());

        // Total of 2 samples.
        assertEquals(2, histogramSnapshot.durations().values().stream().mapToLong(it -> it).sum());
        assertEquals(1, histogramSnapshot.durations().get(Duration.ofMillis(20)).longValue());
        assertEquals(1, histogramSnapshot.durations().get(Duration.ofMillis(10)).longValue());
    }

    @Test
    public void testGaugeSnapshot() {
        TestScope testScope = TestScope.create("prefix", ImmutableMap.EMPTY);
        Scope subscope = testScope.tagged(ImmutableMap.of("key", "value"));

        subscope.gauge("gauge").update(20.0);

        Snapshot snapshot = testScope.snapshot();
        assertNotNull(snapshot);

        Map<ScopeKey, GaugeSnapshot> gauge = snapshot.gauges();
        assertNotNull(gauge);
        assertEquals(1, gauge.size());

        ImmutableMap<String, String> totalTags = ImmutableMap.of("key", "value");
        GaugeSnapshot gaugeSnapshot = gauge.get(new ScopeKey("prefix.gauge", totalTags));

        assertNotNull(gaugeSnapshot);
        assertEquals("prefix.gauge", gaugeSnapshot.name());
        assertEquals(totalTags, gaugeSnapshot.tags());

        assertEquals(20.0, gaugeSnapshot.value(), 0.01);
    }
}

