package com.uber.m3.tally;

import java.util.Map;

import com.uber.m3.util.Duration;

/**
 * Noop implementation of Scope.
 */
public class NoopScope implements Scope {

    static final Counter NOOP_COUNTER = (delta) -> {
    };

    static final Gauge NOOP_GAUGE = (value) -> {
    };

    static final Stopwatch NOOP_STOPWATCH = new Stopwatch(0L, (s) -> {
    });

    static final Timer NOOP_TIMER = new Timer() {
        @Override
        public void record(Duration interval) {
        }

        @Override
        public Stopwatch start() {
            return NOOP_STOPWATCH;
        }
    };

    static final Histogram NOOP_HISTOGRAM = new Histogram() {
        @Override
        public void recordValue(double value) {
        }

        @Override
        public void recordDuration(Duration value) {
        }

        @Override
        public Stopwatch start() {
            return NOOP_STOPWATCH;
        }
    };

    static final Capabilities NOOP_CAPABILITIES = new Capabilities() {
        @Override
        public boolean reporting() {
            return false;
        }

        @Override
        public boolean tagging() {
            return false;
        }
    };

    @Override
    public Counter counter(String name) {
        return NOOP_COUNTER;
    }

    @Override
    public Gauge gauge(String name) {
        return NOOP_GAUGE;
    }

    @Override

    public Timer timer(String name) {
        return NOOP_TIMER;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Histogram histogram(String name, Buckets buckets) {
        return NOOP_HISTOGRAM;
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
        return NOOP_CAPABILITIES;
    }

    @Override
    public void close() {
    }
}
