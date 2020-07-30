package com.uber.m3.tally;

import com.uber.m3.util.Duration;

import java.util.List;

public interface ImmutableBuckets {

    double getValueLowerBoundFor(int bucketIndex);
    double getValueUpperBoundFor(int bucketIndex);

    Duration getDurationLowerBoundFor(int bucketIndex);
    Duration getDurationUpperBoundFor(int bucketIndex);

    int getBucketIndexFor(double value);
    int getBucketIndexFor(Duration value);

    /**
     * Returns these buckets as {@code double}s.
     * @return an immutable list of {@code double}s representing these buckets
     */
    List<Double> getValueBuckets();


    /**
     * Returns these buckets as {@link Duration}s.
     * @return an immutable list of {@link Duration}s representing these buckets
     */
    List<Duration> getDurationBuckets();
}
