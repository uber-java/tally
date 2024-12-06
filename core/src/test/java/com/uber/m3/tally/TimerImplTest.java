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

import static com.uber.m3.tally.ScopeImpl.keyForPrefixedStringMap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TimerImplTest {
    private final MonotonicClock.FakeClock clock = MonotonicClock.fake();
    private final TestStatsReporter reporter = new TestStatsReporter();

    @Test
    public void record() {
        TimerImpl timer = new TimerImpl(clock, "", null, reporter);

        timer.record(Duration.ofMillis(42));
        assertEquals(Duration.ofMillis(42), reporter.nextTimerVal());

        timer.record(Duration.ofMinutes(2));
        assertEquals(Duration.ofMinutes(2), reporter.nextTimerVal());

        Stopwatch stopwatch = timer.start();
        clock.addDuration(Duration.ofMillis(200));
        stopwatch.stop();

        assertEquals(Duration.ofMillis(200), reporter.nextTimerVal());
    }

    @Test
    public void snapshotDuration() {
        SnapshotBasedStatsReporter snapshotReporter = new SnapshotBasedStatsReporter();
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(snapshotReporter)
                .clock(clock)
                .build();
        Timer timer = scope.timer("timer");

        timer.record(Duration.ofMillis(42));
        Snapshot snapshot1 = scope.snapshot();

        assertArrayEquals(
            new Duration[]{Duration.ofMillis(42)},
            getSnapshot(snapshot1, "timer").values());

        timer.record(Duration.ofMillis(3));
        timer.record(Duration.ofMillis(2));
        timer.record(Duration.ofMillis(1));
        Snapshot snapshot2 = scope.snapshot();

        assertArrayEquals(
            new Duration[]{Duration.ofMillis(3), Duration.ofMillis(2), Duration.ofMillis(1)},
            getSnapshot(snapshot2, "timer").values());
    }

    @Test
    public void snapshotStopwatch() {
        SnapshotBasedStatsReporter snapshotReporter = new SnapshotBasedStatsReporter();
        ScopeImpl scope =
            new ScopeBuilder(null, new ScopeImpl.Registry())
                .reporter(snapshotReporter)
                .clock(clock)
                .build();
        Timer timer = scope.timer("timer");

        Stopwatch stopwatch1 = timer.start();
        clock.addNanos(100);
        stopwatch1.stop();
        Snapshot snapshot1 = scope.snapshot();
        assertArrayEquals(
            new Duration[]{Duration.ofNanos(100)},
            getSnapshot(snapshot1, "timer").values());

        Stopwatch stopwatch2 = timer.start();
        clock.addNanos(200);
        stopwatch2.stop();
        Stopwatch stopwatch3 = timer.start();
        clock.addNanos(150);
        stopwatch3.stop();
        Snapshot snapshot2 = scope.snapshot();
        assertArrayEquals(
            new Duration[]{Duration.ofNanos(200), Duration.ofNanos(150)},
            getSnapshot(snapshot2, "timer").values());
    }

    private static TimerSnapshot getSnapshot(Snapshot snapshot, String name) {
        ScopeKey key = keyForPrefixedStringMap(name, ImmutableMap.EMPTY);
        return snapshot.timers().get(key);
    }
}
