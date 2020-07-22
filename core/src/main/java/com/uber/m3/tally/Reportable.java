package com.uber.m3.tally;

import com.uber.m3.util.ImmutableMap;

/**
 * Abstracts capability to report the metrics to {@link StatsReporter}
 */
interface Reportable {

    void report(ImmutableMap<String, String> tags, StatsReporter reporter);

}
