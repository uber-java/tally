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

import com.uber.m3.util.ImmutableMap;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of a {@link Counter}.
 */
class CounterImpl implements Counter {
    private AtomicLong prev = new AtomicLong(0);
    private AtomicLong curr = new AtomicLong(0);

    @Override
    public void inc(long delta) {
        curr.getAndAdd(delta);
    }

    long value() {
        long current = curr.get();
        long previous = prev.get();

        if (current == previous) {
            return 0;
        }

        prev.set(current);

        return current - previous;
    }

    void report(String name, ImmutableMap<String, String> tags, StatsReporter reporter) {
        long delta = value();

        if (delta == 0) {
            return;
        }

        reporter.reportCounter(name, tags, delta);
    }

    long snapshot() {
        return curr.get() - prev.get();
    }
}
