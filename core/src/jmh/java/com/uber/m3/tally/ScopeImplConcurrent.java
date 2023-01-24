// Copyright (c) 2023 Uber Technologies, Inc.
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
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = { "-server", "-XX:+UseG1GC" })
public class ScopeImplConcurrent {
    private static final String[] KEYS = new String[]{
        " ", "0", "@", "P",
    };

    @Benchmark
    public void hotkeyLockContention(Blackhole bh, BenchmarkState state) {
        ImmutableMap<String, String> common = new ImmutableMap.Builder<String, String>().build();
        for (int i = 0; i < 10000; i++) {

            for (String key : KEYS) {
                Scope scope = state.scope.computeSubscopeIfAbsent("prefix", key, common);
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
                scope.computeSubscopeIfAbsent("prefix", key, new ImmutableMap.Builder<String, String>().build());
            }
        }

        @TearDown
        public void teardown() {
            scope.close();
        }
    }
}
