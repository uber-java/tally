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

package com.uber.m3.tally.statsd;

import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.DurationBuckets;
import com.uber.m3.tally.ValueBuckets;
import com.uber.m3.tally.statsd.StatsdAssertingUdpServer.ReportedMetric;
import com.uber.m3.util.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import java.util.HashSet;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class StatsdReporterTest {
    private final int PORT = 4434;

    private StatsDClient statsd;
    private StatsdReporter reporter;

    @Test
    public void statsdClient() {
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("key1:val1");
        expectedTags.add("key2:val:with:colons");

        Set<ReportedMetric> expected = new HashSet<>();
        expected.add(new ReportedMetric("statsd-test.statsd-count", "4", "c", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-count-notags", "4", "c", new HashSet<>()));
        expected.add(new ReportedMetric("statsd-test.statsd-gauge", "1.5", "g", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-timer", "250", "ms", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-histvalue.2000.000000-3000.000000", "510", "c", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-histduration.19ms-20ms", "1250", "c", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-histvalue-inf.-infinity-infinity", "99", "c", expectedTags));
        expected.add(new ReportedMetric("statsd-test.statsd-histduration-inf.-infinity-infinity", "999", "c", expectedTags));

        StatsdAssertingUdpServer server = new StatsdAssertingUdpServer("localhost", PORT, expected);

        Thread serverThread = new Thread(server);
        serverThread.start();

        statsd = new NonBlockingStatsDClientBuilder()
            .prefix("statsd-test")
            .hostname("localhost")
            .port(PORT)
            .blocking(true)
            .build();
        reporter = new StatsdReporter(statsd);

        Map<String, String> tags = new HashMap<>();
        tags.put("key1", "val1");
        tags.put("key2", "val:with:colons");

        reporter.reportCounter("statsd-count", tags, 4);
        reporter.reportCounter("statsd-count-notags", null, 4);
        reporter.reportGauge("statsd-gauge", tags, 1.5);
        reporter.reportTimer("statsd-timer", tags, Duration.ofMillis(250));
        reporter.reportHistogramValueSamples(
            "statsd-histvalue",
            tags,
            ValueBuckets.linear(0, 1000, 6),
            2000,
            3000,
            510
        );
        reporter.reportHistogramDurationSamples(
            "statsd-histduration",
            tags,
            DurationBuckets.linear(Duration.ofSeconds(10), Duration.ofSeconds(1), 11),
            Duration.ofMillis(19),
            Duration.ofMillis(20),
            1250
        );
        reporter.reportHistogramValueSamples(
            "statsd-histvalue-inf",
            tags,
            new ValueBuckets(new Double[]{-Double.MAX_VALUE, Double.MAX_VALUE}),
            -Double.MAX_VALUE,
            Double.MAX_VALUE,
            99
        );
        reporter.reportHistogramDurationSamples(
            "statsd-histduration-inf",
            tags,
            new DurationBuckets(new Duration[]{Duration.MIN_VALUE, Duration.MAX_VALUE}),
            Duration.MIN_VALUE,
            Duration.MAX_VALUE,
            999
        );

        statsd.stop();
        reporter.close();

        assertThat(server.getErrored(), is(new ArrayList<>()));
    }

    @Test
    public void capabilities() {
        reporter = new StatsdReporter(new NoOpStatsDClient());

        assertEquals(CapableOf.REPORTING, reporter.capabilities());
    }
}
