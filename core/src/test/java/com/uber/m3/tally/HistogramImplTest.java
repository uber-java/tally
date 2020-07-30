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
import com.uber.m3.util.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HistogramImplTest {

    private static final double[] BUCKETS = new double[] {
        1.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 125.0, 150.0, 175.0, 200.0,
        225.0, 250.0, 300.0, 350.0, 400.0, 450.0, 500.0, 550.0, 600.0, 650.0, 700.0, 750.0, 800.0, 850.0, 900.0, 950.0, 1000.0
    };

    private TestStatsReporter reporter;
    private ScopeImpl scope;

    private HistogramImpl histogram;

    @Before
    public void setUp() {
        reporter = new TestStatsReporter();
        scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(reporter)
                .build();
    }

    @Test
    public void test() {
        histogram = new HistogramImpl(scope, "histogram", ImmutableMap.EMPTY, ValueBuckets.custom(BUCKETS));

        double maxUpperBound = BUCKETS[BUCKETS.length - 1];

        List<Double> values =
                IntStream.range(0, 10000)
                    .mapToDouble(ignored -> Math.random() * (maxUpperBound + 1))
                    .mapToObj(a -> a)
                    .collect(Collectors.toList());

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

        histogram =
            new HistogramImpl(
                scope,
            "",
            null,
            buckets
            );

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

        assertEquals(new Long(3L), reporter.getValueSamples().get(10d));
        assertEquals(new Long(5L), reporter.getValueSamples().get(60d));
        assertEquals(buckets, reporter.getBuckets());
    }

    @Test
    public void recordDuration() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);

        histogram =
            new HistogramImpl(
                scope,
            "",
            null,
            buckets
            );

        for (int i = 0; i < 3; i++) {
            histogram.recordDuration(Duration.ofMillis(Math.random() * 10));
        }

        for (int i = 0; i < 5; i++) {
            histogram.recordDuration(Duration.ofMillis(50).add(Duration.ofMillis(Math.random() * 10)));
        }

        // Report will actually be fulfilled through scope
        scope.report(reporter);

        assertEquals(new Long(3L), reporter.getDurationSamples().get(Duration.ofMillis(10)));
        assertEquals(new Long(5L), reporter.getDurationSamples().get(Duration.ofMillis(60)));
        assertEquals(buckets, reporter.getBuckets());
    }

    @Test
    public void recordStopwatch() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 10);

        histogram =
            new HistogramImpl(
                scope,
            "",
            null,
            buckets
            );

        Stopwatch stopwatch = histogram.start();
        assertNotNull(stopwatch);
        stopwatch.stop();

        // Report will actually be fulfilled through scope
        scope.report(reporter);

        assertEquals(new Long(1L), reporter.getDurationSamples().get(Duration.ofMillis(10)));
    }

    @Test
    public void snapshotValues() {
        Buckets buckets = ValueBuckets.linear(0, 10, 10);

        histogram =
            new HistogramImpl(
                scope,
            "",
            null,
            buckets
            );

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

        assertEquals(expectedMap, histogram.snapshotValues());
    }

    @Test
    public void snapshotDurations() {
        Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 5);

        histogram =
            new HistogramImpl(
                scope,
            "",
            null,
            buckets
            );

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

        assertEquals(expectedMap, histogram.snapshotDurations());
    }
}
