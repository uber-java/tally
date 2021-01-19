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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GaugeImplTest {
    private final double EPSILON = 1e-10;

    private TestStatsReporter reporter;
    private GaugeImpl gauge;

    private ScopeImpl scope;

    @Before
    public void setUp() {
        reporter = new TestStatsReporter();
        scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(reporter)
                .build();

        gauge = new GaugeImpl(scope, "gauge");
    }

    @Test
    public void update() {
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
        assertEquals(0, gauge.value(), EPSILON);

        gauge.update(55);
        assertEquals(55, gauge.value(), EPSILON);

        gauge.update(60);
        gauge.update(61);
        assertEquals(61, gauge.value(), EPSILON);
    }

    @Test
    public void snapshot() {
        gauge.update(70);
        assertEquals(70, gauge.snapshot(), EPSILON);

        gauge.update(71);
        gauge.update(72);
        assertEquals(72, gauge.snapshot(), EPSILON);
    }
}
