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
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DurationBucketsTest {
    @Test
    public void durationBuckets() {
        DurationBuckets buckets = DurationBuckets.linear(Duration.ofMillis(100), Duration.ofMillis(10), 6);

        assertFalse(buckets.isEmpty());
        assertTrue(buckets.contains(Duration.ofMillis(120)));

        for (Iterator<?> iter : new Iterator[]{buckets.iterator(), buckets.listIterator()}) {
            for (int i = 0; i < buckets.size(); i++) {
                assertEquals(Duration.ofMillis(100 + 10 * i), iter.next());
            }
        }

        buckets.add(Duration.ofMillis(160));
        assertEquals(Duration.ofMillis(160), buckets.get(6));

        buckets.set(6, Duration.ofMillis(170));
        assertEquals(Duration.ofMillis(170), buckets.get(6));

        buckets.add(6, Duration.ofMillis(160));
        assertEquals(Duration.ofMillis(160), buckets.get(6));

        ArrayList<Duration> additionalDurations = new ArrayList<>();
        additionalDurations.add(Duration.ofMillis(200));
        additionalDurations.add(Duration.ofMillis(210));
        buckets.addAll(additionalDurations);

        additionalDurations = new ArrayList<>();
        additionalDurations.add(Duration.ofMillis(180));
        additionalDurations.add(Duration.ofMillis(190));
        buckets.addAll(8, additionalDurations);

        buckets.add(Duration.ofMillis(220));
        buckets.add(Duration.ofMillis(220));

        assertEquals(12, buckets.indexOf(Duration.ofMillis(220)));
        assertEquals(13, buckets.lastIndexOf(Duration.ofMillis(220)));

        buckets.remove(12);
        assertEquals(13, buckets.size());
        assertEquals(Duration.ofMillis(220), buckets.get(12));
        buckets.remove(12);
        assertEquals(12, buckets.size());

        ListIterator<Duration> iter = buckets.listIterator(11);
        assertTrue(iter.hasNext());
        assertTrue(iter.hasPrevious());
        assertEquals(Duration.ofMillis(210), iter.next());
        assertFalse(iter.hasNext());

        assertTrue(buckets.containsAll(additionalDurations));

        List<Duration> durationList = buckets.subList(0, 2);
        assertEquals(2, durationList.size());
        assertEquals(Duration.ofMillis(100), buckets.get(0));
        assertEquals(Duration.ofMillis(110), buckets.get(1));

        assertTrue(buckets.retainAll(durationList));
        assertEquals(2, buckets.size());

        assertArrayEquals(new Duration[]{Duration.ofMillis(100), Duration.ofMillis(110)}, buckets.toArray());

        durationList = new ArrayList<>();
        durationList.add(Duration.ofMillis(100));
        assertTrue(buckets.removeAll(durationList));
        assertEquals(1, buckets.size());
        assertEquals(Duration.ofMillis(110), buckets.get(0));

        Duration[] durationArr = new Duration[1];
        assertArrayEquals(durationArr, buckets.toArray(durationArr));

        assertFalse(buckets.remove(Duration.ofMillis(999)));
        assertTrue(buckets.remove(Duration.ofMillis(110)));

        assertEquals(0, buckets.size());

        durationList.add(Duration.ofMillis(110));
        durationList.add(Duration.ofMillis(120));
        durationList.add(Duration.ofMillis(130));
        durationList.add(Duration.ofMillis(140));
        buckets.addAll(durationList);

        assertEquals(5, buckets.size());

        buckets.clear();
        assertEquals(0, buckets.size());
    }

    @Test
    public void asValues() {
        DurationBuckets buckets = new DurationBuckets(new Duration[]{
            Duration.ofNanos(10),
            Duration.ofNanos(30),
            Duration.ofNanos(55),
            Duration.ofNanos(200),
            Duration.ofNanos(1000)
        });

        Double[] expectedBuckets = new Double[]{
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

    @Test
    public void custom() {
        DurationBuckets expectedBuckets = new DurationBuckets(
            new Duration[]{
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                Duration.ofMillis(3),
                Duration.ofMillis(5),
                Duration.ofMillis(7),
                Duration.ofMillis(10),
            }
        );

        assertThat("custom buckets are created as per our expectations",
            DurationBuckets.custom(
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                Duration.ofMillis(3),
                Duration.ofMillis(5),
                Duration.ofMillis(7),
                Duration.ofMillis(10)
            ),
            CoreMatchers.equalTo(expectedBuckets));
    }

    @Test(expected = IllegalArgumentException.class)
    public void customFailWithEmptyBuckets() {
        DurationBuckets.custom();
    }

    @Test(expected = IllegalArgumentException.class)
    public void customFailWithUnsortedBuckets() {
        DurationBuckets.custom(
            Duration.ofMillis(1),
            Duration.ofMillis(3),
            Duration.ofMillis(2)
        );
    }

    @Test
    public void testToString() {
        DurationBuckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofMillis(10), 6);
        assertEquals("[0s, 10ms, 20ms, 30ms, 40ms, 50ms]", buckets.toString());

        buckets = new DurationBuckets();
        assertEquals("[]", buckets.toString());

        buckets = new DurationBuckets(new Duration[]{Duration.ofHours(1)});
        assertEquals("[1h]", buckets.toString());
    }

    @Test
    public void equalsHashcode() {
        DurationBuckets buckets = DurationBuckets.linear(Duration.ZERO, Duration.ofSeconds(10), 3);
        DurationBuckets sameBuckets = DurationBuckets.linear(Duration.ZERO, Duration.ofSeconds(10), 3);

        assertEquals(buckets, sameBuckets);
        assertEquals(buckets.hashCode(), sameBuckets.hashCode());

        assertNotEquals(null, buckets);
        assertNotEquals(9, buckets);
    }
}
