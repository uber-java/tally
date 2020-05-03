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

import com.uber.m3.thrift.gen.Metric;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SizedMetricTest {
    @Test
    public void simpleSizedMetric() {
        Metric metric = new Metric();

        SizedMetric sizedMetric = new SizedMetric(metric, 1);

        assertEquals(sizedMetric.getMetric(), metric);
        assertEquals(sizedMetric.getSize(), 1);
    }

    @Test
    public void nullSizedMetric() {
        SizedMetric sizedMetric = new SizedMetric(null, 9);

        assertNull(sizedMetric.getMetric());
        assertEquals(sizedMetric.getSize(), 9);
    }

    @Test
    public void setters() {
        SizedMetric sizedMetric = new SizedMetric();

        sizedMetric.setSize(9);

        assertEquals(9, sizedMetric.getSize());

        sizedMetric.setSize(11);
        sizedMetric.setSize(12);

        assertEquals(12, sizedMetric.getSize());

        Metric metric1 = new Metric();
        sizedMetric.setMetric(metric1);

        assertEquals(metric1, sizedMetric.getMetric());
    }
}
