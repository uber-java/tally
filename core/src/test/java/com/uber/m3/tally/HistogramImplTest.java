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
import com.uber.m3.util.ImmutableMap;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.uber.m3.tally.ScopeImpl.keyForPrefixedStringMap;
import static org.junit.Assert.assertEquals;

public class HistogramImplTest {

    private static final double[] BUCKETS = new double[]{
        1.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 125.0, 150.0, 175.0, 200.0,
        225.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0, 550.0, 600.0, 650.0, 700.0, 750.0, 800.0, 850.0, 900.0, 950.0, 1000.0
    };

    private final MonotonicClock.FakeClock clock = MonotonicClock.fake();
    private final TestStatsReporter reporter = new TestStatsReporter();
    private final ScopeImpl scope = new ScopeBuilder(null, new ScopeImpl.Registry()).clock(clock).reporter(reporter).build();

    @Test
    public void test() {
        HistogramImpl histogram = new HistogramImpl(clock, scope, "histogram", ImmutableMap.EMPTY, ValueBuckets.custom(BUCKETS));

        double maxUpperBound = BUCKETS[BUCKETS.length - 1];

        List<Double> values = IntStream.range(0, 10000).mapToDouble(ignored -> Math.random() * (maxUpperBound + 1)).boxed().collect(Collectors.toList());

        Map<Double, Long> expected = new HashMap<>();

        for (int i = 0; i < values.size(); ++i) {
            int index = Arrays.binarySearch(BUCKETS, values.get(i));
            double upper;
            if (index >= 0) {
                upper = index + 1 < BUCKETS.length ? BUCKETS[index + 1] : Double.MAX_VALUE;
            } else {
                upper = ~index < BUCKETS.length ? BUCKETS[~index] : Double.MAX_VALUE;
            }

            expected.put(upper, expected.getOrDefault(upper, 0L) + 1);

            ////////////////////////////////////////////////////

            histogram.recordValue(values.get(i));

            if (i % 13 == 0) {
                scope.report(reporter);
            }

        }

        scope.report(reporter);

        assertEquals(reporter.getCumulativeValueSamples(), expected);
    }

    @Test
    public void recordValue() {
        Buckets buckets = ValueBuckets.linear(0, 10, 10);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "", null, buckets);

