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

package com.uber.m3.tally.example;

import com.uber.m3.tally.Counter;
import com.uber.m3.tally.Gauge;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.ScopeCloseException;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.tally.Timer;
import com.uber.m3.util.Duration;

/**
 * PrintStatsReporterExample usage with a PrintStatsReporter reporting synthetically emitted metrics
 */
public class PrintStatsReporterExample {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("\n-----------\nStarting...\n-----------");

        StatsReporter reporter = new PrintStatsReporter();

        // Set up rootScope, automatically starting to report every second
        try (Scope rootScope = new RootScopeBuilder().reporter(reporter).reportEvery(Duration.ofSeconds(1))) {
            Scope subScope = rootScope.subScope("requests");

            Counter exampleCounter = subScope.counter("ticks");
            exampleCounter.inc(1);
            exampleCounter.inc(1);

            Gauge exampleGauge = rootScope.gauge("thing");
            exampleGauge.update(42.1);

            Timer exampleTimer = rootScope.timer("timings");
            exampleTimer.record(Duration.ofMillis(3200));
        } catch (ScopeCloseException e) {
            System.err.println("Error closing scope:\n" + e.getMessage());
        }

        System.out.println("-----------\nDone!\n-----------");
    }
}
