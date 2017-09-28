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

package com.uber.m3.util;

/**
 * Custom duration class to avoid Java 8 dependency.
 */
public class Duration implements Comparable<Duration> {
    /**
     * A {@link Duration} of zero length.
     */
    public static final Duration ZERO = new Duration(0);

    /**
     * The minimum {@link Duration} value.
     */
    public static final Duration MIN_VALUE = new Duration(Long.MIN_VALUE);

    /**
     * The maximum {@link Duration} value.
     */
    public static final Duration MAX_VALUE = new Duration(Long.MAX_VALUE);

    /**
     * The number of nanoseconds in a microsecond.
     */
    public static final long NANOS_PER_MICRO = 1_000;

    /**
     * The number of nanoseconds in a millisecond.
     */
    public static final long NANOS_PER_MILLI = 1_000_000;

    /**
     * The number of milliseconds in a second.
     */
    public static final long MILLIS_PER_SECOND = 1000;

    /**
     * The number of seconds in a minute.
     */
    public static final long SECONDS_PER_MINUTE = 60;

    /**
     * The number of minutes in an hour.
     */
    public static final long MINUTES_PER_HOUR = 60;

    /**
     * The number of nanoseconds in a second.
     */
    public static final long NANOS_PER_SECOND = NANOS_PER_MILLI * MILLIS_PER_SECOND;

    /**
     * The number of nanoseconds in a minute.
     */
    public static final long NANOS_PER_MINUTE = NANOS_PER_SECOND * SECONDS_PER_MINUTE;

    /**
     * The number of nanoseconds in an hour.
     */
    public static final long NANOS_PER_HOUR = NANOS_PER_MINUTE * MINUTES_PER_HOUR;

    // Long.MAX_VALUE nanos > 290 years, which should be good enough
    private final long nanos;

    private Duration(long nanos) {
        this.nanos = nanos;
    }

    /**
     * Helper function to create a {@link Duration} using nanos.
     * Truncates any fractional nanoseconds.
     * @param nanos number of nanos in this {@link Duration}
     * @return a {@link Duration} of these nanos
     */
    public static Duration ofNanos(long nanos) {
        return new Duration(nanos);
    }

    /**
     * Helper function to create a {@link Duration} using millis.
     * Truncates any fractional nanoseconds.
     * @param millis number of millis in this {@link Duration}
     * @return a {@link Duration} of these millis
     */
    public static Duration ofMillis(double millis) {
        checkMultiplicationLongOverflow(millis, NANOS_PER_MILLI);

        return new Duration((long) (millis * NANOS_PER_MILLI));
    }

    /**
     * Helper function to create a {@link Duration} using seconds.
     * Truncates any fractional nanoseconds.
     * @param seconds number of seconds in this {@link Duration}
     * @return a {@link Duration} of these seconds
     */
    public static Duration ofSeconds(double seconds) {
        checkMultiplicationLongOverflow(seconds, NANOS_PER_SECOND);

        return new Duration((long) (seconds * NANOS_PER_SECOND));
    }

    /**
     * Helper function to create a {@link Duration} using minutes.
     * Truncates any fractional nanoseconds.
     * @param minutes number of minutes in this {@link Duration}
     * @return a {@link Duration} of these minutes
     */
    public static Duration ofMinutes(double minutes) {
        checkMultiplicationLongOverflow(minutes, NANOS_PER_MINUTE);

        return new Duration((long) (minutes * NANOS_PER_MINUTE));
    }

    /**
     * Helper function to create a {@link Duration} using hours.
     * Truncates any fractional nanoseconds.
     * @param hours number of hours in this {@link Duration}
     * @return a {@link Duration} of these hours
     */
    public static Duration ofHours(double hours) {
        checkMultiplicationLongOverflow(hours, NANOS_PER_HOUR);

        return new Duration((long) (hours * NANOS_PER_HOUR));
    }

    /**
     * Returns the number of whole milliseconds. Note that this function loses
     * sub-millisecond precision. Used in functions that only support
     * millisecond-level granularity.
     * @return the number of whole milliseconds in this {@link Duration}
     */
    public long toMillis() {
        return nanos / NANOS_PER_MILLI;
    }

    /**
     * Returns the number of seconds in this {@link Duration} as a double.
     * @return the number of seconds in this {@link Duration}
     */
    public long getNanos() {
        return nanos;
    }

    /**
     * Returns the number of seconds in this {@link Duration} as a double.
     * @return the number of seconds in this {@link Duration}
     */
    public double getSeconds() {
        return (double) nanos / (double) NANOS_PER_SECOND;
    }

