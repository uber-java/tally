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
//
package com.uber.m3.tally.prometheus;

import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.DurationBuckets;
import com.uber.m3.tally.ValueBuckets;
import com.uber.m3.util.Duration;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uber.m3.tally.prometheus.PrometheusReporter.METRIC_ID_KEY_VALUE;
import static com.uber.m3.tally.prometheus.PrometheusReporter.collectionToStringArray;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.mockito.Mockito.times;

@RunWith(Enclosed.class)
public class PrometheusReporterTest {

    /**
     * Asserts all the time series exposed by {@link io.prometheus.client.Histogram}:
     * - cumulative counters for the observation buckets, exposed as {@code metricName}_bucket;
     * - the total sum of all observed values, exposed as  {@code metricName}_sum;
     * - the count of events that have been observed, exposed as  {@code metricName}_count.
     *
     * @param registry      registry where to search for metric.
     * @param metricName    base metric name to assert.
     * @param tags          metric's tags.
     * @param bucketsValues map of pairs bucket upper bound to its expected value. Use null to assert that the bucket
     *                      is not reported.
     * @param expectedCount OPTIONAL. Expected value of the count of events that have been observed.
     * @param expectedSum   OPTIONAL. The total sum of all observed values.
     */
    private static void assertHistogram(
            CollectorRegistry registry,
            String metricName,
            Map<String, String> tags,
            Map<Double, Double> bucketsValues,
            Double expectedCount,
            Double expectedSum
    ) {
        bucketsValues.forEach((key, value) -> assertBucket(registry, metricName, tags, key, value));
        if (expectedCount != null) {
            String sampleName = String.format("%s_count", metricName);
            Double actualCount = getMetricSample(registry, sampleName, tags, null);
            Assert.assertThat(
                    String.format("failed validation for %s sample", sampleName), actualCount, is(expectedCount)
            );
        }
        if (expectedSum != null) {
            String sampleName = String.format("%s_sum", metricName);
            Double actualSum = getMetricSample(registry, sampleName, tags, null);
            Assert.assertThat(String.format("failed validation for %s sample", sampleName), actualSum, is(actualSum));
        }
    }

    /**
     * Asserts that {@code metricName} metric has a bucket with upper bound equal to {@code bucketUpperBound} and is
     * its value is equal to {@code expectedValue}.
     * If {@code expectedValue} is set to null the method will assert that the bucket does NOT exist.
     *
     * @param registry         registry where to search for metric.
     * @param metricName       base metric name to assert.
     * @param tags             metric's tags.
     * @param bucketUpperBound bucket's upper bound, the value which is used to register a bucket
     *                         in {@link io.prometheus.client.Histogram.Builder#buckets(double...)}.
     * @param expectedValue    expected bucket value or null to assert that the bucket is not reported for a metric.
     */
    private static void assertBucket(
            CollectorRegistry registry,
            String metricName,
            final Map<String, String> tags,
            double bucketUpperBound,
            Double expectedValue
    ) {
        String sampleName = String.format("%s_bucket", metricName);
        String bucketName = Double.toString(bucketUpperBound);
        Double actualValue = getMetricSample(
                registry,
                sampleName,
                tags,
                singletonMap("le", bucketName)
        );
        Assert.assertThat(
                String.format("failed validation for %s bucket:%s sample", sampleName, bucketName),
                actualValue, is(expectedValue)
        );
    }

