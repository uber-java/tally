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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for {@link StatsReporter} benchmark tests.
 * If you're adding a new implementation of {@link StatsReporter} you should include benchmark tests in your code.
 * See `com.uber.m3.tally.m3.M3ReporterBenchmark` for an example.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 2, jvmArgsAppend = {"-server", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public abstract class AbstractReporterBenchmark<Reporter extends StatsReporter> {

    private Logger logger = LoggerFactory.getLogger(getClass());

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

    private static final int TARGET_PARALLELISM = 4;

    private Reporter reporter;

    @Benchmark
    public void reportCounterBenchmark(Counter value) {
        reporter.reportCounter(COUNTER_NAME, DEFAULT_TAGS, value.incrementAndGet());
    }

    @Threads(TARGET_PARALLELISM)
    @Benchmark
    public void reportCounterParallelBenchmark(Counter value) {
        if (isOpen()) {
            reporter.reportCounter(COUNTER_NAME, DEFAULT_TAGS, value.incrementAndGet());
        }
    }

    // This method is used to throttle the execution, returning
    //  - {@code true} w/ probability equal to {@code 1 - 1 / TARGET_PARALLELISM}, and returning
    //  - {@code false} w/ probability equal to {@code 1 / TARGET_PARALLELISM}
    protected boolean isOpen() {
        int dice = ThreadLocalRandom.current().nextInt(TARGET_PARALLELISM);
        return dice == 0;
    }

    @Benchmark
    public void reportGaugeBenchmark(Counter value) {
        reporter.reportGauge(GAUGE_NAME, DEFAULT_TAGS, value.incrementAndGet());
    }

    @Benchmark
    public void reportTimerBenchmark(Counter value) {
        reporter.reportTimer(TIMER_NAME, DEFAULT_TAGS, Duration.ofSeconds(value.incrementAndGet()));
    }

    @Benchmark
    public void reportHistogramDurationSamplesBenchmark(Counter value) {
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
    public void reportHistogramValueSamplesBenchmark(Counter value) {
        reporter.reportHistogramValueSamples(
                HISTOGRAM_VALUE_NAME,
                DEFAULT_TAGS,
                VALUE_EXPONENTIAL_BUCKETS,
                5.0,
                5.0,
                value.incrementAndGet()
        );
    }

    @Setup(Level.Iteration)
    public void setup() {
        this.reporter = bootReporter();
        logger.info("Booted reporter");
    }

    @TearDown(Level.Iteration)
    public void teardown() {
        shutdownReporter(reporter);
        reporter = null;
        logger.info("Shutdown reporter");
    }

    public abstract Reporter bootReporter();

    public void shutdownReporter(Reporter reporter) {
        reporter.close();
    }

    @State(Scope.Thread)
    public static class Counter {
        private long x;

        long incrementAndGet() {
            if (x == Long.MAX_VALUE) {
                x = 0;
            }
            return x++;
        }
    }
}