    /**
     * Returns a {@link Duration} representing the time between two nanosecond-level numbers.
     * @param startNanos start nanosecond time
     * @param endNanos   end nanosecond time
     * @return a {@link Duration} between two nanosecond-level numbers
     */
    public static Duration between(long startNanos, long endNanos) {
        return new Duration(endNanos - startNanos);
    }

    /**
     * Allows {@link Duration}s to be compared and sorted.
     * @param other the other {@link Duration} to compare with
     * @return -1 if this {@link Duration} is shorter than other; 0 if the same; 1 if longer
     */
    @Override
    public int compareTo(Duration other) {
        return Long.compare(nanos, other.nanos);
    }

    @Override
    public String toString() {
        if (nanos == 0) {
            return "0s";
        } else if (nanos == Long.MIN_VALUE) {
            // Return hard coded response as workaround because
            // Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE
            return "-2562047h47m16.854775808s";
        }

        boolean isNegative = nanos < 0;

        long nanosLocal = Math.abs(nanos);

        long hours = nanosLocal / NANOS_PER_HOUR;
        int minutes = (int) ((nanosLocal % NANOS_PER_HOUR) / NANOS_PER_MINUTE);
        long secondsInNanos = nanosLocal % NANOS_PER_MINUTE;
        int nanoOffset = (int) (nanosLocal % NANOS_PER_SECOND);

        StringBuilder buf = new StringBuilder(24);

        if (isNegative) {
            buf.append("-");
        }
        if (hours != 0) {
            buf.append(hours).append("h");
        }
        if (minutes != 0) {
            buf.append(minutes).append("m");
        }
        if (secondsInNanos / NANOS_PER_SECOND > 0) {
            appendDurationSegment(buf, secondsInNanos, 9, "s");
        } else if (nanoOffset > 0) {
            // If less than one second, print the highest unit that is >1
            if (nanoOffset / NANOS_PER_MILLI > 0) {
                appendDurationSegment(buf, nanoOffset, 6, "ms");
            } else if (nanoOffset / NANOS_PER_MICRO > 0) {
                appendDurationSegment(buf, nanoOffset % NANOS_PER_MILLI, 3, "µs");
            } else {
                buf.append(nanoOffset).append("ns");
            }
        }

        return buf.toString();
    }

    private static void appendDurationSegment(
        StringBuilder buf,
        long durationPart,
        int exponent,
        String suffix
    ) {
        buf.append(durationPart);

        int decimalIdx = buf.length() - exponent;

        // Remove trailing 0s if they come after decimal
        while (decimalIdx < buf.length() && buf.charAt(buf.length() - 1) == '0') {
            buf.setLength(buf.length() - 1);
        }

        if (decimalIdx < buf.length()) {
            buf.insert(decimalIdx, '.');
        }

        buf.append(suffix);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof Duration)) {
            return false;
        }

        return nanos == ((Duration) other).nanos;
    }

    @Override
    public int hashCode() {
        return (int) (nanos ^ (nanos >>> 32));
    }

    /**
     * Returns a {@link Duration} of the sum of this and another {@link Duration}.
     * @param otherDuration the other {@link Duration} to add to
     * @return a {@link Duration} of the sum of this and another {@link Duration}
     */
    public Duration add(Duration otherDuration) {
        checkAdditionLongOverflow(nanos, otherDuration.nanos);

        return new Duration(nanos + otherDuration.nanos);
    }

    /**
     * Returns a {@link Duration} of the product of this with a factor.
     * @param factor the factor to multiply this with
     * @return a {@link Duration} of the product of this with a factor
     */
    public Duration multiply(double factor) {
        checkMultiplicationLongOverflow(nanos, factor);

        long newNanos = (long) (nanos * factor);

        // Throw if multiplying by the given factor will result in a fractional nanosecond
        if (newNanos != nanos * factor) {
            throw new IllegalArgumentException(
                "Unable to create a precise Duration with the specified factor"
            );
        }

        return new Duration(newNanos);
    }

    // Check whether addition will cause long overflow
    private static void checkAdditionLongOverflow(long input1, long input2) {
        long sum = input1 + input2;

        if (((input1 ^ sum) & (input2 ^ sum)) < 0) {
            throw new ArithmeticException(
                String.format("Addition of %d and %d will cause long overflow", input1, input2)
            );
        }
    }

    // Check whether the input to static factory methods will cause long overflow.
    private static void checkMultiplicationLongOverflow(double input, double factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor cannot be negative");
        }

        if (input > 0 && input > Long.MAX_VALUE / factor
            || input < 0 && input < Long.MIN_VALUE / factor) {
            throw new ArithmeticException("Duration of this magnitude not supported");
        }
    }
}
