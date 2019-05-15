package com.uber.m3.tally;

import java.util.Map;

import com.uber.m3.util.Duration;

/**
 * Noop implementation of Scope.
 */
public class NoopScope implements Scope {

    @Override
    public Counter counter(String name) {
        return (delta) -> {
        };
    }

    @Override
    public Gauge gauge(String name) {
        return (v) -> {
        };
    }

    @Override

    public Timer timer(String name) {
        return new Timer() {
            @Override
            public void record(Duration interval) {
                // noop
            }

            @Override

            public Stopwatch start() {
                return new Stopwatch(0L, (s) -> {
                });
            }
        };
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Histogram histogram(String name, Buckets buckets) {
        return new Histogram() {
            @Override
            public void recordValue(double value) {
                // noop
            }

            @Override
            public void recordDuration(Duration value) {
                // noop
            }

            @Override

            public Stopwatch start() {
                return new Stopwatch(0L, (s) -> {
                });
            }
        };
    }

    @Override
    public Scope tagged(Map<String, String> tags) {
        return this;
    }

    @Override
    public Scope subScope(String name) {
        return this;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities() {
            @Override
            public boolean reporting() {
                return false;
            }

            @Override
            public boolean tagging() {
                return false;
            }
        };
    }

    @Override
    public void close() {
    }
}