    private static Double getMetricSample(
            CollectorRegistry registry,
            String metricName,
            final Map<String, String> tags,
            final Map<String, String> extraTags
    ) {
        Map<String, String> ttags = new HashMap<>();
        if (tags != null) {
            ttags.putAll(tags);
        }
        if (extraTags != null) {
            ttags.putAll(extraTags);
        }
        for (Collector.MetricFamilySamples metricFamilySamples : Collections.list(registry.metricFamilySamples())) {
            for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                if (sample.name.equals(metricName)
                        && ttags.size() == sample.labelNames.size()
                        && sample.labelNames.containsAll(ttags.keySet())
                        && ttags.keySet().containsAll(sample.labelNames)
                        && sample.labelValues.containsAll(ttags.values())
                        && ttags.values().containsAll(sample.labelValues)
                ) {
                    return sample.value;
                }
            }
        }
        return registry.getSampleValue(
                metricName,
                collectionToStringArray(ttags.keySet()),
                collectionToStringArray(ttags.values())
        );
    }

    @RunWith(JUnit4.class)
    public static class ReportCounterTest {
        private CollectorRegistry registry;
        private PrometheusReporter reporter;

        @Before
        public void init() {
            registry = Mockito.spy(new CollectorRegistry(true));
            reporter = PrometheusReporter.builder().registry(registry).build();
        }

        @Test
        public void reportCounterNoTags() {
            reporter.reportCounter("test", null, 42);
            Double metricValue = getMetricSample(registry, "test", null, null);
            Assert.assertThat(metricValue, is(42d));
        }

        @Test
        public void reportCounterWithTags() {
            Map<String, String> tags = singletonMap("key", "value");
            reporter.reportCounter("test", tags, 23);
            Double metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(23d));

            reporter.reportCounter("test", tags, 19);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(42d));
            // make sure that counter with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportCounterWithDifferentTagsValues() {
            Map<String, String> tags1 = singletonMap("key", "value1");
            Map<String, String> tags2 = singletonMap("key", "value2");
            reporter.reportCounter("test", tags1, 23);
            reporter.reportCounter("test", tags2, 42);
            Double metricValue1 = getMetricSample(registry, "test", tags1, null);
            Double metricValue2 = getMetricSample(registry, "test", tags2, null);

            Assert.assertThat(metricValue1, is(23d));
            Assert.assertThat(metricValue2, is(42d));
            // make sure that counter with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportCounterWithMultipleTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            tags.put("c", "1");
            tags.put("d", "1");
            reporter.reportCounter("test", tags, 23);
            Double metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(23d));

            reporter.reportCounter("test", tags, 19);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(42d));
            // make sure that counter with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());

            // make sure that counter with the same tag keys but different tags values registered only once but report
            // separate metrics.
            tags.put("a", "2");
            reporter.reportCounter("test", tags, 42);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(42d));
        }

        @Test(expected = IllegalArgumentException.class)
        public void registeringCounterWithSameNameButDifferentTagsShouldFail() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporter.reportCounter("test", tags, 23);
            tags.put("c", "1");
            reporter.reportCounter("test", tags, 19);
        }

        @Test
        public void countersWithDifferentNamesAndSameTagsRegisteredSeparetly() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporter.reportCounter("test1", tags, 23);
            reporter.reportCounter("test2", tags, 42);
            Double metricValue1 = getMetricSample(registry, "test1", tags, null);
            Assert.assertThat(metricValue1, is(23d));

            Double metricValue2 = getMetricSample(registry, "test2", tags, null);
            Assert.assertThat(metricValue2, is(42d));
            Mockito.verify(registry, times(2)).register(Mockito.any());
        }
    }

    @RunWith(JUnit4.class)
    public static class ReportGaugeTest {
        private CollectorRegistry registry;
        private PrometheusReporter reporter;

        @Before
        public void init() {
            registry = Mockito.spy(new CollectorRegistry(true));
            reporter = PrometheusReporter.builder().registry(registry).build();
        }

        @Test
        public void reportGaugeNoTags() {
            reporter.reportGauge("test", null, 23);
            Double metricValue = getMetricSample(registry, "test", null, null);
            Assert.assertThat(metricValue, is(23d));

            reporter.reportGauge("test", null, 0);
            metricValue = getMetricSample(registry, "test", null, null);
            Assert.assertThat(metricValue, is(0d));
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportGaugeWithTags() {
            Map<String, String> tags = singletonMap("key", "value");
            reporter.reportGauge("test", tags, 23);
            Double metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(23d));

            reporter.reportGauge("test", tags, 0);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(0d));
            // make sure that gauge with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportGaugeWithDifferentTagsValues() {
            Map<String, String> tags1 = singletonMap("key", "value1");
            Map<String, String> tags2 = singletonMap("key", "value2");
            reporter.reportGauge("test", tags1, 23);
            reporter.reportGauge("test", tags2, 42);
            Double metricValue1 = getMetricSample(registry, "test", tags1, null);
            Double metricValue2 = getMetricSample(registry, "test", tags2, null);

            Assert.assertThat(metricValue1, is(23d));
            Assert.assertThat(metricValue2, is(42d));
            // make sure that gauge with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportGaugeWithMultipleTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            tags.put("c", "1");
            tags.put("d", "1");
            reporter.reportGauge("test", tags, 23);
            Double metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(23d));

            reporter.reportGauge("test", tags, 42);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(42d));
            // make sure that gauge with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Matchers.any());

            // make sure that gauge with the same tag keys but different tags values registered only once but report
            // separate metrics.
            tags.put("a", "2");
            reporter.reportGauge("test", tags, 13);
            metricValue = getMetricSample(registry, "test", tags, null);
            Assert.assertThat(metricValue, is(13d));
        }

        @Test(expected = IllegalArgumentException.class)
        public void registeringGaugeWithSameNameButDifferentTagsShouldFail() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporter.reportGauge("test", tags, 23);
            tags.put("c", "1");
            reporter.reportGauge("test", tags, 19);
        }

        @Test
        public void gaugesWithDifferentNamesAndSameTagsRegisteredSeparately() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporter.reportGauge("test1", tags, 23);
            reporter.reportGauge("test2", tags, 42);
            Double metricValue1 = getMetricSample(registry, "test1", tags, null);
            Assert.assertThat(metricValue1, is(23d));

            Double metricValue2 = getMetricSample(registry, "test2", tags, null);
            Assert.assertThat(metricValue2, is(42d));
            Mockito.verify(registry, times(2)).register(Mockito.any());
        }
    }

    @RunWith(JUnit4.class)
    public static class ReportTimerTest {
        private CollectorRegistry registry;
        private PrometheusReporter reporterSummary;

        @Before
        public void init() {
            registry = Mockito.spy(new CollectorRegistry(true));
            reporterSummary = PrometheusReporter.builder()
                    .registry(registry)
                    .timerType(TimerType.SUMMARY)
                    .build();
        }

        @Test
        public void reportTimerNoTags() {
            reporterSummary.reportTimer("test", null, Duration.ofSeconds(42));
            Double metricValue = getMetricSample(registry, "test_count", null, null);
            Assert.assertThat(metricValue, is(1d));
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportTimerWithTags() {
            Map<String, String> tags = singletonMap("key", "value");
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(42));
            Double metricValue = getMetricSample(registry, "test_count", tags, null);

            Assert.assertThat(metricValue, is(1d));
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(19));
            metricValue = getMetricSample(registry, "test_count", tags, null);
            Assert.assertThat(metricValue, is(2d));
            // make sure that timer with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportTimerWithDifferentTagsValues() {
            Map<String, String> tags1 = singletonMap("key", "value1");
            Map<String, String> tags2 = singletonMap("key", "value2");
            reporterSummary.reportTimer("test", tags1, Duration.ofSeconds(23));
            reporterSummary.reportTimer("test", tags2, Duration.ofSeconds(42));
            Double metricValue1 = getMetricSample(registry, "test_count", tags1, null);
            Double metricValue2 = getMetricSample(registry, "test_count", tags2, null);

            Assert.assertThat(metricValue1, is(1d));
            Assert.assertThat(metricValue2, is(1d));
            // make sure that timer with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportTimerWithMultipleTags() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            tags.put("c", "1");
            tags.put("d", "1");
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(23));
            Double metricValue = getMetricSample(registry, "test_count", tags, null);
            Assert.assertThat(metricValue, is(1d));
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(42));
            metricValue = getMetricSample(registry, "test_count", tags, null);
            Assert.assertThat(metricValue, is(2d));
            // make sure that timer with the same tag keys registered only once
            Mockito.verify(registry, times(1)).register(Matchers.any());

            // make sure that timer with the same tag keys but different tags values registered only once but report
            // separate metrics.
            tags.put("a", "2");
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(23));
            metricValue = getMetricSample(registry, "test_count", tags, null);
            Assert.assertThat(metricValue, is(1d));
        }

        @Test(expected = IllegalArgumentException.class)
        public void registeringTimerWithSameNameButDifferentTagsShouldFail() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(1));
            tags.put("c", "1");
            reporterSummary.reportTimer("test", tags, Duration.ofSeconds(1));
        }

        @Test
        public void timersWithDifferentNamesAndSameTagsRegisteredSeparately() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            reporterSummary.reportTimer("test1", tags, Duration.ofSeconds(23));
            reporterSummary.reportTimer("test2", tags, Duration.ofSeconds(42));
            Double metricValue1 = getMetricSample(registry, "test1_count", tags, null);
            Double metricValue2 = getMetricSample(registry, "test2_count", tags, null);

            Assert.assertThat(metricValue1, is(1d));
            Assert.assertThat(metricValue2, is(1d));
            Mockito.verify(registry, times(2)).register(Mockito.any());
        }

        @Test
        public void reportTimerDefaultQuantiles() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            double sum = 0;
            for (int i = 0; i < 100; i++) {
                reporterSummary.reportTimer("test", tags, Duration.ofSeconds(i));
                sum += i;
            }
            double quantile_50 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.5")
            );
            double quantile_75 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.75")
            );
            double quantile_95 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.95")
            );
            double quantile_99 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.99")
            );
            double quantile_999 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.999")
            );
            double count_metric = getMetricSample(
                    registry, "test_count", tags, null
            );
            double sum_metric = getMetricSample(
                    registry, "test_sum", tags, null
            );
            Assert.assertThat(quantile_50, is(49d));
            Assert.assertThat(quantile_75, is(74d));
            Assert.assertThat(quantile_95, is(94d));
            Assert.assertThat(quantile_99, is(98d));
            Assert.assertThat(quantile_999, is(98d));
            Assert.assertThat(count_metric, is(100d));
            Assert.assertThat(sum_metric, is(sum));
        }

        @Test
        public void reportTimerCustomQuantiles() {
            Map<String, String> tags = new HashMap<>();
            tags.put("a", "1");
            tags.put("b", "1");
            Map<Double, Double> quantiles = singletonMap(0.1, 0.0001);
            PrometheusReporter reporter = PrometheusReporter.builder()
                    .registry(registry)
                    .defaultQuantiles(quantiles)
                    .build();
            for (int i = 0; i < 100; i++) {
                reporter.reportTimer("test", tags, Duration.ofSeconds(i));
            }
            double quantile_10 = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.1")
            );
            Assert.assertThat(quantile_10, is(9d));
            // make sure that default quantiles are not reported
            Double default_percentile = getMetricSample(
                    registry, "test", tags, singletonMap("quantile", "0.5")
            );
            Assert.assertThat(default_percentile, is(nullValue()));
        }

        @Test
        public void reportTimerHistogramDefaultBuckets() {
            PrometheusReporter reporterHistogram = PrometheusReporter.builder()
                    .registry(registry)
                    .timerType(TimerType.HISTOGRAM)
                    .build();
            Map<String, String> tags1 = new HashMap<>();
            tags1.put("foo", "1");
            tags1.put("bar", "1");

            Map<String, String> tags2 = new HashMap<>();
            tags2.put("foo", "2");
            tags2.put("bar", "2");

            reporterHistogram.reportTimer("test", tags1, Duration.ofMillis(10));
            reporterHistogram.reportTimer("test", tags1, Duration.ofSeconds(1));
            reporterHistogram.reportTimer("test", tags1, Duration.ofSeconds(42));

            reporterHistogram.reportTimer("test", tags2, Duration.ofSeconds(10));

            Map<Double, Double> expectedBucketsValues1 = new HashMap<>();
            expectedBucketsValues1.put(0.005, 0d);
            expectedBucketsValues1.put(0.01, 1d);
            expectedBucketsValues1.put(0.025, 1d);
            expectedBucketsValues1.put(0.05, 1d);
            expectedBucketsValues1.put(0.1, 1d);
            expectedBucketsValues1.put(0.25, 1d);
            expectedBucketsValues1.put(0.5, 1d);
            expectedBucketsValues1.put(0.75, 1d);
            expectedBucketsValues1.put(1d, 2d);
            expectedBucketsValues1.put(2.5, 2d);
            expectedBucketsValues1.put(5d, 2d);
            expectedBucketsValues1.put(7.5, 2d);
            expectedBucketsValues1.put(10d, 2d);

            Map<Double, Double> expectedBucketsValues2 = new HashMap<>();
            expectedBucketsValues2.put(0.005, 0d);
            expectedBucketsValues2.put(0.01, 0d);
            expectedBucketsValues2.put(0.025, 0d);
            expectedBucketsValues2.put(0.05, 0d);
            expectedBucketsValues2.put(0.1, 0d);
            expectedBucketsValues2.put(0.25, 0d);
            expectedBucketsValues2.put(0.5, 0d);
            expectedBucketsValues2.put(0.75, 0d);
            expectedBucketsValues2.put(1d, 0d);
            expectedBucketsValues2.put(2.5, 0d);
            expectedBucketsValues2.put(5d, 0d);
            expectedBucketsValues2.put(7.5, 0d);
            expectedBucketsValues2.put(10d, 1d);

            assertHistogram(registry, "test", tags1, expectedBucketsValues1, 3d, 43.01);
            assertHistogram(registry, "test", tags2, expectedBucketsValues2, 1d, 10d);
        }

        @Test
        public void reportTimerHistogramCustomBuckets() {
            double[] buckets = {1, 10, 100};
            PrometheusReporter reporterHistogram = PrometheusReporter.builder()
                    .registry(registry)
                    .timerType(TimerType.HISTOGRAM)
                    .defaultBuckets(buckets)
                    .build();
            Map<String, String> tags1 = new HashMap<>();
            tags1.put("foo", "1");
            tags1.put("bar", "1");

            Map<String, String> tags2 = new HashMap<>();
            tags2.put("foo", "2");
            tags2.put("bar", "2");

            reporterHistogram.reportTimer("test", tags1, Duration.ofMillis(10));
            reporterHistogram.reportTimer("test", tags1, Duration.ofSeconds(5));
            reporterHistogram.reportTimer("test", tags1, Duration.ofSeconds(42));

            reporterHistogram.reportTimer("test", tags2, Duration.ofSeconds(10));

            reporterHistogram.reportTimer("test2", null, Duration.ofSeconds(10));

            Map<Double, Double> expectedBucketsValues1 = new HashMap<>();
            expectedBucketsValues1.put(1d, 1d);
            expectedBucketsValues1.put(10d, 2d);
            expectedBucketsValues1.put(100d, 3d);

            Map<Double, Double> expectedBucketsValues2 = new HashMap<>();
            expectedBucketsValues2.put(1d, 0d);
            expectedBucketsValues2.put(10d, 1d);
            expectedBucketsValues2.put(100d, 1d);

            assertHistogram(registry, "test", tags1, expectedBucketsValues1, 3d, 43.01);
            assertHistogram(registry, "test", tags2, expectedBucketsValues2, 1d, 10d);
            assertHistogram(registry, "test2", null, expectedBucketsValues2, 1d, 10d);
        }
    }

    @RunWith(Parameterized.class)
    public static class ReportHistogramTest {
        private static final Buckets<Duration> defaultDuraionBuckets = new DurationBuckets(
                new Duration[]{Duration.ofMillis(1), Duration.ofSeconds(1), Duration.ofSeconds(100)}
        );
        private static final Buckets<Double> defaultValueBuckets = new ValueBuckets(new Double[]{0.001, 1d, 100d});

        @Parameterized.Parameter
        public boolean isReportDuration;

        private CollectorRegistry registry;
        private PrometheusReporter reporter;

        @Parameterized.Parameters(name = "{index} Use duration {0}")
        public static Collection<Boolean> data() {
            return Arrays.asList(true, false);
        }

        @Before
        public void init() {
            registry = Mockito.spy(new CollectorRegistry(true));
            reporter = PrometheusReporter.builder().registry(registry).build();
        }

        @Test
        public void reportHistogramSamplesNoTags() {
            if (isReportDuration) {
                reporter.reportHistogramDurationSamples(
                        "test",
                        null,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        10
                );
            } else {
                reporter.reportHistogramValueSamples(
                        "test",
                        null,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        10
                );
            }
            Map<Double, Double> bucketsValues = new HashMap<>();
            bucketsValues.put(0.001, 0d);
            bucketsValues.put(1d, 10d);
            bucketsValues.put(100d, 10d);
            assertHistogram(registry, "test", null, bucketsValues, 10d, 10d);
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportHistogramWithTags() {
            Map<String, String> tags = Collections.singletonMap("foo", "bar");

            if (isReportDuration) {
                reporter.reportHistogramDurationSamples(
                        "test",
                        tags,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        10
                );
            } else {
                reporter.reportHistogramValueSamples(
                        "test",
                        tags,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        10
                );
            }
            Map<Double, Double> bucketsValues = new HashMap<>();
            bucketsValues.put(0.001, 0d);
            bucketsValues.put(1d, 10d);
            bucketsValues.put(100d, 10d);
            assertHistogram(registry, "test", tags, bucketsValues, 10d, 10d);
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportHistogramWithDifferentTagsValues() {
            Map<String, String> tags1 = Collections.singletonMap("foo", "bar");
            Map<String, String> tags2 = Collections.singletonMap("foo", "bazz");

            if (isReportDuration) {
                reporter.reportHistogramDurationSamples(
                        "test",
                        tags1,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        10
                );
                reporter.reportHistogramDurationSamples(
                        "test",
                        tags2,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        100
                );
            } else {
                reporter.reportHistogramValueSamples(
                        "test",
                        tags1,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        10
                );
                reporter.reportHistogramValueSamples(
                        "test",
                        tags2,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        100
                );
            }
            Map<Double, Double> bucketsValues1 = new HashMap<>();
            bucketsValues1.put(0.001, 0d);
            bucketsValues1.put(1d, 10d);
            bucketsValues1.put(100d, 10d);

            Map<Double, Double> bucketsValues2 = new HashMap<>();
            bucketsValues2.put(0.001, 0d);
            bucketsValues2.put(1d, 100d);
            bucketsValues2.put(100d, 100d);
            assertHistogram(registry, "test", tags1, bucketsValues1, 10d, 10d);
            assertHistogram(registry, "test", tags2, bucketsValues2, 100d, 100d);
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test(expected = IllegalArgumentException.class)
        public void registeringHistogramWithSameNameButDifferentTagsShouldFail() {
            Map<String, String> tags1 = Collections.singletonMap("foo", "bar");
            Map<String, String> tags2 = Collections.singletonMap("foo1", "bar");

            if (isReportDuration) {
                reporter.reportHistogramDurationSamples(
                        "test",
                        tags1,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        10
                );
                reporter.reportHistogramDurationSamples(
                        "test",
                        tags2,
                        defaultDuraionBuckets,
                        Duration.ofMillis(1),
                        Duration.ofSeconds(1),
                        100
                );
            } else {
                reporter.reportHistogramValueSamples(
                        "test",
                        tags1,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        10
                );
                reporter.reportHistogramValueSamples(
                        "test",
                        tags2,
                        defaultValueBuckets,
                        0.001,
                        1d,
                        100
                );
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class CanonicalMetricIdTest {

        @Parameterized.Parameter
        public Case testCase;

        @Parameterized.Parameters(name = "{index}: {0}}")
        public static Collection<Case> data() {
            return Arrays.asList(
                    new Case(
                            "no tags keys",
                            "foo",
                            null,
                            "foo+",
                            null
                    ),
                    new Case(
                            "tags names",
                            "foo",
                            new HashSet<>(Arrays.asList("a", "b")),
                            String.format("foo+a=%s,b=%s", METRIC_ID_KEY_VALUE, METRIC_ID_KEY_VALUE),
                            null
                    ),
                    new Case(
                            "empty tags names",
                            "foo",
                            Collections.emptySet(),
                            "foo+",
                            null
                    ),
                    new Case(
                            "no metric name",
                            null,
                            Collections.emptySet(),
                            null,
                            new IllegalArgumentException("metric name cannot be null")
                    )
            );
        }

        @Test
        public void test() {
            try {
                String res = PrometheusReporter.canonicalMetricId(testCase.prefix, testCase.tags);
                if (testCase.expectedResult != null) {
                    Assert.assertThat(res, is(testCase.expectedResult));
                }
            } catch (Exception e) {
                if (testCase.expectedException == null) {
                    Assert.fail("unexpected exception");
                } else {
                    Assert.assertThat(e, instanceOf(testCase.expectedException.getClass()));
                    Assert.assertThat(e.getMessage(), is(testCase.expectedException.getMessage()));
                }
            }
        }

        private static class Case {
            final String testName;
            final String prefix;
            final Set<String> tags;
            final String expectedResult;
            final Exception expectedException;

            Case(String testName, String prefix, Set<String> tags, String expectedResult, Exception expectedException) {
                this.testName = testName;
                this.prefix = prefix;
                this.tags = tags;
                this.expectedResult = expectedResult;
                this.expectedException = expectedException;
            }

            @Override
            public String toString() {
                return testName;
            }
        }
    }

    @RunWith(JUnit4.class)
    public static class CloseReporter {
        @Test
        public void closeShouldRemoveOnlyTallyCollector() {
            CollectorRegistry registry = new CollectorRegistry(true);
            PrometheusReporter reporter = PrometheusReporter.builder().registry(registry).build();
            reporter.reportCounter("counter", null, 42);
            reporter.reportTimer("timer", null, Duration.ofSeconds(42));
            reporter.reportGauge("gauge", null, 42);
            reporter.reportHistogramValueSamples(
                    "histogram", null, new ValueBuckets(new Double[]{1.0, 2.0}), 1.0, 2.0, 42
            );
            Counter promCounter = Counter.build("prom_counter", "help").register(registry);
            promCounter.inc();
            List<Collector.MetricFamilySamples> metricFamilySamples = Collections.list(registry.metricFamilySamples());
            Assert.assertThat(metricFamilySamples.size(), is(5));
            reporter.close();
            metricFamilySamples = Collections.list(registry.metricFamilySamples());
            Assert.assertThat(metricFamilySamples.size(), is(1));
            Double promSample = getMetricSample(registry, "prom_counter", null, null);
            Assert.assertThat(promSample, notNullValue());
        }
    }
}
