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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TimerImplTest {
    private TestStatsReporter reporter;
    private TimerImpl timer;

    @Before
    public void setUp() {
        reporter = new TestStatsReporter();
        timer = new TimerImpl("", null, reporter);
    }

    @Test
    public void record() {
        timer.record(Duration.ofMillis(42));
        assertEquals(Duration.ofMillis(42), reporter.nextTimerVal());

        timer.record(Duration.ofMinutes(2));
        assertEquals(Duration.ofMinutes(2), reporter.nextTimerVal());

        Stopwatch stopwatch = timer.start();
        stopwatch.stop();

        Duration duration = reporter.nextTimerVal();
        assertNotNull(duration);
        assertTrue(duration.compareTo(Duration.ZERO) > 0);
    }

    @Test
    public void noReporterSinkSnapshot() {
        timer = new TimerImpl("no-reporter-timer", null, null);

        StatsReporter sink = timer.new NoReporterSink();

        // No-ops
        sink.flush();
        sink.close();

        assertEquals(CapableOf.REPORTING_TAGGING, sink.capabilities());
        sink.reportTimer("new-timer", null, Duration.ofMillis(888));

        assertArrayEquals(new Duration[]{Duration.ofMillis(888)}, timer.snapshot());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void unsupportedCounter() {
        StatsReporter sink = new TimerImpl("", null, null).new NoReporterSink();
        sink.reportCounter(null, null, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void unsupportedGauge() {
        StatsReporter sink = new TimerImpl("", null, null).new NoReporterSink();
        sink.reportGauge(null, null, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void unsupportedHistogramValue() {
        StatsReporter sink = new TimerImpl("", null, null).new NoReporterSink();
        sink.reportHistogramValueSamples(null, null, null, 0, 0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void unsupportedHistogramDuration() {
        StatsReporter sink = new TimerImpl("", null, null).new NoReporterSink();
        sink.reportHistogramDurationSamples(null, null, null, null, null, 0);
    }
}
