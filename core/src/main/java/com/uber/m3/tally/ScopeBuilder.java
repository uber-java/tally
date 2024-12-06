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

import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Builder class to create {@link Scope}s.
 */
public class ScopeBuilder {
    private static final String DEFAULT_SEPARATOR = ".";
    private static final Buckets DEFAULT_SCOPE_BUCKETS = new DurationBuckets(new Duration[] {
        Duration.ZERO,
        Duration.ofMillis(10),
        Duration.ofMillis(15),
        Duration.ofMillis(50),
        Duration.ofMillis(75),
        Duration.ofMillis(100),
        Duration.ofMillis(200),
        Duration.ofMillis(300),
        Duration.ofMillis(400),
        Duration.ofMillis(500),
        Duration.ofMillis(600),
        Duration.ofMillis(800),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5),
    });

    protected StatsReporter reporter = null;
    protected String prefix = "";
    protected String separator = DEFAULT_SEPARATOR;
    protected ImmutableMap<String, String> tags;
    protected Buckets defaultBuckets = DEFAULT_SCOPE_BUCKETS;
    protected MonotonicClock clock = MonotonicClock.system();

    private ScheduledExecutorService scheduler;
    private ScopeImpl.Registry registry;

    // Protected constructor. Clients should use `RootScopeBuilder` and rely on `reportEvery`
    // to create root scopes, and a root scope's `tagged` and `subScope` functions to create subscopes.
    protected ScopeBuilder(ScheduledExecutorService scheduler, ScopeImpl.Registry registry) {
        this.scheduler = scheduler;
        this.registry = registry;
    }

    /**
     * Updates the reporter.
     * @param reporter value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder reporter(StatsReporter reporter) {
        this.reporter = reporter;
        return this;
    }

    /**
     * Updates the prefix.
     * @param prefix value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * Updates the separator.
     * @param separator value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder separator(String separator) {
        this.separator = separator;
        return this;
    }

    /**
     * Updates the tags, cloning the tags map to an ImmutableMap.
     * @param tags value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder tags(Map<String, String> tags) {
        this.tags = new ImmutableMap<>(tags);

        return this;
    }

    /**
     * Updates the tags. Since this function takes an ImmutableMap, we don't need to clone it.
     * @param tags value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder tags(ImmutableMap<String, String> tags) {
        this.tags = tags;

        return this;
    }

    /**
     * Updates the defaultBuckets.
     * @param defaultBuckets value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder defaultBuckets(Buckets defaultBuckets) {
        this.defaultBuckets = defaultBuckets;
        return this;
    }

    /**
     * Updates the monotonic clock that should be used when measuring elapsed time.
     * @param clock value to update to
     * @return Builder with new param updated
     */
    public ScopeBuilder clock(MonotonicClock clock) {
        this.clock = clock;
        return this;
    }

    // Private build method - clients should rely on `reportEvery` to create root scopes, and
    // a root scope's `tagged` and `subScope` functions to create subscopes.
    ScopeImpl build() {
        return new ScopeImpl(scheduler, registry, this);
    }

    /**
     * Creates a root scope and starts reporting with the specified interval.
     * @param interval duration between each report
     * @return the root scope created
     */
    public Scope reportEvery(Duration interval) {
        return reportEvery(interval, null);
    }

    /**
     * Creates a root scope and starts reporting with the specified interval.
     * @param interval duration between each report
     * @param uncaughtExceptionHandler an  {@link java.lang.Thread.UncaughtExceptionHandler} that's
     *                                 called when there's an uncaught exception in the report loop
     * @return the root scope created
     */
    public Scope reportEvery(Duration interval,
                             Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        if (interval.compareTo(Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("Reporting interval must be a positive Duration");
        }

        ScopeImpl scope = build();
        registry.subscopes.put(ScopeImpl.keyForPrefixedStringMap(prefix, tags), scope);

        scheduler.scheduleWithFixedDelay(scope.new ReportLoop(uncaughtExceptionHandler), 0, interval.toMillis(), TimeUnit.MILLISECONDS);

        return scope;
    }
}
