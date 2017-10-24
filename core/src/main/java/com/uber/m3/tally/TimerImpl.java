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

package com.uber.m3.tally;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of a {@link Timer}.
 */
class TimerImpl implements Timer, StopwatchRecorder {
    private String name;
    private ImmutableMap<String, String> tags;
    private StatsReporter reporter;
    private Values unreported = new Values();

    TimerImpl(String name, ImmutableMap<String, String> tags, StatsReporter reporter) {
        this.name = name;
        this.tags = tags;

        if (reporter == null) {
            this.reporter = new NoReporterSink();
        } else {
            this.reporter = reporter;
        }
    }

    @Override
    public void record(Duration interval) {
        reporter.reportTimer(name, tags, interval);
    }

    @Override
    public Stopwatch start() {
        return new Stopwatch(System.nanoTime(), this);
    }

    /**
     * Records the stopwatch for the Timer
     * @param stopwatchStart a long previously obtained via System.nanoTime()
     */
    @Override
    public void recordStopwatch(long stopwatchStart) {
        record(Duration.between(stopwatchStart, System.nanoTime()));
    }

    Duration[] snapshot() {
        unreported.readLock().lock();

        Duration[] snap = new Duration[unreported.getValues().size()];

        for (int i = 0; i < unreported.getValues().size(); i++) {
            snap[i] = unreported.getValues().get(i);
        }

        unreported.readLock().unlock();

        return snap;
    }

    static class Values {
        // Using a ReadWriteLock here to protect against multithreaded reads
        // and writes separately. In other places, synchronized blocks are used
        // instead as this separation is not needed e.g. we only lock a
        // ConcurrentHashMap when doing writes and not for reads.
        private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
        private List<Duration> values = new ArrayList<>();

        Lock readLock() {
            return rwlock.readLock();
        }

        Lock writeLock() {
            return rwlock.writeLock();
        }

        public List<Duration> getValues() {
            return values;
        }
    }

    class NoReporterSink implements StatsReporter {
        @Override
        public Capabilities capabilities() {
            return CapableOf.REPORTING_TAGGING;
        }

        @Override
        public void flush() {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        @Override
        public void reportCounter(String name, Map<String, String> tags, long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reportGauge(String name, Map<String, String> tags, double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reportTimer(String name, Map<String, String> tags, Duration interval) {
            unreported.writeLock().lock();

            unreported.getValues().add(interval);

            unreported.writeLock().unlock();
        }

        @Override
        public void reportHistogramValueSamples(String name, Map<String, String> tags, Buckets buckets, double bucketLowerBound, double bucketUpperBound, long samples) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reportHistogramDurationSamples(String name, Map<String, String> tags, Buckets buckets, Duration bucketLowerBound, Duration bucketUpperBound, long samples) {
            throw new UnsupportedOperationException();
        }
    }

}
