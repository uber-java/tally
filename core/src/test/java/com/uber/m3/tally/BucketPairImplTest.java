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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BucketPairImplTest {
    @Test
    public void bucketPairs() {
        BucketPair[] expectedPairs = new BucketPair[]{
            new BucketPairImpl(
                -Double.MAX_VALUE,
                Double.MAX_VALUE,
                Duration.MIN_VALUE,
                Duration.MAX_VALUE
            )
        };

        assertArrayEquals(expectedPairs, BucketPairImpl.bucketPairs(null));

        expectedPairs = new BucketPair[]{
            new BucketPairImpl(
                -Double.MAX_VALUE,
                0,
                Duration.MIN_VALUE,
                Duration.ZERO
            ),
            new BucketPairImpl(
                0,
                50,
                Duration.ZERO,
                Duration.ofSeconds(50)
            ),
            new BucketPairImpl(
                50,
                100,
                Duration.ofSeconds(50),
                Duration.ofSeconds(100)
            ),
            new BucketPairImpl(
                100,
                Double.MAX_VALUE,
                Duration.ofSeconds(100),
                Duration.MAX_VALUE
            ),
        };

        assertArrayEquals(
            expectedPairs,
            BucketPairImpl.bucketPairs(ValueBuckets.linear(0, 50, 3))
        );
    }

    @Test
    public void addZeroBucket() {
        BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(
            DurationBuckets.linear(Duration.ofMillis(-30), Duration.ofMillis(20), 3)
        );

        assertEquals(5, bucketPairs.length);

        assertEquals(Duration.MIN_VALUE, bucketPairs[0].lowerBoundDuration());
        assertEquals(Duration.ofMillis(-30), bucketPairs[0].upperBoundDuration());

        assertEquals(Duration.ofMillis(-30), bucketPairs[1].lowerBoundDuration());
        assertEquals(Duration.ofMillis(-10), bucketPairs[1].upperBoundDuration());

        assertEquals(Duration.ofMillis(-10), bucketPairs[2].lowerBoundDuration());
        assertEquals(Duration.ZERO, bucketPairs[2].upperBoundDuration());

        assertEquals(Duration.ZERO, bucketPairs[3].lowerBoundDuration());
        assertEquals(Duration.ofMillis(10), bucketPairs[3].upperBoundDuration());

        assertEquals(Duration.ofMillis(10), bucketPairs[4].lowerBoundDuration());
        assertEquals(Duration.MAX_VALUE, bucketPairs[4].upperBoundDuration());
    }

    @Test
    public void testToString() {
        BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(
            ValueBuckets.linear(-100, 200, 2)
        );

        // Unlike Duration buckets, we don't add a 0 in the buckets
        assertEquals(3, bucketPairs.length);
        assertEquals("[-2562047h47m16.854775808s, -1m40s]", bucketPairs[0].toString());
        assertEquals("[-1m40s, 1m40s]", bucketPairs[1].toString());
        assertEquals("[1m40s, 2562047h47m16.854775807s]", bucketPairs[2].toString());

        BucketPair[] emptyBucketPairs = BucketPairImpl.bucketPairs(null);
        assertEquals(1, emptyBucketPairs.length);
        assertEquals("[-2562047h47m16.854775808s, 2562047h47m16.854775807s]", emptyBucketPairs[0].toString());
    }

    @Test
    public void equalsHashCode() {
        BucketPair bucketPair = BucketPairImpl.bucketPairs(null)[0];
        BucketPair sameBucketPair = BucketPairImpl.bucketPairs(null)[0];

        assertTrue(bucketPair.equals(sameBucketPair));
        assertTrue(bucketPair.equals(bucketPair));
        assertEquals(bucketPair.hashCode(), sameBucketPair.hashCode());

        assertFalse(bucketPair.equals(null));
        assertFalse(bucketPair.equals(9));
    }
}
