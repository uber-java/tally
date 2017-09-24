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

import com.uber.m3.tally.m3.thrift.TCalcTransport;
import com.uber.m3.thrift.generated.CountValue;
import com.uber.m3.thrift.generated.GaugeValue;
import com.uber.m3.thrift.generated.Metric;
import com.uber.m3.thrift.generated.MetricBatch;
import com.uber.m3.thrift.generated.MetricTag;
import com.uber.m3.thrift.generated.MetricValue;
import com.uber.m3.thrift.generated.TimerValue;
import org.apache.thrift.protocol.TCompactProtocol;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashSet;

public class ResourcePoolBenchmark {
    static final int NUM_OUTER_ITERATIONS = 5;
    static final int NUM_INNER_ITERATIONS = 10000;

    @Benchmark
    public void usingResourcePool(Blackhole sink) {
        ResourcePool pool = new ResourcePool(ResourcePool.BATCH_POOL_SIZE);

        for (int iterations = 0; iterations < NUM_OUTER_ITERATIONS; iterations++) {
            // Get and release from the pool right away, then suggest a GC run
            for (int innerIterations = 0; innerIterations < NUM_INNER_ITERATIONS; innerIterations++) {
                sink.consume(pool.releaseMetric(pool.getMetric()));
                sink.consume(pool.releaseMetricTag(pool.getMetricTag()));
                sink.consume(pool.releaseMetricValue(pool.getMetricValue()));
                sink.consume(pool.releaseMetricBatch(pool.getMetricBatch()));
                sink.consume(pool.releaseProtocol(pool.getProtocol()));
                sink.consume(pool.releaseSizedMetric(pool.getSizedMetric()));
                sink.consume(pool.releaseCountValue(pool.getCountValue()));
                sink.consume(pool.releaseGaugeValue(pool.getGaugeValue()));
                sink.consume(pool.releaseTimerValue(pool.getTimerValue()));
                sink.consume(pool.getTagList());
            }

            System.gc();
        }
    }

    @Benchmark
    public void usingNaiveObjectCreation(Blackhole sink) {
        for (int iterations = 0; iterations < NUM_OUTER_ITERATIONS; iterations++) {
            for (int innerIterations = 0; innerIterations < NUM_INNER_ITERATIONS; innerIterations++) {
                sink.consume(new Metric());
                sink.consume(new MetricTag());
                sink.consume(new MetricValue());
                sink.consume(new MetricBatch());
                sink.consume(new TCompactProtocol.Factory().getProtocol(new TCalcTransport()));
                sink.consume(new SizedMetric());
                sink.consume(new CountValue());
                sink.consume(new GaugeValue());
                sink.consume(new TimerValue());
                sink.consume(new HashSet<>());
            }

            System.gc();
        }
    }
}
