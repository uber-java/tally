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

import com.uber.m3.util.ImmutableMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of a {@link Gauge}.
 */
class GaugeImpl extends MetricBase implements Gauge, Reportable {
    private AtomicBoolean updated = new AtomicBoolean(false);
    private AtomicLong curr = new AtomicLong(0);

    protected GaugeImpl(ScopeImpl scope, String fqn) {
        super(fqn);

        scope.addToReportingQueue(this);
    }

    @Override
    public void update(double value) {
        curr.set(Double.doubleToLongBits(value));
        updated.set(true);
    }

    double value() {
        return Double.longBitsToDouble(curr.get());
    }

    @Override
    public void report(ImmutableMap<String, String> tags, StatsReporter reporter) {
        if (updated.getAndSet(false)) {
            reporter.reportGauge(getQualifiedName(), tags, value());
        }
    }

    double snapshot() {
        return value();
    }
}
