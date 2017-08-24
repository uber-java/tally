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

import static org.junit.Assert.assertEquals;

public class ScopeImplTest {
    private static final double EPSILON = 1e-10;

    @Test
    public void close() {
        TestStatsReporter reporter = new TestStatsReporter();

        // Construct scope using test reporter reporting every minute
        try (Scope scope = new RootScopeBuilder().reporter(reporter).reportEvery(Duration.ofMinutes(1))) {
            // Create a gauge, update it, and let the AutoCloseable interface
            // functionality close the scope right away.
            Gauge shortLifeGauge = scope.gauge("shortLifeGauge");
            shortLifeGauge.update(123);
        } catch (ScopeCloseException e) {
            System.err.println("Error closing scope: " + e.getMessage());
        }

        // Make sure the reporter received gauge update
        assertEquals(123, reporter.prevGauge, EPSILON);
    }
}
