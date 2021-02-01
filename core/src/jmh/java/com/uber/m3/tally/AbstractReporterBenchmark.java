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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

// TODO: 31/01/2021 add descrition and readme how to use.
@State(Scope.Benchmark)
public abstract class AbstractReporterBenchmark {
    private static final ImmutableMap<String, String> DEFAULT_TAGS = new ImmutableMap.Builder<String, String>(5)
            .put("tag1", "test1")
            .put("tag2", "test2")
            .put("tag3", "test3")
            .put("tag4", "test4")
            .put("env", "test")
            .build();

    private static final DurationBuckets DURATION_EXPONENTIAL_BUCKETS = DurationBuckets.linear(
            Duration.ofMillis(1), Duration.ofMillis(10), 128
    );

    private static final ValueBuckets VALUE_EXPONENTIAL_BUCKETS = ValueBuckets.linear(
            0.1, 100d, 128
    );

    private static final String COUNTER_NAME = "counter";
    private static final String GAUGE_NAME = "gauge";
    private static final String TIMER_NAME = "timer";
    private static final String HISTOGRAM_DURATION_NAME = "histogram_duration";
    private static final String HISTOGRAM_VALUE_NAME = "histogram_value";

    private StatsReporter reporter;

    @Benchmark
    public void reportCounterBenchmark(IncrementalValueState value) {
        reporter.reportCounter(COUNTER_NAME, DEFAULT_TAGS, value.incrementAndGet());
    }

    @Benchmark
    public void reportGaugeBenchmark(IncrementalValueState value) {
        reporter.reportCounter(COUNTER_NAME, DEFAULT_TAGS, value.incrementAndGet());
    }

    @Benchmark
    public void reportTimerBenchmark(IncrementalValueState value) {
        reporter.reportTimer(TIMER_NAME, DEFAULT_TAGS, Duration.ofSeconds(value.incrementAndGet()));
    }

    @Benchmark
    public void reportHistogramDurationSamplesBenchmark(IncrementalValueState value) {
        reporter.reportHistogramDurationSamples(
                HISTOGRAM_DURATION_NAME,
                DEFAULT_TAGS,
                DURATION_EXPONENTIAL_BUCKETS,
                DURATION_EXPONENTIAL_BUCKETS.getDurationLowerBoundFor(10),
                DURATION_EXPONENTIAL_BUCKETS.getDurationLowerBoundFor(10),
                value.incrementAndGet()
        );
    }

    @Benchmark
    public void reportHistogramValueSamplesBenchmark(IncrementalValueState value) {
        reporter.reportHistogramValueSamples(
                HISTOGRAM_VALUE_NAME,
                DEFAULT_TAGS,
                VALUE_EXPONENTIAL_BUCKETS,
                5.0,
                5.0,
                value.incrementAndGet()
        );
    }

    @Setup
    public void setup() {
        this.reporter = initReporter();

        reporter.reportCounter(COUNTER_NAME, DEFAULT_TAGS, 1);
        reporter.reportGauge(GAUGE_NAME, DEFAULT_TAGS, 1);
        reporter.reportTimer(TIMER_NAME, DEFAULT_TAGS, Duration.ofSeconds(1));
        reporter.reportHistogramValueSamples(
                HISTOGRAM_VALUE_NAME,
                DEFAULT_TAGS,
                VALUE_EXPONENTIAL_BUCKETS,
                0.1,
                0.1,
                1
        );
        reporter.reportHistogramDurationSamples(
                HISTOGRAM_DURATION_NAME,
                DEFAULT_TAGS,
                DURATION_EXPONENTIAL_BUCKETS,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1
        );
    }

    @TearDown
    public void teardown() {
        reporter.close();
    }

    public abstract StatsReporter initReporter();

    @State(Scope.Thread)
    public static class IncrementalValueState {
        private long x;

        long incrementAndGet() {
            if (x == Long.MAX_VALUE) {
                x = 0;
            }
            return x++;
        }
    }
}
