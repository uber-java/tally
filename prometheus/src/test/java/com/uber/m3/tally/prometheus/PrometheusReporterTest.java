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

import io.prometheus.client.CollectorRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uber.m3.tally.prometheus.PrometheusReporter.METRIC_ID_KEY_VALUE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.times;

@RunWith(Enclosed.class)
public class PrometheusReporterTest {

    @RunWith(JUnit4.class)
    public static class CounterTest {
        private CollectorRegistry registry;
        private PrometheusReporter reporter;

        @Before
        public void init() {
            registry = Mockito.spy(new CollectorRegistry());
            reporter = new PrometheusReporter(registry);

        }

        @Test
        public void reportCounterNoTags() {
            reporter.reportCounter("test", null, 42);
            Double metricValue = registry.getSampleValue("test");
            Assert.assertThat(metricValue, is(42d));
        }

        @Test
        public void reportCounterWithTags() {
            reporter.reportCounter("test", Collections.singletonMap("key", "value"), 23);
            Double metricValue = registry.getSampleValue("test", new String[]{"key"}, new String[]{"value"});
            Assert.assertThat(metricValue, is(23d));
            reporter.reportCounter("test", Collections.singletonMap("key", "value"), 19);
            metricValue = registry.getSampleValue("test", new String[]{"key"}, new String[]{"value"});
            Assert.assertThat(metricValue, is(42d));
            // make sure that counter with the same tag keys registered only ones
            Mockito.verify(registry, times(1)).register(Mockito.any());
        }

        @Test
        public void reportCounterWithDifferentTagsValues() {
            reporter.reportCounter("test", Collections.singletonMap("key", "value1"), 23);
            reporter.reportCounter("test", Collections.singletonMap("key", "value2"), 42);
            Double metricValue1 = registry.getSampleValue("test", new String[]{"key"}, new String[]{"value1"});
            Double metricValue2 = registry.getSampleValue("test", new String[]{"key"}, new String[]{"value2"});
            Assert.assertThat(metricValue1, is(23d));
            Assert.assertThat(metricValue2, is(42d));
            // make sure that counter with the same tag keys registered only ones
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
            Double metricValue = registry.getSampleValue("test",
                    PrometheusReporter.collectionToStringArray(tags.keySet()),
                    PrometheusReporter.collectionToStringArray(tags.values()));
            Assert.assertThat(metricValue, is(23d));
            reporter.reportCounter("test", tags, 19);
            metricValue = registry.getSampleValue("test",
                    PrometheusReporter.collectionToStringArray(tags.keySet()),
                    PrometheusReporter.collectionToStringArray(tags.values()));
            Assert.assertThat(metricValue, is(42d));
            // make sure that counter with the same tag keys registered only ones
            Mockito.verify(registry, times(1)).register(Mockito.any());

            // make sure that counter with the same tag keys but different tags values registered only ones but report
            // separate metrics.
            tags.put("a", "2");
            reporter.reportCounter("test", tags, 42);
            metricValue = registry.getSampleValue("test",
                    PrometheusReporter.collectionToStringArray(tags.keySet()),
                    PrometheusReporter.collectionToStringArray(tags.values()));
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
            Double metricValue1 = registry.getSampleValue("test1",
                    PrometheusReporter.collectionToStringArray(tags.keySet()),
                    PrometheusReporter.collectionToStringArray(tags.values()));
            Assert.assertThat(metricValue1, is(23d));
            Double metricValue2 = registry.getSampleValue("test2",
                    PrometheusReporter.collectionToStringArray(tags.keySet()),
                    PrometheusReporter.collectionToStringArray(tags.values()));
            Assert.assertThat(metricValue2, is(42d));
            Mockito.verify(registry, times(2)).register(Mockito.any());
        }
    }

    @RunWith(Parameterized.class)
    public static class KeyForPrefixedStringMapsTest {

        @Parameterized.Parameter
        public Case testCase;

        @Parameterized.Parameters(name = "{index}: {0}}")
        public static Collection<Case> data() {
            return Arrays.asList(
                    new Case(
                            "no maps",
                            "foo",
                            null,
                            "foo+",
                            null
                    ),
                    new Case(
                            "disjoint maps",
                            "foo",
                            Arrays.asList(
                                    listToMap("a:foo", "b:bar"),
                                    listToMap("c:baz", "d:qux")
                            ),
                            "foo+a=foo,b=bar,c=baz,d=qux",
                            null
                    ),
                    new Case(
                            "maps overlap",
                            "foo",
                            Arrays.asList(
                                    listToMap("a:1", "b:1", "c:1", "d:1"),
                                    listToMap("b:2", "c:2", "d:2")
                            ),
                            "foo+a=1,b=2,c=2,d=2",
                            null
                    ),
                    new Case(
                            "no metric name",
                            null,
                            Arrays.asList(
                                    listToMap("a:1", "b:1", "c:1", "d:1"),
                                    listToMap("b:2", "c:2", "d:2")
                            ),
                            null,
                            new IllegalArgumentException("prefix cannot be null")
                    )
            );
        }

        @Test
        public void test() {
            String res;
            try {
                res = PrometheusReporter.keyForPrefixedStringMaps(testCase.prefix, testCase.tags);
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
            final List<Map<String, String>> tags;
            final String expectedResult;
            final Exception expectedException;

            Case(String testName,
                 String prefix,
                 List<Map<String, String>> tags,
                 String expectedResult, Exception expectedException
            ) {
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

        private static Map<String, String> listToMap(String... input) {
            return Stream.of(input)
                    .distinct()
                    .collect(
                            Collectors.toMap(pair -> pair.split(":")[0],
                                    pair -> pair.split(":")[1]
                            )
                    );
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
            String res;
            try {
                res = PrometheusReporter.canonicalMetricId(testCase.prefix, testCase.tags);
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
}
