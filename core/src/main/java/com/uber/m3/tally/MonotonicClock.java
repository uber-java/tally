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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.uber.m3.util.Duration;

/**
 * An interface for a monotonic clock. This is specially useful when manipulating time in tests.
 *
 * <p>This interface is used in time dependent sensors rather than {@link java.time.Clock} because
 * we are only interested in measuring elapsed time and don't want to incur additional complexity
 * due to the nature of wall-clocks time (e.g., zones, offsets, etc.).
 */
public interface MonotonicClock {
    /**
     * Returns the current value of a monotonically increasing clock measured in nanoseconds This
     * method can only be used to measure elapsed time and is not related to any other notion of
     * system or wall-clock time. The value returned represents nanoseconds since some fixed but
     * arbitrary origin time (perhaps in the future, so values may be negative). The same origin is
     * used by all invocations of this method in an instance of a Java virtual machine; other virtual
     * machine instances are likely to use a different origin.
     */
    long nowNanos();

    /**
     * Returns a fake monotonic clock that can be manipulated for testing purposes.
     */
    static MonotonicClock.FakeClock fake() {
        return FakeClock.create();
    }

    /**
     * Returns a monotonic clock based on {@link System#nanoTime()}.
     */
    static MonotonicClock system() {
        return new SystemClock();
    }

    /**
     * A fake monotonic clock that can be manipulated for testing purposes.
     */
    class FakeClock implements MonotonicClock {
        private final AtomicLong nowNanos = new AtomicLong(0);
        private volatile long autoAdvanceNanos = 0;

        // Private constructor to prevent instantiation. Use factory methods instead.
        private FakeClock() {
        }

        /**
         * Creates a new instance of {@link FakeClock}.
         */
        static FakeClock create() {
            return new FakeClock();
        }

        /**
         * Adds nanoseconds to the current measure of time.
         */
        public void addNanos(long nanos) {
            this.nowNanos.addAndGet(nanos);
        }

        /**
         * Adds the specified duration to the current measure of time.
         */
        public void addDuration(Duration duration) {
            addNanos(TimeUnit.MILLISECONDS.toNanos(duration.toMillis()));
        }

        /**
         * Advances the clock by the given nanoseconds every time {@link #nowNanos()} is called.
         */
        public void autoAdvanceNanos(long nanos) {
            this.autoAdvanceNanos = nanos;
        }

        /**
         * Advances the clock by the given duration every time {@link #nowNanos()} is called.
         */
        public void autoAdvanceDuration(Duration duration) {
            autoAdvanceNanos(TimeUnit.MILLISECONDS.toNanos(duration.toMillis()));
        }

        @Override
        public long nowNanos() {
            return nowNanos.updateAndGet(operand -> operand + autoAdvanceNanos);
        }
    }

    /**
     * A monotonic clock implementation that uses {@link System#nanoTime()}
     */
    class SystemClock implements MonotonicClock {
        private SystemClock() {
        }

        @Override
        public long nowNanos() {
            return System.nanoTime();
        }
    }
}
