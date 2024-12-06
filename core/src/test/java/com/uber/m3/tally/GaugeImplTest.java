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
import org.junit.Test;

import static com.uber.m3.tally.ScopeImpl.keyForPrefixedStringMap;
import static org.junit.Assert.assertEquals;

public class GaugeImplTest {
    private static final double EPSILON = 1e-10;
    private final TestStatsReporter reporter = new TestStatsReporter();
    private final ScopeImpl scope = new ScopeBuilder(null, new ScopeImpl.Registry()).reporter(reporter).build();

    @Test
    public void update() {
        GaugeImpl gauge = new GaugeImpl(scope, "gauge");

        gauge.update(42);
        gauge.report(null, reporter);
        assertEquals(42, reporter.nextGaugeVal(), EPSILON);

        gauge.update(2);
        gauge.update(8);
        gauge.report(null, reporter);
        assertEquals(8, reporter.nextGaugeVal(), EPSILON);

        gauge.update(0);
        gauge.report(null, reporter);
        assertEquals(0, reporter.nextGaugeVal(), EPSILON);

        gauge.update(1);
        gauge.update(-3);
        gauge.report(null, reporter);
        assertEquals(-3, reporter.nextGaugeVal(), EPSILON);
    }

    @Test
    public void value() {
        GaugeImpl gauge = new GaugeImpl(scope, "gauge");

        assertEquals(0, gauge.value(), EPSILON);

        gauge.update(55);
        assertEquals(55, gauge.value(), EPSILON);

        gauge.update(60);
        gauge.update(61);
        assertEquals(61, gauge.value(), EPSILON);
    }

    @Test
    public void snapshot() {
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(new SnapshotBasedStatsReporter())
                .build();
        Gauge gauge = new GaugeImpl(scope, "gauge");

        gauge.update(70);
        Snapshot snapshot = scope.snapshot();
        assertEquals(70, getSnapshot(snapshot, "gauge").value(), EPSILON);

        gauge.update(71);
        gauge.update(72);
        Snapshot snapshot1 = scope.snapshot();

        assertEquals(72, getSnapshot(snapshot1, "gauge").value(), EPSILON);
    }

    private static GaugeSnapshot getSnapshot(Snapshot snapshot, String name) {
        ScopeKey key = keyForPrefixedStringMap(name, ImmutableMap.EMPTY);
        return snapshot.gauges().get(key);
    }
}
