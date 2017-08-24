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

import com.uber.m3.util.Duration;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DurationBucketsTest {
    @Test
    public void asValues() {
        DurationBuckets buckets = new DurationBuckets(new Duration[]{
            Duration.ofNanos(10),
            Duration.ofNanos(30),
            Duration.ofNanos(55),
            Duration.ofNanos(200),
            Duration.ofNanos(1000)
        });

        Double[] expectedBuckets = new Double[] {
            10d / Duration.NANOS_PER_SECOND,
            30d / Duration.NANOS_PER_SECOND,
            55d / Duration.NANOS_PER_SECOND,
            200d / Duration.NANOS_PER_SECOND,
            1000d / Duration.NANOS_PER_SECOND
        };

        assertArrayEquals(expectedBuckets, buckets.asValues());
    }

    @Test
    public void asDurations() {
        DurationBuckets buckets = new DurationBuckets(new Duration[]{
            Duration.ofNanos(10),
            Duration.ofNanos(30),
            Duration.ofNanos(55),
            Duration.ofNanos(200),
            Duration.ofNanos(1000)
        });

        Duration[] expectedBuckets = new Duration[]{
            Duration.ofNanos(10),
            Duration.ofNanos(30),
            Duration.ofNanos(55),
            Duration.ofNanos(200),
            Duration.ofNanos(1000)
        };

        assertArrayEquals(expectedBuckets, buckets.asDurations());
    }

    @Test
    public void linear() {
        DurationBuckets expectedBuckets = new DurationBuckets(new Duration[]{
            Duration.ofSeconds(2),
            Duration.ofSeconds(7),
            Duration.ofSeconds(12),
            Duration.ofSeconds(17),
            Duration.ofSeconds(22),
            Duration.ofSeconds(27),
            Duration.ofSeconds(32),
            Duration.ofSeconds(37),
            Duration.ofSeconds(42),
            Duration.ofSeconds(47),
        });

        assertEquals(expectedBuckets, DurationBuckets.linear(
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            10
        ));
    }

    @Test
    public void exponential() {
        DurationBuckets expectedBuckets = new DurationBuckets(new Duration[]{
            Duration.ofSeconds(256),
            Duration.ofSeconds(384),
            Duration.ofSeconds(576),
            Duration.ofSeconds(864),
            Duration.ofSeconds(1296),
        });

        assertEquals(expectedBuckets, DurationBuckets.exponential(
            Duration.ofSeconds(256),
            1.5,
            5
        ));
    }
}
