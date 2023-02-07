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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of a {@link Snapshot}.
 */
class SnapshotImpl implements Snapshot {
    Map<ScopeKey, CounterSnapshot> counters = new ConcurrentHashMap<>();
    Map<ScopeKey, GaugeSnapshot> gauges = new ConcurrentHashMap<>();
    Map<ScopeKey, TimerSnapshot> timers = new ConcurrentHashMap<>();
    Map<ScopeKey, HistogramSnapshot> histograms = new ConcurrentHashMap<>();

    @Override
    public Map<ScopeKey, CounterSnapshot> counters() {
        return counters;
    }

    @Override
    public Map<ScopeKey, GaugeSnapshot> gauges() {
        return gauges;
    }

    @Override
    public Map<ScopeKey, TimerSnapshot> timers() {
        return timers;
    }

    @Override
    public Map<ScopeKey, HistogramSnapshot> histograms() {
        return histograms;
    }
}
