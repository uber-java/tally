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

package com.uber.m3.tally.statsd;

import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.DurationBuckets;
import com.uber.m3.tally.ValueBuckets;
import com.uber.m3.util.Duration;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class StatsdReporterTest {
    private static final int PORT = 4434;

    private StatsDClient statsd;
    private StatsdReporter reporter;

    @Test
    public void statsdClient() {
        HashSet<String> expectedStrs = new HashSet<>();
        expectedStrs.add("statsd-test.statsd-count:4|c");
        expectedStrs.add("statsd-test.statsd-gauge:1.5|g");
        expectedStrs.add("statsd-test.statsd-timer:250|ms");
        expectedStrs.add("statsd-test.statsd-histvalue.2000.000000-3000.000000:510|c");
        expectedStrs.add("statsd-test.statsd-histduration.19ms-20ms:1250|c");
        expectedStrs.add("statsd-test.statsd-histvalue-inf.-infinity-infinity:99|c");
        expectedStrs.add("statsd-test.statsd-histduration-inf.-infinity-infinity:999|c");

        StatsdAssertingUdpServer server = new StatsdAssertingUdpServer("localhost", PORT, expectedStrs);

        Thread serverThread = new Thread(server);
        serverThread.start();

        statsd = new NonBlockingStatsDClient("statsd-test", "localhost", PORT);
        reporter = new StatsdReporter(statsd);

        reporter.reportCounter("statsd-count", null, 4);
        reporter.reportGauge("statsd-gauge", null, 1.5);
        reporter.reportTimer("statsd-timer", null, Duration.ofMillis(250));
        reporter.reportHistogramValueSamples(
            "statsd-histvalue",
            null,
            ValueBuckets.linear(0, 1000, 6),
            2000,
            3000,
            510
        );
        reporter.reportHistogramDurationSamples(
            "statsd-histduration",
            null,
            DurationBuckets.linear(Duration.ofSeconds(10), Duration.ofSeconds(1), 11),
            Duration.ofMillis(19),
            Duration.ofMillis(20),
            1250
        );
        reporter.reportHistogramValueSamples(
            "statsd-histvalue-inf",
            null,
            new ValueBuckets(new Double[]{-Double.MAX_VALUE, Double.MAX_VALUE}),
            -Double.MAX_VALUE,
            Double.MAX_VALUE,
            99
        );
        reporter.reportHistogramDurationSamples(
            "statsd-histduration-inf",
            null,
            new DurationBuckets(new Duration[]{Duration.MIN_VALUE, Duration.MAX_VALUE}),
            Duration.MIN_VALUE,
            Duration.MAX_VALUE,
            999
        );

        statsd.stop();
        reporter.close();
    }

    @Test
    public void capabilities() {
        reporter = new StatsdReporter(new NoOpStatsDClient());

        assertEquals(CapableOf.REPORTING, reporter.capabilities());
    }
}
