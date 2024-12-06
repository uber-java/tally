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

import static com.uber.m3.tally.ScopeImpl.keyForPrefixedStringMap;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;

/**
 * A stats reporter implementation enabling snapshot recording.
 */
public class SnapshotBasedStatsReporter implements StatsReporter {
    private final AtomicReference<Snapshot> currentSnapshot = new AtomicReference<>(new SnapshotImpl());

    // Not synchronized as it's a read-only snapshot.
    private volatile Snapshot flushedSnapshot = new SnapshotImpl();

    /**
     * Returns the last flushed snapshot.
     */
    Snapshot getFlushedSnapshot() {
        return flushedSnapshot;
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.NONE;
    }

    @Override
    public void flush() {
        currentSnapshot.updateAndGet(snapshot -> {
            flushedSnapshot = snapshot;
            return new SnapshotImpl();
        });
    }

    @Override
    public void close() {
        flush();
    }

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        ImmutableMap<String, String> tagsKey = toImmutable(tags);
        ScopeKey scopeKey = keyForPrefixedStringMap(name, tagsKey);
        currentSnapshot.get().counters().compute(scopeKey, (k, v) -> new CounterSnapshotImpl(name, tagsKey, value));
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        ImmutableMap<String, String> tagsKey = toImmutable(tags);
        ScopeKey scopeKey = keyForPrefixedStringMap(name, tagsKey);
        currentSnapshot.get().gauges().compute(scopeKey, (k, v) -> new GaugeSnapshotImpl(name, tagsKey, value));
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        ImmutableMap<String, String> tagsKey = toImmutable(tags);
        ScopeKey scopeKey = keyForPrefixedStringMap(name, tagsKey);
        currentSnapshot.get().timers().computeIfAbsent(scopeKey, k -> new TimerSnapshotImpl(name, tagsKey));
        currentSnapshot.get().timers().computeIfPresent(scopeKey, (k, snapshot) -> {
            ((TimerSnapshotImpl) snapshot).addDuration(interval);
            return snapshot;
        });
    }

    @Override
    public void reportHistogramValueSamples(String name, Map<String, String> tags, Buckets buckets, double bucketLowerBound, double bucketUpperBound, long samples) {
        ImmutableMap<String, String> tagsKey = toImmutable(tags);
        ScopeKey scopeKey = keyForPrefixedStringMap(name, tagsKey);
        ensureHistogramBucketsFor(name, tagsKey, buckets);
        currentSnapshot.get().histograms().computeIfPresent(scopeKey, (k, snapshot) -> {
            ((HistogramSnapshotImpl) snapshot).addValue(bucketUpperBound, samples);
            return snapshot;
        });
    }

    @Override
    public void reportHistogramDurationSamples(String name, Map<String, String> tags, Buckets buckets, Duration bucketLowerBound, Duration bucketUpperBound, long samples) {
        ImmutableMap<String, String> tagsKey = toImmutable(tags);
        ScopeKey scopeKey = keyForPrefixedStringMap(name, toImmutable(tags));
        ensureHistogramBucketsFor(name, tagsKey, buckets);
        currentSnapshot.get().histograms().computeIfPresent(scopeKey, (k, snapshot) -> {
            ((HistogramSnapshotImpl) snapshot).addDuration(bucketUpperBound, samples);
            return snapshot;
        });
    }

    private void ensureHistogramBucketsFor(String name, ImmutableMap<String, String> tags, Buckets buckets) {
        ScopeKey scopeKey = keyForPrefixedStringMap(name, tags);
        currentSnapshot.get().histograms().computeIfAbsent(scopeKey, k -> {
            HistogramSnapshotImpl snapshot = new HistogramSnapshotImpl(name, tags);
            for (int i = 0; i <= buckets.asValues().length; ++i) {
                if (buckets instanceof DurationBuckets) {
                    snapshot.addDuration(buckets.getDurationUpperBoundFor(i), 0);
                }
                if (buckets instanceof ValueBuckets) {
                    snapshot.addValue(buckets.getValueUpperBoundFor(i), 0);
                }
            }
            return snapshot;
        });
    }

    private static ImmutableMap<String, String> toImmutable(Map<String, String> tags) {
        if (tags == null) {
            return ImmutableMap.EMPTY;
        }
        return new ImmutableMap<>(tags);
    }
}
