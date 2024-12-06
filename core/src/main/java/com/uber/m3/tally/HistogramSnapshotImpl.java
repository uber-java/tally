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

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of a {@link HistogramSnapshot}.
 */
class HistogramSnapshotImpl implements HistogramSnapshot {
    private final String name;
    private final ImmutableMap<String, String> tags;
    private final Map<Double, Long> values = new ConcurrentHashMap<>();
    private final Map<Duration, Long> durations = new ConcurrentHashMap<>();

    HistogramSnapshotImpl(String name, ImmutableMap<String, String> tags) {
        this.name = name;
        this.tags = tags;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return tags;
    }

    @Override
    public Map<Double, Long> values() {
        return new ImmutableMap<>(values);
    }

    @Override
    public Map<Duration, Long> durations() {
        return new ImmutableMap<>(durations);
    }

    /**
     * Appends a new duration to the current snapshot. We kept the access modifier as default to avoid any external access.
     */
    void addDuration(Duration upperBound, long samples) {
        durations.put(upperBound, samples);
    }

    /**
     * Appends a new value to the current snapshot. We kept the access modifier as default to avoid any external access.
     */
    void addValue(double upperBound, long samples) {
        values.put(upperBound, samples);
    }
}
