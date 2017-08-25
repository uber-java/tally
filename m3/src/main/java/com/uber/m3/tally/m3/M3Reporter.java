// Copyright (c) 2017 Uber Technologies, Inc.
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

package com.uber.m3.tally.m3;

import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.CachedCounter;
import com.uber.m3.tally.CachedGauge;
import com.uber.m3.tally.CachedHistogram;
import com.uber.m3.tally.CachedStatsReporter;
import com.uber.m3.tally.CachedTimer;
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.util.ImmutableMap;

import java.util.Map;

public class M3Reporter implements CachedStatsReporter, AutoCloseable {
    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = 1440;
    private static final String DEFAULT_HISTOGRAM_BUCKET_ID_NAME = "bucketid";
    private static final String DEFAULT_HISTOGRAM_BUCKET_NAME = "bucket";
    private static final int DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION = 6;

    private String[] hostPorts;
    private String service;
    private String env;
    private ImmutableMap<String, String> commonTags;
    private boolean includeHost;
    //TODO protocol?
    private int maxQueueSize;
    private int maxPacketSizeBytes;
    private String histogramBucketIdName;
    private String histogramBucketName;
    private int histogramBucketTagPrecision;

    private M3Reporter(Builder builder) {
        hostPorts = builder.hostPorts;
        service = builder.service;
        env = builder.env;
        commonTags = builder.commonTags;
        includeHost = builder.includeHost;
        maxQueueSize = builder.maxQueueSize;
        maxPacketSizeBytes = builder.maxPacketSizeBytes;
        histogramBucketIdName = builder.histogramBucketIdName;
        histogramBucketName = builder.histogramBucketName;
        histogramBucketTagPrecision = builder.histogramBucketTagPrecision;
    }

    @Override
    public CachedCounter allocateCounter(String name, Map<String, String> tags) {
        //TODO
        return null;
    }

    @Override
    public CachedGauge allocateGauge(String name, Map<String, String> tags) {
        //TODO
        return null;
    }

    @Override
    public CachedTimer allocateTimer(String name, Map<String, String> tags) {
        //TODO
        return null;
    }

    @Override
    public CachedHistogram allocateHistogram(String name, Map<String, String> tags, Buckets buckets) {
        //TODO
        return null;
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING_TAGGING;
    }

    @Override
    public void flush() {
        //TODO
    }

    @Override
    public void close() {
        //TODO
    }

    public static class Builder {
        private String[] hostPorts;
        private String service;
        private String env;
        private ImmutableMap<String, String> commonTags;
        private boolean includeHost;
        private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        private int maxPacketSizeBytes = DEFAULT_MAX_PACKET_SIZE;
        private String histogramBucketIdName = DEFAULT_HISTOGRAM_BUCKET_ID_NAME;
        private String histogramBucketName = DEFAULT_HISTOGRAM_BUCKET_NAME;
        private int histogramBucketTagPrecision = DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION;

        public Builder hostPorts(String[] hostPorts) {
            this.hostPorts = hostPorts;

            return this;
        }

        public Builder service(String service) {
            this.service = service;

            return this;
        }

        public Builder env(String env) {
            this.env = env;

            return this;
        }

        public Builder commonTags(ImmutableMap<String, String> commonTags) {
            this.commonTags = commonTags;

            return this;
        }

        public Builder includeHost(boolean includeHost) {
            this.includeHost = includeHost;

            return this;
        }

        public Builder maxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;

            return this;
        }

        public Builder maxPacketSizeBytes(int maxPacketSizeBytes) {
            this.maxPacketSizeBytes = maxPacketSizeBytes;

            return this;
        }

        public Builder histogramBucketIdName(String histogramBucketIdName) {
            this.histogramBucketIdName = histogramBucketIdName;

            return this;
        }

        public Builder histogramBucketName(String histogramBucketName) {
            this.histogramBucketName = histogramBucketName;

            return this;
        }

        public Builder histogramBucketTagPrecision(int histogramBucketTagPrecision) {
            this.histogramBucketTagPrecision = histogramBucketTagPrecision;

            return this;
        }

        public M3Reporter build() {
            return new M3Reporter(this);
        }
    }
}
