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

import org.hamcrest.CoreMatchers;
import org.junit.Test;

import com.uber.m3.util.Duration;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

public class ValueBucketsTest {
    @Test
    public void asValues() {
        ValueBuckets buckets = new ValueBuckets(new Double[]{
            10d,
            30d,
            55d,
            200d,
            1000d
        });

        Double[] expectedBuckets = new Double[] {
            10d,
            30d,
            55d,
            200d,
            1000d
        };

        assertArrayEquals(expectedBuckets, buckets.asValues());
    }

    @Test
    public void asDurations() {
        ValueBuckets buckets = new ValueBuckets(new Double[]{
            10d,
            30d,
            55d,
            200d,
            1000d
        });

        Duration[] expectedBuckets = new Duration[]{
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofSeconds(55),
            Duration.ofSeconds(200),
            Duration.ofSeconds(1000)
        };

        assertArrayEquals(expectedBuckets, buckets.asDurations());
    }

    @Test
    public void linear() {
        ValueBuckets expectedBuckets = new ValueBuckets(new Double[]{
            2d,
            7d,
            12d,
            17d,
            22d,
            27d,
            32d,
            37d,
            42d,
            47d
        });

        assertEquals(expectedBuckets, ValueBuckets.linear(
            2,
            5,
            10
        ));
    }

    @Test
    public void exponential() {
        ValueBuckets expectedBuckets = new ValueBuckets(new Double[]{
            256d,
            384d,
            576d,
            864d,
            1296d
        });

        assertEquals(expectedBuckets, ValueBuckets.exponential(
            256,
            1.5,
            5
        ));
    }

    @Test
    public void custom() {
        ValueBuckets expectedBuckets = new ValueBuckets(new Double[] {
            1d,
            2d,
            3d,
            5d,
            7d
        });
        assertThat("Buckets are created as per our expectations",
                ValueBuckets.custom(1D, 2D, 3D, 5D, 7D),
                equalTo(expectedBuckets));
    }

    @Test(expected = IllegalArgumentException.class)
    public void customFailOnEmptyBucket() {
        ValueBuckets.custom();
    }

    @Test(expected = IllegalArgumentException.class)
    public void customFailOnUnsortedBuckets() {
        ValueBuckets.custom(1, 3, 2);
    }

    @Test
    public void testToString() {
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 6);
        assertEquals("[0.0, 10.0, 20.0, 30.0, 40.0, 50.0]", buckets.toString());

        buckets = new ValueBuckets(new Double[]{99.99});
        assertEquals("[99.99]", buckets.toString());
    }

    @Test
    public void equalsHashcode() {
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);
        ValueBuckets sameBuckets = ValueBuckets.linear(0, 10, 3);

        assertTrue(buckets.equals(sameBuckets));
        assertEquals(buckets.hashCode(), sameBuckets.hashCode());

        assertFalse(buckets.equals(null));
        assertFalse(buckets.equals(9));
    }

    @Test
    public void getBucketIndexFor() {
        // This will create the following buckets:
        // (-Inf, 0), [0, 10), [10, 20), [20, +Inf)
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);

        // Everything bellow 0 should fall into the first bucket
        int res = buckets.getBucketIndexFor(-1);
        assertThat(res, is(0));
        res = buckets.getBucketIndexFor(Double.NEGATIVE_INFINITY);
        assertThat(res, is(0));

        // Everything in [0; 10) should fall into the second bucket
        res = buckets.getBucketIndexFor(0);
        assertThat(res, is(1));
        res = buckets.getBucketIndexFor(Double.MIN_VALUE);
        assertThat(res, is(1));
        res = buckets.getBucketIndexFor(9);
        assertThat(res, is(1));
        res = buckets.getBucketIndexFor(9.9999999999);
        assertThat(res, is(1));
        // Oh well...
        // res = buckets.getBucketIndexFor(10 - Double.MIN_VALUE);
        // assertThat(res, is(1));

        // Everything in [10; 20) should fall into the third bucket
        res = buckets.getBucketIndexFor(10);
        assertThat(res, is(2));
        res = buckets.getBucketIndexFor(10 + Double.MIN_VALUE);
        assertThat(res, is(2));
        res = buckets.getBucketIndexFor(19);
        assertThat(res, is(2));
        res = buckets.getBucketIndexFor(19.9999999999);
        assertThat(res, is(2));
        // Oh well...
        // res = buckets.getBucketIndexFor(20 - Double.MIN_VALUE);
        // assertThat(res, is(2));

        // Everything in [20; +Inf) should fall into the fourth bucket
        res = buckets.getBucketIndexFor(20);
        assertThat(res, is(3));
        res = buckets.getBucketIndexFor(20 + Double.MIN_VALUE);
        assertThat(res, is(3));
        res = buckets.getBucketIndexFor(42);
        assertThat(res, is(3));
        res = buckets.getBucketIndexFor(Double.MAX_VALUE);
        assertThat(res, is(3));
        res = buckets.getBucketIndexFor(Double.POSITIVE_INFINITY);
        assertThat(res, is(3));
    }

    @Test
    public void getValueUpperBoundFor() {
        // This will create the following buckets:
        // (-Inf, 0), [0, 10), [10, 20), [20, +Inf)
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);
        double valueUpperBoundFor = buckets.getValueUpperBoundFor(0);
        assertThat(valueUpperBoundFor, is(0.0));
        valueUpperBoundFor = buckets.getValueUpperBoundFor(1);
        assertThat(valueUpperBoundFor, is(10.0));
        valueUpperBoundFor = buckets.getValueUpperBoundFor(2);
        assertThat(valueUpperBoundFor, is(20.0));
        valueUpperBoundFor = buckets.getValueUpperBoundFor(3);
        assertThat(valueUpperBoundFor, is(Double.POSITIVE_INFINITY));
        valueUpperBoundFor = buckets.getValueUpperBoundFor(4);
        assertThat(valueUpperBoundFor, is(Double.POSITIVE_INFINITY));
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void getValueUpperBoundForThroughsException() {
        // This will create the following buckets:
        // (-Inf, 0), [0, 10), [10, 20), [20, +Inf)
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);
        buckets.getValueUpperBoundFor(-1);
    }
    
    @Test
    public void getValueLowerBoundFor() {
        // This will create the following buckets:
        // (-Inf, 0), [0, 10), [10, 20), [20, +Inf)
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);
        double valueLowerBoundFor = buckets.getValueLowerBoundFor(0);
        assertThat(valueLowerBoundFor, is(Double.NEGATIVE_INFINITY));
        valueLowerBoundFor = buckets.getValueLowerBoundFor(1);
        assertThat(valueLowerBoundFor, is(0.0));
        valueLowerBoundFor = buckets.getValueLowerBoundFor(2);
        assertThat(valueLowerBoundFor, is(10.0));
        valueLowerBoundFor = buckets.getValueLowerBoundFor(3);
        assertThat(valueLowerBoundFor, is(20.0));
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void getValueLowerBoundForThroughsException() {
        // This will create the following buckets:
        // (-Inf, 0), [0, 10), [10, 20), [20, +Inf)
        ValueBuckets buckets = ValueBuckets.linear(0, 10, 3);
        buckets.getValueLowerBoundFor(-1);
    }
}
