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

import com.uber.m3.tally.sanitizers.ScopeSanitizerBuilder;
import com.uber.m3.tally.sanitizers.ValidCharacters;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-server", "-XX:+UseG1GC"})
public class ScopeImplBenchmark {

    private static final DurationBuckets EXPONENTIAL_BUCKETS = DurationBuckets.linear(Duration.ofMillis(1), Duration.ofMillis(10), 128);

    private static final String[] COUNTER_NAMES = {
        "first-counter",
        "second-counter",
        "third-counter",
        "fourth-counter",
        "fifth-counter",
    };

    private static final String[] GAUGE_NAMES = {
        "first-gauge",
        "second-gauge",
        "third-gauge",
        "fourth-gauge",
        "fifth-gauge",
    };

    private static final String[] HISTOGRAM_NAMES = {
        "first-histogram",
        "second-histogram",
        "third-histogram",
        "fourth-histogram",
        "fifth-histogram",
    };

    @Benchmark
    public void scopeReportingBenchmark(BenchmarkState state) {
        state.reportingBenchmarkScope.reportLoopIteration();
    }

    @Benchmark
    public void recordingWithSanitizingDashBenchmark(BenchmarkState state) {
        state.recordTestMetrics(state.sanitizingBenchmarkScope);
    }

    @Benchmark
    public void recordingBenchmark(BenchmarkState state) {
        state.recordTestMetrics(state.recordingBenchmarkScope);
    }

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class BenchmarkState {

        private ScopeImpl reportingBenchmarkScope;
        private ScopeImpl sanitizingBenchmarkScope;
        private ScopeImpl recordingBenchmarkScope;

        @Setup
        public void setup() {
            final ScopeBuilder scopeBuilder = new RootScopeBuilder()
                .reporter(new TestStatsReporter())
                .tags(
                    ImmutableMap.of(
                        "service", "some-service",
                        "application", "some-application",
                        "instance", "some-instance"
                    )
                );

            this.reportingBenchmarkScope =
                (ScopeImpl) scopeBuilder.reportEvery(Duration.MAX_VALUE);

            this.recordTestMetrics(this.reportingBenchmarkScope);

            this.recordingBenchmarkScope = (ScopeImpl) scopeBuilder.reportEvery(Duration.MAX_VALUE);
            this.sanitizingBenchmarkScope =
                (ScopeImpl) scopeBuilder.sanitizer(
                    new ScopeSanitizerBuilder()
                        .withNameValidCharacters(ValidCharacters.of(null, ValidCharacters.UNDERSCORE_CHARACTERS))
                        .withTagKeyValidCharacters(ValidCharacters.of(null, ValidCharacters.UNDERSCORE_CHARACTERS))
                        .withTagValueValidCharacters(ValidCharacters.of(null, ValidCharacters.UNDERSCORE_CHARACTERS))
                        .build()
                )
                    .reportEvery(Duration.MAX_VALUE);
        }

        public void recordTestMetrics(final ScopeImpl scope) {
            for (String counterName : COUNTER_NAMES) {
                scope.counter(counterName).inc(1);
            }

            for (String gaugeName : GAUGE_NAMES) {
                scope.gauge(gaugeName).update(0.);
            }

            for (String histogramName : HISTOGRAM_NAMES) {
                Histogram h = scope.histogram(histogramName, EXPONENTIAL_BUCKETS);

                Random r = new Random();

                // Populate at least 20% of the buckets
                int bucketsCount = EXPONENTIAL_BUCKETS.buckets.size();
                for (int i = 0; i < bucketsCount / 5; ++i) {
                    h.recordDuration(EXPONENTIAL_BUCKETS.buckets.get(r.nextInt(bucketsCount)));
                }
            }
        }

        @TearDown
        public void teardown() {
            reportingBenchmarkScope.close();
            recordingBenchmarkScope.close();
            sanitizingBenchmarkScope.close();
        }

    }
}
