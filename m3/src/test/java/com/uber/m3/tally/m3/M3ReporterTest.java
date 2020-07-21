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

package com.uber.m3.tally.m3;

import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.DurationBuckets;
import com.uber.m3.tally.ValueBuckets;
import com.uber.m3.thrift.gen.CountValue;
import com.uber.m3.thrift.gen.GaugeValue;
import com.uber.m3.thrift.gen.Metric;
import com.uber.m3.thrift.gen.MetricBatch;
import com.uber.m3.thrift.gen.MetricTag;
import com.uber.m3.thrift.gen.MetricValue;
import com.uber.m3.thrift.gen.TimerValue;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// TODO add tests to validate proper shutdown
// TODO add tests to validate uncaughts don't crash processor
// TODO add tests for multi-processor setup
public class M3ReporterTest {
    private static double EPSILON = 1e-9;

    private static SocketAddress socketAddress;

    private static final ImmutableMap<String, String> DEFAULT_TAGS =
        ImmutableMap.of(
            "env", "test",
            "host", "test-host"
        );

    private M3Reporter reporter;

    @BeforeClass
    public static void setup() {
        try {
            socketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 4448);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to get localhost");
        }
    }

    @Before
    public void setupTest() {
        reporter =
                new M3Reporter.Builder(socketAddress)
                    .service("test-service")
                    .commonTags(DEFAULT_TAGS)
                    .build();
    }

    @After
    public void teardownTest() {
        reporter.close();
    }

    @Test
    public void reporter() throws InterruptedException {
        ImmutableMap<String, String> commonTags = new ImmutableMap.Builder<String, String>(5)
                .put("env", "development")
                .put("host", "default")
                .put("commonTag1", "val1")
                .put("commonTag2", "val2")
                .put("commonTag3", "val3")
                .build();

        ImmutableMap<String, String> tags = new ImmutableMap.Builder<String, String>(2)
                .put("testTag1", "testVal1")
                .put("testTag2", "testVal2")
                .build();

        M3Reporter.Builder reporterBuilder =
                new M3Reporter.Builder(socketAddress)
                    .service("test-service")
                    .commonTags(commonTags)
                    .includeHost(true);

        List<MetricBatch> receivedBatches;
        List<Metric> receivedMetrics;

        try (final M3Reporter reporter = reporterBuilder.build()) {
            try (final MockM3Server server = bootM3Collector(3)) {
                reporter.reportCounter("my-counter", tags, 10);
                reporter.flush();

                reporter.reportTimer("my-timer", tags, Duration.ofMillis(5));
                reporter.flush();

                reporter.reportGauge("my-gauge", tags, 42.42);
                reporter.flush();

                // Shutdown both reporter and server
                reporter.close();
                server.await();

                receivedBatches = server.getService().getBatches();
                receivedMetrics = server.getService().snapshotMetrics();
            }
        }

        // Validate common tags
        for (MetricBatch batch : receivedBatches) {
            assertNotNull(batch);
            assertTrue(batch.isSetCommonTags());
            assertEquals(commonTags.size() + 1, batch.getCommonTags().size());

            for (MetricTag tag : batch.getCommonTags()) {
                if (tag.getTagName().equals(M3Reporter.SERVICE_TAG)) {
                    assertEquals("test-service", tag.getTagValue());
                } else {
                    assertEquals(commonTags.get(tag.getTagName()), tag.getTagValue());
                }
            }
        }

        // Validate metrics
        assertEquals(3, receivedMetrics.size());

        Metric emittedCounter = receivedMetrics.get(0);
        Metric emittedTimer = receivedMetrics.get(1);
        Metric emittedGauge = receivedMetrics.get(2);

        assertEquals("my-counter", emittedCounter.getName());
        assertTrue(emittedCounter.isSetTags());
        assertEquals(tags.size(), emittedCounter.getTagsSize());

        for (MetricTag tag : emittedCounter.getTags()) {
            assertEquals(tags.get(tag.getTagName()), tag.getTagValue());
        }

        // Validate counter
        assertTrue(emittedCounter.isSetMetricValue());

        MetricValue emittedValue = emittedCounter.getMetricValue();
        assertTrue(emittedValue.isSetCount());
        assertFalse(emittedValue.isSetGauge());
        assertFalse(emittedValue.isSetTimer());

        CountValue emittedCount = emittedValue.getCount();
        assertTrue(emittedCount.isSetI64Value());
        assertEquals(10, emittedCount.getI64Value());

        // Validate timer
        assertTrue(emittedTimer.isSetMetricValue());

        emittedValue = emittedTimer.getMetricValue();
        assertFalse(emittedValue.isSetCount());
        assertFalse(emittedValue.isSetGauge());
        assertTrue(emittedValue.isSetTimer());

        TimerValue emittedTimerValue = emittedValue.getTimer();
        assertTrue(emittedTimerValue.isSetI64Value());
        assertEquals(5_000_000, emittedTimerValue.getI64Value());

        // Validate gauge
        assertTrue(emittedGauge.isSetMetricValue());

        emittedValue = emittedGauge.getMetricValue();
        assertFalse(emittedValue.isSetCount());
        assertTrue(emittedValue.isSetGauge());
        assertFalse(emittedValue.isSetTimer());

        GaugeValue emittedGaugeValue = emittedValue.getGauge();
        assertTrue(emittedGaugeValue.isSetDValue());
        assertEquals(42.42, emittedGaugeValue.getDValue(), EPSILON);
    }

    @Test
    public void builder() {
        SocketAddress address = new InetSocketAddress("1.2.3.4", 5678);
        M3Reporter.Builder builder = new M3Reporter.Builder(address)
            .service("some-service")
            .env("test")
            .histogramBucketIdName("histId")
            .histogramBucketName("histName")
            .histogramBucketTagPrecision(8);

        assertNotNull(builder);

        M3Reporter reporter = builder.build();

        assertNotNull(reporter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderInvalidAddress() {
        new M3Reporter.Builder(new SocketAddress[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderMissingCommonTag() {
        new M3Reporter.Builder(socketAddress).build();
    }

    @Test
    public void reporterFinalFlush() throws InterruptedException {
        try (final MockM3Server server = bootM3Collector(1)) {
            reporter.reportTimer("final-flush-timer", null, Duration.ofMillis(10));
            reporter.close();

            server.await();

            List<MetricBatch> batches = server.getService().getBatches();
            assertEquals(1, batches.size());
            assertNotNull(batches.get(0));
            assertEquals(1, batches.get(0).getMetrics().size());
        }
    }

    @Test
    public void reporterAfterCloseNoThrow() throws InterruptedException {
        try (final MockM3Server server = bootM3Collector(0);) {
            reporter.close();

            reporter.reportGauge("my-gauge", null, 4.2);
            reporter.flush();
        }
    }

    @Test
    public void reporterHistogramDurations() throws InterruptedException {
        List<MetricBatch> receivedBatches;

        try (final MockM3Server server = bootM3Collector(2)) {
            Buckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(25), 5);

            Map<String, String> histogramTags = new HashMap<>();
            histogramTags.put("foo", "bar");

            reporter.reportHistogramDurationSamples(
                    "my-histogram",
                    histogramTags,
                    buckets,
                    Duration.ZERO,
                    Duration.ofMillis(25),
                    7
            );

            reporter.reportHistogramDurationSamples(
                    "my-histogram",
                    histogramTags,
                    buckets,
                    Duration.ofMillis(50),
                    Duration.ofMillis(75),
                    3
            );

            reporter.close();
            server.await();

            receivedBatches = server.getService().getBatches();
        }

        assertEquals(1, receivedBatches.size());
        assertNotNull(receivedBatches.get(0));
        assertEquals(2, receivedBatches.get(0).getMetrics().size());

        // Verify first bucket
        Metric metric = receivedBatches.get(0).getMetrics().get(0);
        assertEquals("my-histogram", metric.getName());
        assertTrue(metric.isSetTags());
        assertEquals(3, metric.getTagsSize());

        Map<String, String> expectedTags = new HashMap<>(3, 1);
        expectedTags.put("foo", "bar");
        expectedTags.put("bucketid", "0001");
        expectedTags.put("bucket", "0-25ms");
        for (MetricTag tag : metric.getTags()) {
            assertEquals(expectedTags.get(tag.getTagName()), tag.getTagValue());
        }

        assertTrue(metric.isSetMetricValue());

        MetricValue value = metric.getMetricValue();
        assertTrue(value.isSetCount());
        assertFalse(value.isSetGauge());
        assertFalse(value.isSetTimer());

        CountValue count = value.getCount();
        assertTrue(count.isSetI64Value());
        assertEquals(7, count.getI64Value());

        // Verify second bucket
        metric = receivedBatches.get(0).getMetrics().get(1);
        assertEquals("my-histogram", metric.getName());
        assertTrue(metric.isSetTags());
        assertEquals(3, metric.getTagsSize());

        expectedTags.put("bucketid", "0003");
        expectedTags.put("bucket", "50ms-75ms");
        for (MetricTag tag : metric.getTags()) {
            assertEquals(expectedTags.get(tag.getTagName()), tag.getTagValue());
        }

        assertTrue(metric.isSetMetricValue());

        value = metric.getMetricValue();
        assertTrue(value.isSetCount());
        assertFalse(value.isSetGauge());
        assertFalse(value.isSetTimer());

        count = value.getCount();
        assertTrue(count.isSetI64Value());
        assertEquals(3, count.getI64Value());
    }

    @Test
    public void reporterHistogramValues() throws InterruptedException {
        List<MetricBatch> receivedBatches;

        try(final MockM3Server server = bootM3Collector(2);) {
            Buckets buckets = ValueBuckets.linear(0, 25_000_000, 5);

            Map<String, String> histogramTags = new HashMap<>();
            histogramTags.put("foo", "bar");

            reporter.reportHistogramValueSamples(
                    "my-histogram",
                    histogramTags,
                    buckets,
                    0,
                    25_000_000,
                    7
            );

            reporter.reportHistogramValueSamples(
                    "my-histogram",
                    histogramTags,
                    buckets,
                    50_000_000,
                    75_000_000,
                    3
            );

            reporter.close();
            server.await();

            receivedBatches = server.getService().getBatches();
        }

        assertEquals(1, receivedBatches.size());
        assertNotNull(receivedBatches.get(0));
        assertEquals(2, receivedBatches.get(0).getMetrics().size());

        // Verify first bucket
        Metric metric = receivedBatches.get(0).getMetrics().get(0);
        assertEquals("my-histogram", metric.getName());
        assertTrue(metric.isSetTags());
        assertEquals(3, metric.getTagsSize());

        Map<String, String> expectedTags = new HashMap<>(3, 1);
        expectedTags.put("foo", "bar");
        expectedTags.put("bucketid", "0001");
        expectedTags.put("bucket", "0.000000-25000000.000000");
        for (MetricTag tag : metric.getTags()) {
            assertEquals(expectedTags.get(tag.getTagName()), tag.getTagValue());
        }

        assertTrue(metric.isSetMetricValue());

        MetricValue value = metric.getMetricValue();
        assertTrue(value.isSetCount());
        assertFalse(value.isSetGauge());
        assertFalse(value.isSetTimer());

        CountValue count = value.getCount();
        assertTrue(count.isSetI64Value());
        assertEquals(7, count.getI64Value());

        // Verify second bucket
        metric = receivedBatches.get(0).getMetrics().get(1);
        assertEquals("my-histogram", metric.getName());
        assertTrue(metric.isSetTags());
        assertEquals(3, metric.getTagsSize());

        expectedTags.put("bucketid", "0003");
        expectedTags.put("bucket", "50000000.000000-75000000.000000");
        for (MetricTag tag : metric.getTags()) {
            assertEquals(expectedTags.get(tag.getTagName()), tag.getTagValue());
        }

        assertTrue(metric.isSetMetricValue());

        value = metric.getMetricValue();
        assertTrue(value.isSetCount());
        assertFalse(value.isSetGauge());
        assertFalse(value.isSetTimer());

        count = value.getCount();
        assertTrue(count.isSetI64Value());
        assertEquals(3, count.getI64Value());
    }

    @Test
    public void capability() {
        M3Reporter reporter = new M3Reporter.Builder(socketAddress)
            .service("capability-service")
            .env("capability-env")
            .build();

        assertEquals(CapableOf.REPORTING_TAGGING, reporter.capabilities());
    }

    private static MockM3Server bootM3Collector(int expectedMetricsCount) {
        final MockM3Server server = new MockM3Server(expectedMetricsCount, socketAddress);
        new Thread(server::serve).start();
        return server;
    }

    @Test
    public void testSinglePacketPayloadOverflow() throws InterruptedException {
        // NOTE: Every metric emitted in this test is taking about 22 bytes,
        //       therefore 5000 of those should occupy at least 110_000 bytes of the payload
        //       which should be split across 2 UDP datagrams with the current configuration
        int expectedMetricsCount = 5_000;

        // NOTE: We're using default max packet size
        M3Reporter.Builder reporterBuilder = new M3Reporter.Builder(socketAddress)
                .service("test-service")
                .commonTags(
                        ImmutableMap.of("env", "test")
                )
                // Effectively disable time-based flushing to only keep
                // size-based one
                .maxProcessorWaitUntilFlushMillis(1_000_000);

        List<Metric> metrics;

        try (final MockM3Server server =bootM3Collector(expectedMetricsCount)) {
            try (final M3Reporter reporter = reporterBuilder.build()) {

                ImmutableMap<String, String> emptyTags =
                        new ImmutableMap.Builder<String, String>(0).build();

                for (int i = 0; i < expectedMetricsCount; ++i) {
                    // NOTE: The goal is to minimize the metric size, to make sure
                    //       they're granular enough to detect any transport/reporter configuration
                    //       inconsistencies
                    reporter.reportCounter("c", emptyTags, 1);
                }

                // Shutdown reporter
                reporter.close();

                server.await();

                metrics = server.getService().snapshotMetrics();
            }
        }

        assertEquals(expectedMetricsCount, metrics.size());
    }
}
