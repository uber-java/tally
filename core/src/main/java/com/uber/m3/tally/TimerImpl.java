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

/**
 * Default implementation of a {@link Timer}.
 */
class TimerImpl implements Timer, StopwatchRecorder {
    private final MonotonicClock clock;
    private final String name;
    private final ImmutableMap<String, String> tags;
    private final StatsReporter reporter;

    TimerImpl(MonotonicClock clock, String name, ImmutableMap<String, String> tags, StatsReporter reporter) {
        this.clock = clock;
        this.name = name;
        this.tags = tags;
        this.reporter = reporter;
    }

    @Override
    public void record(Duration interval) {
        reporter.reportTimer(name, tags, interval);
    }

    @Override
    public Stopwatch start() {
        return new Stopwatch(clock.nowNanos(), this);
    }

    /**
     * Records the stopwatch for the Timer
     * @param stopwatchStart a long previously obtained via System.nanoTime()
     */
    @Override
    public void recordStopwatch(long stopwatchStart) {
        record(Duration.between(stopwatchStart, clock.nowNanos()));
    }
}
