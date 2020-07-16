package com.uber.m3.tally;

public abstract class MetricBase {

    private final String fullyQualifiedName;

    protected MetricBase(String fqn) {
        this.fullyQualifiedName = fqn;
    }

    String getQualifiedName() {
        return fullyQualifiedName;
    }
}
