package com.uber.m3.tally;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(jvmArgsAppend = "-server")
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

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class BenchmarkState {

        private ScopeImpl scope;

        @Setup
        public void setup() {
            this.scope =
                    (ScopeImpl) new RootScopeBuilder()
                            .reporter(new TestStatsReporter())
                            .tags(
                                    ImmutableMap.of(
                                            "service", "some-service",
                                            "application", "some-application",
                                            "instance", "some-instance"
                                    )
                            )
                            .reportEvery(Duration.MAX_VALUE);

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
                for (int i = 0; i < bucketsCount / 10; ++i) {
                    h.recordDuration(EXPONENTIAL_BUCKETS.buckets.get(r.nextInt(bucketsCount)));
                }
            }
        }

        @TearDown
        public void teardown() {
            scope.close();
        }

    }

    @Benchmark
    public void scopeReportingBenchmark(BenchmarkState state) {
        state.scope.reportLoopIteration();
    }
}
