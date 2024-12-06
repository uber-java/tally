// Copyright (c) 2023 Uber Technologies, Inc.
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

import java.util.Map;

/**
 * TestScope is a metrics collector that has no reporting, ensuring that
 * all emitted values have a given prefix or set of tags.
 */
public interface TestScope extends Scope {

    /**
     * Creates a new TestScope that adds the ability to take snapshots of
     * metrics emitted to it.
     */
    static TestScope create() {
        return new RootScopeBuilder()
            .reporter(new SnapshotBasedStatsReporter())
            .build();
    }

    /**
     * Creates a new TestScope that adds the ability to manipulate time and take snapshots of
     * metrics emitted to it.
     */
    static TestScope create(MonotonicClock clock) {
        return new RootScopeBuilder()
            .clock(clock)
            .reporter(new SnapshotBasedStatsReporter())
            .build();
    }

    /**
     * Creates a new TestScope with given prefix/tags that adds the ability to
     * take snapshots of metrics emitted to it.
     */
    static TestScope create(String prefix, Map<String, String> tags) {
        return new RootScopeBuilder()
            .prefix(prefix)
            .tags(tags)
            .reporter(new SnapshotBasedStatsReporter())
            .build();
    }

    /**
     * Creates a new TestScope with given prefix/tags that adds the ability to manipulate time and
     * take snapshots of metrics emitted to it.
     */
    static TestScope create(String prefix, Map<String, String> tags, MonotonicClock clock) {
        return new RootScopeBuilder()
            .clock(clock)
            .prefix(prefix)
            .tags(tags)
            .reporter(new SnapshotBasedStatsReporter())
            .build();
    }

    /**
     * Snapshot returns a copy of all values since the last report execution.
     * This is an expensive operation and should only be used for testing purposes.
     */
    Snapshot snapshot();
}
