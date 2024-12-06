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

public class CounterImplTest {
    private final TestStatsReporter reporter = new TestStatsReporter();
    private final ScopeImpl scope = new ScopeBuilder(null, new ScopeImpl.Registry()).reporter(reporter).build();

    @Test
    public void inc() {
        CounterImpl counter = new CounterImpl(scope, "counter");

        counter.inc(1);
        counter.report(null, reporter);
        assertEquals(1, reporter.nextCounterVal());

        counter.inc(1);
        counter.report(null, reporter);
        assertEquals(1, reporter.nextCounterVal());

        counter.inc(1);
        counter.inc(1);
        counter.report(null, reporter);
        assertEquals(2, reporter.nextCounterVal());

        counter.inc(3);
        counter.report(null, reporter);
        assertEquals(3, reporter.nextCounterVal());

        counter.inc(1);
        counter.inc(-3);
        counter.report(null, reporter);
        assertEquals(-2, reporter.nextCounterVal());
    }

    @Test
    public void value() {
        CounterImpl counter = new CounterImpl(scope, "counter");

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
        ScopeImpl scope = new ScopeBuilder(null, new ScopeImpl.Registry()).reporter(new SnapshotBasedStatsReporter()).build();
        Counter counter = new CounterImpl(scope, "counter");

        Snapshot snapshot1 = scope.snapshot();
        assertEquals(0, getSnapshot(snapshot1, "counter").value());

        counter.inc(1);
        Snapshot snapshot2 = scope.snapshot();
        assertEquals(1, getSnapshot(snapshot2, "counter").value());

        counter.inc(1);
        counter.inc(2);
        Snapshot snapshot3 = scope.snapshot();
        assertEquals(4, getSnapshot(snapshot3, "counter").value());
    }

    private static CounterSnapshot getSnapshot(Snapshot snapshot, String name) {
        ScopeKey key = keyForPrefixedStringMap(name, ImmutableMap.EMPTY);
        return snapshot.counters().get(key);
    }
}
