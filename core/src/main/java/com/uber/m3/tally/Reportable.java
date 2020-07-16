package com.uber.m3.tally;

import com.uber.m3.util.ImmutableMap;

interface Reportable {

    void report(ImmutableMap<String, String> tags, StatsReporter reporter);

}