        List<Double> log = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            double val = Math.random() * 10;
            log.add(val);
            histogram.recordValue(val);
        }

        for (int i = 0; i < 5; i++) {
            double val = 50 + Math.random() * 10;
            log.add(val);
            histogram.recordValue(val);
        }

        // Report will actually be fulfilled through scope
        scope.report(reporter);

        assertEquals(Long.valueOf(3L), reporter.getValueSamples().get(10d));
        assertEquals(Long.valueOf(5L), reporter.getValueSamples().get(60d));
        assertEquals(buckets, reporter.getBuckets());
    }

    @Test
    public void recordDuration() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "", null, buckets);

        for (int i = 0; i < 3; i++) {
            histogram.recordDuration(Duration.ofMillis(Math.random() * 10));
        }

        for (int i = 0; i < 5; i++) {
            histogram.recordDuration(Duration.ofMillis(50).add(Duration.ofMillis(Math.random() * 10)));
        }

        // Report will actually be fulfilled through scope
        scope.report(reporter);

        assertEquals(Long.valueOf(3L), reporter.getDurationSamples().get(Duration.ofMillis(10)));
        assertEquals(Long.valueOf(5L), reporter.getDurationSamples().get(Duration.ofMillis(60)));
        assertEquals(buckets, reporter.getBuckets());
    }

    @Test
    public void recordStopwatch() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "", null, buckets);

        Stopwatch stopwatch = histogram.start();
        clock.addDuration(Duration.ofMillis(5));
        stopwatch.stop();

        // Report will actually be fulfilled through scope
        scope.report(reporter);

        assertEquals(Long.valueOf(1L), reporter.getDurationSamples().get(Duration.ofMillis(10)));
    }

    @Test
    public void snapshotValues() {
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(new SnapshotBasedStatsReporter())
                .build();
        Buckets buckets = ValueBuckets.linear(0, 10, 10);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "histogram", null, buckets);

        for (int i = 0; i < 3; i++) {
            histogram.recordValue(Math.random() * 10);
        }

        for (int i = 0; i < 5; i++) {
            histogram.recordValue(50 + Math.random() * 10);
        }

        HashMap<Double, Long> expectedMap = new HashMap<>(buckets.size(), 1);
        expectedMap.put(0d, 0L);
        expectedMap.put(10d, 3L);
        expectedMap.put(20d, 0L);
        expectedMap.put(30d, 0L);
        expectedMap.put(40d, 0L);
        expectedMap.put(50d, 0L);
        expectedMap.put(60d, 5L);
        expectedMap.put(70d, 0L);
        expectedMap.put(80d, 0L);
        expectedMap.put(90d, 0L);
        expectedMap.put(Double.MAX_VALUE, 0L);

        Snapshot snapshot = scope.snapshot();
        assertEquals(expectedMap, getSnapshot(snapshot, "histogram").values());
    }

    @Test
    public void snapshotValuesIsIdempotent() {
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(new SnapshotBasedStatsReporter())
                .build();
        ValueBuckets buckets = ValueBuckets.custom(0, 10);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "histogram", null, buckets);

        histogram.recordValue(5);
        Snapshot snapshot1 = scope.snapshot();

        assertEquals(1, getSnapshot(snapshot1, "histogram").values().get(10D).longValue());

        // Snapshot again to ensure the first snapshot didn't mutate internal state.
        Snapshot snapshot2 = scope.snapshot();
        assertEquals(1, getSnapshot(snapshot2, "histogram").values().get(10D).longValue());
    }

    @Test
    public void snapshotDurations() {
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(new SnapshotBasedStatsReporter())
                .build();
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 5);
        HistogramImpl histogram = new HistogramImpl(clock, scope, "histogram", null, buckets);

        for (int i = 0; i < 3; i++) {
            histogram.recordDuration(Duration.ofMillis(Math.random() * 10));
        }

        for (int i = 0; i < 5; i++) {
            histogram.recordDuration(Duration.ofMillis(50).add(Duration.ofMillis(Math.random() * 10)));
        }

        HashMap<Duration, Long> expectedMap = new HashMap<>(buckets.size(), 1);
        expectedMap.put(Duration.ZERO, 0L);
        expectedMap.put(Duration.ofMillis(10d), 3L);
        expectedMap.put(Duration.ofMillis(20d), 0L);
        expectedMap.put(Duration.ofMillis(30d), 0L);
        expectedMap.put(Duration.ofMillis(40d), 0L);
        expectedMap.put(Duration.MAX_VALUE, 5L);

        Snapshot snapshot = scope.snapshot();
        assertEquals(expectedMap, getSnapshot(snapshot, "histogram").durations());
    }

    @Test
    public void snapshotDurationsIsIdempotent() {
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(new SnapshotBasedStatsReporter())
                .build();
        DurationBuckets buckets = DurationBuckets.custom(Duration.ofMillis(0), Duration.ofMillis(10));
        HistogramImpl histogram = new HistogramImpl(clock, scope, "histogram", null, buckets);

        histogram.recordDuration(Duration.ofMillis(5));
        Snapshot snapshot1 = scope.snapshot();
        assertEquals(1, getSnapshot(snapshot1, "histogram").durations().get(Duration.ofMillis(10)).longValue());

        // Snapshot again to ensure the first snapshot didn't mutate internal state.
        Snapshot snapshot2 = scope.snapshot();
        assertEquals(1, getSnapshot(snapshot2, "histogram").durations().get(Duration.ofMillis(10)).longValue());
    }

    private static HistogramSnapshot getSnapshot(Snapshot snapshot, String name) {
        ScopeKey key = keyForPrefixedStringMap(name, ImmutableMap.EMPTY);
        return snapshot.histograms().get(key);
    }
}
