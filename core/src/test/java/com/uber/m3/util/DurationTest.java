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

package com.uber.m3.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DurationTest {
    private static final double EPSILON = 1e-10;

    @Test
    public void ofNanos() {
        Duration duration = Duration.ofNanos(123_456_789L);
        assertEquals(123_456_789L, duration.getNanos());
    }

    @Test
    public void ofMillis() {
        Duration duration = Duration.ofMillis(42);
        // 42 * 1e6
        assertEquals(42_000_000L, duration.getNanos());
    }

    @Test
    public void ofSeconds() {
        Duration duration = Duration.ofSeconds(6);
        // 6 * 1e9
        assertEquals(6_000_000_000L, duration.getNanos());
    }

    @Test
    public void ofMinutes() {
        Duration duration = Duration.ofMinutes(5);
        // 5 * 60 * 1e9
        assertEquals(300_000_000_000L, duration.getNanos());
    }

    @Test
    public void ofHours() {
        Duration duration = Duration.ofHours(1);
        // 1 * 60 * 60 * 1e9
        assertEquals(3_600_000_000_000L, duration.getNanos());
    }

    @Test
    public void toMillis() {
        Duration duration = Duration.ofMillis(12345);
        assertEquals(12345, duration.toMillis());

        // toMillis() truncates data more precise than millisecond-level
        duration = Duration.ofNanos(5_900_000L);
        assertEquals(5, duration.toMillis());
    }

    @Test
    public void getSeconds() {
        Duration duration = Duration.ofSeconds(8);
        assertEquals(8, duration.getSeconds(), EPSILON);

        duration = Duration.ofNanos(9_123_456_789L);
        assertEquals(9.123_456_789, duration.getSeconds(), EPSILON);
    }

    @Test
    public void between() {
        assertEquals(Duration.ofNanos(-90), Duration.between(100, 10));
        assertEquals(Duration.ofNanos(90), Duration.between(10, 100));
        assertEquals(Duration.ofNanos(200), Duration.between(0, 200));
        assertEquals(Duration.ofNanos(0), Duration.between(255, 255));
    }

    @Test
    public void compareTo() {
        assertEquals(-1, Duration.ofMinutes(1).compareTo(Duration.ofMinutes(2)));
        assertEquals(1, Duration.ofMinutes(9).compareTo(Duration.ofMinutes(4)));
        assertEquals(0, Duration.ofNanos(33).compareTo(Duration.ofNanos(33)));
        assertEquals(0, Duration.ofMinutes(1).compareTo(Duration.ofSeconds(60)));
        assertEquals(-1, Duration.ofNanos(999_999_999).compareTo(Duration.ofSeconds(1)));
        assertEquals(1, Duration.ofMinutes(181).compareTo(Duration.ofHours(3)));
    }

    @Test
    public void equals() {
        assertEquals(Duration.ZERO, Duration.ZERO);
        assertEquals(Duration.ZERO, Duration.ofNanos(0));
        assertEquals(Duration.ofMillis(6000), Duration.ofSeconds(6));
        assertEquals(Duration.ofHours(1), Duration.ofNanos(3_600_000_000_000L));
        assertEquals(Duration.ofHours(1).hashCode(), Duration.ofNanos(3_600_000_000_000L).hashCode());

        assertNotEquals(Duration.ofMillis(6001), Duration.ofSeconds(6));
        assertNotEquals(null, Duration.ZERO);
        assertNotEquals(1, Duration.ZERO);
    }

    @Test
    public void add() {
        assertEquals(Duration.ofMillis(42), Duration.ZERO.add(Duration.ofMillis(42)));
        assertEquals(Duration.ofMillis(42), Duration.ofMillis(20).add(Duration.ofMillis(22)));
        assertEquals(Duration.ofMinutes(42), Duration.ofSeconds(1320).add(Duration.ofMinutes(20)));
        assertEquals(Duration.ofMinutes(50), Duration.ofHours(1).add(Duration.ofSeconds(-600)));
    }

    @Test(expected = ArithmeticException.class)
    public void addException() {
        Duration.MAX_VALUE.add(Duration.ofNanos(1));
    }

    @Test
    public void multiply() {
        assertEquals(Duration.ZERO, Duration.ZERO.multiply(0));
        assertEquals(Duration.ZERO, Duration.ofMillis(1234).multiply(0));
        assertEquals(Duration.ZERO, Duration.ZERO.multiply(4321));
        assertEquals(Duration.ofMillis(8), Duration.ofMillis(4).multiply(2));
        assertEquals(Duration.ofMillis(9000), Duration.ofSeconds(6).multiply(1.5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiplyException1() {
        Duration.ofNanos(3).multiply(1.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void multiplyException2() {
        Duration.ofNanos(3).multiply(-1);
    }

    @Test(expected = ArithmeticException.class)
    public void multiplyException3() {
        Duration.MAX_VALUE.multiply(2);
    }

    @Test
    public void toStringTest() {
        assertEquals("0s", Duration.ZERO.toString());
        assertEquals("1h", Duration.ofHours(1).toString());
        assertEquals("1.234s", Duration.ofMillis(1234).toString());
        assertEquals("2m3.456789s", Duration.ofSeconds(123.456789).toString());
        assertEquals("26h30m", Duration.ofHours(26.5).toString());
        assertEquals("1ns", Duration.ofNanos(1).toString());
        assertEquals("1.03µs", Duration.ofNanos(1_030).toString());
        assertEquals("1.23456789s", Duration.ofNanos(1_234_567_890).toString());
        assertEquals("50ms", Duration.ofMillis(50).toString());
        assertEquals("200µs", Duration.ofNanos(200_000).toString());
        assertEquals("99.009µs", Duration.ofNanos(99_009).toString());

        assertEquals("-1.25s", Duration.ofSeconds(-1.25).toString());
        assertEquals("-5ns", Duration.ofNanos(-5).toString());
        assertEquals("-34h17m36.7s", Duration.ofSeconds(-123456.7).toString());

        assertEquals("2562047h47m16.854775807s", Duration.MAX_VALUE.toString());
        assertEquals("-2562047h47m16.854775808s", Duration.MIN_VALUE.toString());
        assertEquals("-2562047h47m16.854775807s", Duration.ofNanos(Long.MIN_VALUE + 1).toString());
    }
}
