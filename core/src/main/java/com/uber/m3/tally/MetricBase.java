package com.uber.m3.tally;

import com.uber.m3.util.ImmutableMap;

abstract class MetricBase {

    private final String fullyQualifiedName;

    protected MetricBase(String fqn) {
        this.fullyQualifiedName = fqn;
    }

    String getQualifiedName() {
        return fullyQualifiedName;
    }

    abstract void report(String name, ImmutableMap<String, String> tags, StatsReporter reporter);
}
