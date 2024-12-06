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

/**
 * A stopwatch that is used to record for {@link Timer}s and {@link Histogram}s. This implementation
 * relies on values being recorded as nanosecond-level timestamps. There is no
 * assumption that {@code startNanos} is related to the current time, but successive recordings
 * of the stopwatch are comparable with one another.
 */
public class Stopwatch {
    private final long startNanos;
    private final StopwatchRecorder recorder;

    /**
     * Creates a stopwatch.
     * @param startNanos initial value to set the {@link Stopwatch} to. Not necessarily related
     *                   to current time
     * @param recorder   the recorder used to record this {@link Stopwatch}
     */
    public Stopwatch(long startNanos, StopwatchRecorder recorder) {
        this.startNanos = startNanos;
        this.recorder = recorder;
    }

    /**
     * Stop the stopwatch.
     */
    public void stop() {
        recorder.recordStopwatch(startNanos);
    }

    /**
     * Stop the stopwatch.
     * @deprecated because the wrong casing was used. Use {@link #stop()} instead.
     */
    @Deprecated
    public void Stop() {
        stop();
    }
}
