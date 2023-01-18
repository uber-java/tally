package com.uber.m3.tally;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = { "-server", "-XX:+UseG1GC" })
public class ScopeImplConcurrent {
    private static String KEYS[] = new String[]{
        " ", "0", "@", "P",
    };

    @Benchmark
    public void hotkeyLockContention(Blackhole bh, BenchmarkState state) {
        ImmutableMap<String, String> common = new ImmutableMap.Builder<String, String>().build();
        for (int i = 0; i < 10000; i++) {

            for (String key : KEYS) {
                Scope scope = state.scope.computeSubscopeIfAbsent(key, common);
                assert scope != null;
                bh.consume(scope);
            }
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class BenchmarkState {

        private ScopeImpl scope;

        @Setup
        public void setup() {
            this.scope =
                    (ScopeImpl) new RootScopeBuilder()
                            .reporter(new TestStatsReporter())
                            .reportEvery(Duration.MAX_VALUE);

            for (String key : KEYS) {
                scope.computeSubscopeIfAbsent(key, new ImmutableMap.Builder<String, String>().build());
            }
        }

        @TearDown
        public void teardown() {
            scope.close();
        }
    }
}
