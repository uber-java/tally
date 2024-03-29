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

/**
 * A snapshot of values since last report execution.
 */
public interface Snapshot {
    /**
     * Returns a {@link CounterSnapshot} of all {@link Counter} summations since last report execution.
     * @return a {@link CounterSnapshot} of all {@link Counter} summations since last report execution
     */
    Map<ScopeKey, CounterSnapshot> counters();

    /**
     * Returns a {@link GaugeSnapshot} of {@link Gauge} last values since last report execution.
     * @return a {@link GaugeSnapshot} of {@link Gauge} last values since last report execution
     */
    Map<ScopeKey, GaugeSnapshot> gauges();

    /**
     * Returns a {@link TimerSnapshot} of {@link Timer} values since last report execution.
     * @return a {@link TimerSnapshot} of {@link Timer} values since last report execution
     */
    Map<ScopeKey, TimerSnapshot> timers();

    /**
     * Returns a {@link HistogramSnapshot} of {@link Histogram} samples since last report execution.
     * @return a {@link HistogramSnapshot} of {@link Histogram} samples since last report execution
     */
    Map<ScopeKey, HistogramSnapshot> histograms();
}
