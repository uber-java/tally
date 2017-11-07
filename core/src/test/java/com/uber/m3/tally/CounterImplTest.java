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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CounterImplTest {
    private TestStatsReporter reporter;
    private CounterImpl counter;

    @Before
    public void setUp() {
        reporter = new TestStatsReporter();
        counter = new CounterImpl();
    }

    @Test
    public void inc() {
        counter.inc(1);
        counter.report("", null, reporter);
        assertEquals(1, reporter.nextCounterVal());

        counter.inc(1);
        counter.report("", null, reporter);
        assertEquals(1, reporter.nextCounterVal());

        counter.inc(1);
        counter.inc(1);
        counter.report("", null, reporter);
        assertEquals(2, reporter.nextCounterVal());

        counter.inc(3);
        counter.report("", null, reporter);
        assertEquals(3, reporter.nextCounterVal());

        counter.inc(1);
        counter.inc(-3);
        counter.report("", null, reporter);
        assertEquals(-2, reporter.nextCounterVal());
    }

    @Test
    public void value() {
        assertEquals(0, counter.value());

        counter.inc(10);
        assertEquals(10, counter.value());
        assertEquals(0, counter.value());

        counter.inc(10);
        counter.inc(10);
        assertEquals(20, counter.value());
        assertEquals(0, counter.value());
    }

    @Test
    public void snapshot() {
        assertEquals(0, counter.snapshot());

        counter.inc(1);
        assertEquals(1, counter.snapshot());
        assertEquals(1, counter.snapshot());

        counter.inc(1);
        counter.inc(1);
        assertEquals(3, counter.snapshot());
        assertEquals(3, counter.snapshot());
    }
}
