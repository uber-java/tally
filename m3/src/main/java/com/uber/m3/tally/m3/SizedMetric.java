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

import com.uber.m3.thrift.generated.Metric;

/**
 * A metric along with its associated size.
 */
public class SizedMetric extends Metric {
    private Metric metric;
    private int size;

    /**
     * Creates a new {@link SizedMetric} with the given metric and size.
     * @param metric the metric
     * @param size   the size of this metric
     */
    public SizedMetric(Metric metric, int size) {
        // Recreate metric to prevent underlying metric from being modified
        this.metric = new Metric(metric);
        this.size = size;
    }

    /**
     * Returns the metric.
     * @return the metric
     */
    public Metric getMetric() {
        return metric;
    }

    /**
     * Returns the size of this metric.
     * @return the size of this metric
     */
    public int getSize() {
        return size;
    }
}
