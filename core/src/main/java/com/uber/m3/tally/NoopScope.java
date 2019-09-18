// Copyright (c) 2019 Uber Technologies, Inc.
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

import com.uber.m3.util.Duration;

/**
 * Noop implementation of Scope.
 */
public class NoopScope implements Scope {

    static final Counter NOOP_COUNTER = (delta) -> {
    };

    static final Gauge NOOP_GAUGE = (value) -> {
    };

    static final Stopwatch NOOP_STOPWATCH = new Stopwatch(0L, (s) -> {
    });

    static final Timer NOOP_TIMER = new Timer() {
        @Override
        public void record(Duration interval) {
        }

        @Override
        public Stopwatch start() {
            return NOOP_STOPWATCH;
        }
    };

    static final Histogram NOOP_HISTOGRAM = new Histogram() {
        @Override
        public void recordValue(double value) {
        }

        @Override
        public void recordDuration(Duration value) {
        }

        @Override
        public Stopwatch start() {
            return NOOP_STOPWATCH;
        }
    };

    static final Capabilities NOOP_CAPABILITIES = new Capabilities() {
        @Override
        public boolean reporting() {
            return false;
        }

        @Override
        public boolean tagging() {
            return false;
        }
    };

    @Override
    public Counter counter(String name) {
        return NOOP_COUNTER;
    }

    @Override
    public Gauge gauge(String name) {
        return NOOP_GAUGE;
    }

    @Override

    public Timer timer(String name) {
        return NOOP_TIMER;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Histogram histogram(String name, Buckets buckets) {
        return NOOP_HISTOGRAM;
    }

    @Override
    public Scope tagged(Map<String, String> tags) {
        return this;
    }

    @Override
    public Scope subScope(String name) {
        return this;
    }

    @Override
    public Capabilities capabilities() {
        return NOOP_CAPABILITIES;
    }

    @Override
    public void close() {
    }
}
