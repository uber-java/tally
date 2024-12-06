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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MonotonicClockFakeClockTest {

    @Test
    public void testAddNanos() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();

        assertEquals(0, clock.nowNanos());
        clock.addNanos(100);
        assertEquals(100, clock.nowNanos());
    }

    @Test
    public void testAddDuration() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();

        assertEquals(0, clock.nowNanos());
        clock.addDuration(Duration.ofMillis(10));
        assertEquals(10_000_000, clock.nowNanos());
    }

    @Test
    public void testAutoAdvanceNanos() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();

        clock.autoAdvanceNanos(10);
        assertEquals(10, clock.nowNanos());
        assertEquals(20, clock.nowNanos());
        assertEquals(30, clock.nowNanos());

        clock.autoAdvanceNanos(20);
        assertEquals(50, clock.nowNanos());
        assertEquals(70, clock.nowNanos());
    }

    @Test
    public void testAutoAdvanceDuration() {
        MonotonicClock.FakeClock clock = MonotonicClock.fake();

        clock.autoAdvanceDuration(Duration.ofMillis(10));
        assertEquals(10_000_000, clock.nowNanos());
        assertEquals(20_000_000, clock.nowNanos());
        assertEquals(30_000_000, clock.nowNanos());

        clock.autoAdvanceDuration(Duration.ofMillis(20));
        assertEquals(50_000_000, clock.nowNanos());
        assertEquals(70_000_000, clock.nowNanos());
    }
}
