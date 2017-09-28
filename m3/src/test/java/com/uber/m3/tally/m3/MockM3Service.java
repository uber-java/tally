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

import com.uber.m3.thrift.generated.M3;
import com.uber.m3.thrift.generated.Metric;
import com.uber.m3.thrift.generated.MetricBatch;
import org.apache.thrift.transport.TTransportException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MockM3Service implements M3.Iface {
    ReadWriteLock lock = new ReentrantReadWriteLock();
    List<MetricBatch> batches = new ArrayList<>();
    List<Metric> metrics = new ArrayList<>();
    Phaser phaser;
    boolean countBatches;

    public MockM3Service(Phaser phaser, boolean countBatches) {
        this.phaser = phaser;
        this.countBatches = countBatches;
    }

    public List<MetricBatch> getBatches() {
        lock.readLock().lock();

        try {
            return batches;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Metric> getMetrics() {
        lock.readLock().lock();

        try {
            return metrics;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void emitMetricBatch(MetricBatch batch) throws TTransportException {
        lock.writeLock().lock();

        batches.add(batch);

        if (countBatches) {
            phaser.arrive();
        }

        for (Metric metric : batch.getMetrics()) {
            metrics.add(metric);

            if (!countBatches) {
                phaser.arrive();
            }
        }

        lock.writeLock().unlock();

        throw new TTransportException(TTransportException.END_OF_FILE, "complete");
    }
}
