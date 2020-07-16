package com.uber.m3.tally;

public interface Metric {

    /**
     * Returns metric's fully-qualified name
     */
    String getQualifiedName();

}
