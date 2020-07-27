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

import com.uber.m3.thrift.gen.M3;
import com.uber.m3.thrift.gen.Metric;
import com.uber.m3.thrift.gen.MetricBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MockM3Service implements M3.Iface {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<MetricBatch> batches = new ArrayList<>();
    private final List<Metric> metrics = new ArrayList<>();
    private final CountDownLatch metricsCountLatch;

    public MockM3Service(CountDownLatch metricsCountLatch) {
        this.metricsCountLatch = metricsCountLatch;
    }

    public List<MetricBatch> snapshotBatches() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(batches);
        } finally {
            lock.readLock().unlock();
        }
    }
    public List<Metric> snapshotMetrics() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(metrics);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void emitMetricBatch(MetricBatch batch) {
        lock.writeLock().lock();

        batches.add(batch);

        for (Metric metric : batch.getMetrics()) {
            metrics.add(metric);
            metricsCountLatch.countDown();
        }

        lock.writeLock().unlock();
    }
}
