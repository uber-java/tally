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

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class NoopScopeTest {

    @Test
    public void testCounter() {
        NoopScope noopScope = new NoopScope();
        Counter noopCounter1 = noopScope.counter("test1");
        Counter noopCounter2 = noopScope.counter("test2");
        Assert.assertThat(
                "same noop counter returned",
                noopCounter1 == noopCounter2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop counter",
                noopCounter1 == NoopScope.NOOP_COUNTER,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testGauge() {
        NoopScope noopScope = new NoopScope();
        Gauge noopGauge1 = noopScope.gauge("test1");
        Gauge noopGauge2 = noopScope.gauge("test2");
        Assert.assertThat(
                "same noop gauge returned",
                noopGauge1 == noopGauge2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop gauge",
                noopGauge1 == NoopScope.NOOP_GAUGE,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testTimer() {
        NoopScope noopScope = new NoopScope();
        Timer noopTimer1 = noopScope.timer("test1");
        Timer noopTimer2 = noopScope.timer("test2");
        Assert.assertThat(
                "same noop timer returned",
                noopTimer1 == noopTimer2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop timer",
                noopTimer1 == NoopScope.NOOP_TIMER,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop stopwatch",
                noopTimer1.start() == NoopScope.NOOP_STOPWATCH,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testHistogram() {
        NoopScope noopScope = new NoopScope();
        Histogram noopHistogram1 = noopScope.histogram("test1", ValueBuckets.custom(1, 2, 3));
        Histogram noopHistogram2 = noopScope.histogram("test2", ValueBuckets.custom(4, 5, 6));

        Assert.assertThat(
                "same noop histogram returned",
                noopHistogram1 == noopHistogram2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop histogram",
                noopHistogram1 == NoopScope.NOOP_HISTOGRAM,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testCapabilities() {
        NoopScope noopScope = new NoopScope();
        Capabilities noopCapabilities1 = noopScope.capabilities();
        Capabilities noopCapabilities2 = noopScope.capabilities();
        Assert.assertThat(
                "same noop capabilities returned",
                noopCapabilities1 == noopCapabilities2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat(
                "noop capabilities",
                noopCapabilities1 == NoopScope.NOOP_CAPABILITIES,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testSubscope() {
        NoopScope noopScope = new NoopScope();
        Scope noopScope1 = noopScope.subScope("test1");
        Scope noopScope2 = noopScope.subScope("test2");
        Assert.assertThat("same noop scope returned",
                noopScope1 == noopScope2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat("noop scope returned",
                noopScope1 == noopScope,
                CoreMatchers.equalTo(true)
        );
    }

    @Test
    public void testTagged() {
        NoopScope noopScope = new NoopScope();
        Map<String, String> tagMap1 = new HashMap<>(2);
        tagMap1.put("A", "1");
        tagMap1.put("B", "2");

        Map<String, String> tagMap2 = new HashMap<>(2);
        tagMap1.put("C", "3");
        tagMap1.put("D", "4");

        Scope noopScope1 = noopScope.tagged(tagMap1);
        Scope noopScope2 = noopScope.tagged(tagMap2);
        Assert.assertThat("same noop scope returned",
                noopScope1 == noopScope2,
                CoreMatchers.equalTo(true)
        );
        Assert.assertThat("noop scope returned",
                noopScope1 == noopScope,
                CoreMatchers.equalTo(true)
        );
    }
}
