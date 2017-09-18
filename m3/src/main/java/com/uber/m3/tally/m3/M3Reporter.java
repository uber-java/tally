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

import com.uber.m3.tally.BucketPair;
import com.uber.m3.tally.BucketPairImpl;
import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.CachedCounter;
import com.uber.m3.tally.CachedGauge;
import com.uber.m3.tally.CachedHistogram;
import com.uber.m3.tally.CachedHistogramBucket;
import com.uber.m3.tally.CachedStatsReporter;
import com.uber.m3.tally.CachedTimer;
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.thrift.generated.CountValue;
import com.uber.m3.thrift.generated.GaugeValue;
import com.uber.m3.thrift.generated.M3;
import com.uber.m3.thrift.generated.Metric;
import com.uber.m3.thrift.generated.MetricBatch;
import com.uber.m3.thrift.generated.MetricTag;
import com.uber.m3.thrift.generated.MetricValue;
import com.uber.m3.thrift.generated.TimerValue;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An M3 implementation of a {@link CachedStatsReporter}.
 */
public class M3Reporter implements CachedStatsReporter, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(M3Reporter.class);

    private static final int DEFAULT_METRIC_SIZE = 100;

    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = 1440;

    private static final int THREAD_POOL_SIZE = 10;

    private static final String SERVICE_TAG = "service";
    private static final String ENV_TAG = "env";
    private static final String HOST_TAG = "host";
    private static final String DEFAULT_TAG_VALUE = "default";

    private static final String DEFAULT_HISTOGRAM_BUCKET_ID_NAME = "bucketid";
    private static final String DEFAULT_HISTOGRAM_BUCKET_NAME = "bucket";
    private static final int DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION = 6;

    private static final int EMIT_METRIC_BATCH_OVERHEAD = 19;
    private static final int MIN_METRIC_BUCKET_ID_TAG_LENGTH = 4;

    private M3.Client client;

    private final MetricBatch metricBatch;

    private final Object calcLock = new Object();
    private TCalcTransport calc;
    private TProtocol calcProtocol;

    private int freeBytes;

    private ResourcePool resourcePool;

    private String bucketIdTagName;
    private String bucketTagName;
    private String bucketValFmt;

    // Holds metrics to be flushed
    private BlockingQueue<SizedMetric> metricQueue;
    // The service used to execute metric flushes
    private ExecutorService executor;
    // Used to keep track of how many processors are in progress in
    // order to wait for them to finish before closing
    private Phaser phaser = new Phaser(1);

    // Use inner Builder class to construct an M3Reporter
    private M3Reporter(Builder builder) {
        try {
            // Builder verifies non-null, non-empty hostPorts
            String[] hostPorts = builder.hostPorts;

            TProtocolFactory protocolFactory;
            if (builder.protocol == ThriftProtocol.COMPACT) {
                protocolFactory = new TCompactProtocol.Factory();
            } else {
                protocolFactory = new TBinaryProtocol.Factory();
            }

            TTransport transport = new TUdpClient(hostPorts[0]);

            client = new M3.Client(protocolFactory.getProtocol(transport));
            resourcePool = new ResourcePool(builder.maxQueueSize, protocolFactory);

            Set<MetricTag> commonTags = toMetricTagsToSet(builder.commonTags);

            // Set and ensure required tags
            if (!builder.commonTags.containsKey(SERVICE_TAG)) {
                if (builder.service == null || builder.service.isEmpty()) {
                    throw new IllegalArgumentException(String.format("%s common tag is required", SERVICE_TAG));
                }

                commonTags.add(createMetricTag(SERVICE_TAG, builder.service));
            }
            if (!builder.commonTags.containsKey(ENV_TAG)) {
                if (builder.env == null || builder.env.isEmpty()) {
                    throw new IllegalArgumentException(String.format("%s common tag is required", ENV_TAG));
                }

                commonTags.add(createMetricTag(ENV_TAG, builder.env));
            }
            if (builder.includeHost && !builder.commonTags.containsKey(HOST_TAG)) {
                commonTags.add(createMetricTag(HOST_TAG, getHostName()));
            }

            metricBatch = resourcePool.getMetricBatch();
            calcProtocol = resourcePool.getProtocol();
            calc = (TCalcTransport) calcProtocol.getTransport();

            freeBytes = calculateFreeBytes(builder.maxPacketSizeBytes, commonTags);

            bucketIdTagName = builder.histogramBucketIdName;
            bucketTagName = builder.histogramBucketName;
            bucketValFmt = String.format("%%.%df", builder.histogramBucketTagPrecision);

            metricQueue = new LinkedBlockingQueue<>(builder.maxQueueSize);

            executor = builder.executor;

            addAndRunProcessor();
        } catch (SocketException e) {
            throw new IllegalArgumentException("Exception creating M3Reporter", e);
        }
    }

    private int calculateFreeBytes(int maxPacketSizeBytes, Set<MetricTag> commonTags) {
        metricBatch.setCommonTags(commonTags);
        metricBatch.setMetrics(new ArrayList<Metric>());

        int size;

        try {
            metricBatch.write(calcProtocol);
            size = calc.getSizeAndReset();
        } catch (TException e) {
            LOG.warn("Unable to calculate metric batch size. Defaulting to: " + DEFAULT_METRIC_SIZE);
            size = DEFAULT_METRIC_SIZE;
        }

        int numOverheadBytes = EMIT_METRIC_BATCH_OVERHEAD + size;

        freeBytes = maxPacketSizeBytes - numOverheadBytes;

        if (freeBytes <= 0) {
            throw new IllegalArgumentException("Common tags serialized size exceeds packet size");
        }

        return freeBytes;
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOG.warn("Unable to determine hostname. Defaulting to: " + DEFAULT_TAG_VALUE);
            return DEFAULT_TAG_VALUE;
        }
    }

    private void addAndRunProcessor() {
        phaser.register();

        executor.execute(new Processor());
    }

    @Override
    public CachedCounter allocateCounter(String name, Map<String, String> tags) {
        Metric counter = newMetric(name, tags, MetricType.COUNTER);

        return new CachedMetric(counter, calculateSize(counter));
    }

    @Override
    public CachedGauge allocateGauge(String name, Map<String, String> tags) {
        Metric gauge = newMetric(name, tags, MetricType.GAUGE);

        return new CachedMetric(gauge, calculateSize(gauge));
    }

    @Override
    public CachedTimer allocateTimer(String name, Map<String, String> tags) {
        Metric timer = newMetric(name, tags, MetricType.TIMER);

        return new CachedMetric(timer, calculateSize(timer));
    }

    @Override
    public CachedHistogram allocateHistogram(String name, Map<String, String> tags, Buckets buckets) {
        List<CachedHistogramBucketImpl> cachedValueBuckets = new ArrayList<>();
        List<CachedHistogramBucketImpl> cachedDurationBuckets = new ArrayList<>();

        int bucketIdLen = String.valueOf(buckets.size()).length();
        bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

        String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

        BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(buckets);

        for (int i = 0; i < bucketPairs.length; i++) {
            Map<String, String> valueTags = new HashMap<>(tags);
            Map<String, String> durationTags = new HashMap<>(tags);

            String idTagValue = String.format(bucketIdFmt, i);

            valueTags.put(bucketIdTagName, idTagValue);
            valueTags.put(bucketTagName, String.format("%s-%s",
                valueBucketString(bucketPairs[i].lowerBoundValue()),
                valueBucketString(bucketPairs[i].upperBoundValue())));

            cachedValueBuckets.add(new CachedHistogramBucketImpl(
                bucketPairs[i].upperBoundValue(),
                bucketPairs[i].upperBoundDuration(),
                (CachedMetric) allocateCounter(name, valueTags)
            ));

            durationTags.put(bucketIdTagName, idTagValue);
            durationTags.put(bucketTagName, String.format("%s-%s",
                durationBucketString(bucketPairs[i].lowerBoundDuration()),
                durationBucketString(bucketPairs[i].upperBoundDuration())));

            cachedDurationBuckets.add(new CachedHistogramBucketImpl(
                bucketPairs[i].upperBoundValue(),
                bucketPairs[i].upperBoundDuration(),
                (CachedMetric) allocateCounter(name, durationTags)
            ));
        }

        return new CachedHistogramImpl(
            name,
            tags,
            buckets,
            cachedValueBuckets,
            cachedDurationBuckets
        );
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING_TAGGING;
    }

    @Override
    public void flush() {
        try {
            // A null metric signifies to processors that a flush was called,
            // which causes the metric queue to be cleared
            metricQueue.put(resourcePool.getSizedMetric());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Metric> flush(List<Metric> metrics) throws TException {
        TException exception = null;

        synchronized (metricBatch) {
            metricBatch.setMetrics(metrics);

            try {
                client.emitMetricBatch(metricBatch);
            } catch (TException e) {
                // Store exception to throw later after clean up.
                // Don't want to put everything in a finally block here,
                // which will hold the lock unnecessarily long
                exception = e;
            }

            metricBatch.setMetrics(null);
        }

        resourcePool.releaseShallowMetrics(metrics);

        metrics.clear();

        if (exception != null) {
            throw exception;
        }

        return metrics;
    }

    @Override
    public void close() {
        // Wait (block) until all processors to return from flushing
        phaser.arriveAndAwaitAdvance();
    }

    private Metric newMetric(String name, Map<String, String> tags, MetricType metricType) {
        Metric metric = resourcePool.getMetric();
        metric.setName(name);

        metric.setTags(toMetricTagsToSet(tags));

        metric.setTimestamp(Long.MAX_VALUE);

        MetricValue metricValue = resourcePool.getMetricValue();

        switch (metricType) {
            case COUNTER:
                CountValue countValue = resourcePool.getCountValue();
                countValue.setI64Value(Long.MAX_VALUE);
                metricValue.setCount(countValue);
                break;
            case GAUGE:
                GaugeValue gaugeValue = resourcePool.getGaugeValue();
                gaugeValue.setDValue(Double.MAX_VALUE);
                metricValue.setGauge(gaugeValue);
                break;
            case TIMER:
                TimerValue timerValue = resourcePool.getTimerValue();
                timerValue.setI64Value(Long.MAX_VALUE);
                metricValue.setTimer(timerValue);
                break;
        }

        metric.setMetricValue(metricValue);

        return metric;
    }

    private Set<MetricTag> toMetricTagsToSet(Map<String, String> tags) {
        Set<MetricTag> metricTagSet = resourcePool.getTagList();

        if (tags == null) {
            return metricTagSet;
        }

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            metricTagSet.add(createMetricTag(tag.getKey(), tag.getValue()));
        }

        return metricTagSet;
    }

    private MetricTag createMetricTag(String tagName, String tagValue) {
        MetricTag metricTag = resourcePool.getMetricTag();
        metricTag.setTagName(tagName);

        if (tagValue != null && !tagValue.isEmpty()) {
            metricTag.setTagValue(tagValue);
        }

        return metricTag;
    }

    private int calculateSize(Metric metric) {
        try {
            synchronized (calcLock) {
                metric.write(calcProtocol);

                return calc.getSizeAndReset();
            }
        } catch (TException e) {
            LOG.warn("Unable to calculate metric batch size. Defaulting to: " + DEFAULT_METRIC_SIZE);
            return DEFAULT_METRIC_SIZE;
        }
    }

    private void reportCopyMetric(
        Metric metric,
        int size,
        MetricType metricType,
        long iValue,
        double dValue
    ) {
        Metric metricCopy = resourcePool.getMetric();
        metricCopy.setName(metric.getName());
        metricCopy.setTags(metric.getTags());
        // Unfortunately, there is no finer time resolution for Java 7
        metricCopy.setTimestamp(System.currentTimeMillis() * Duration.NANOS_PER_MILLI);
        metricCopy.setMetricValue(resourcePool.getMetricValue());

        switch (metricType) {
            case COUNTER:
                CountValue countValue = resourcePool.getCountValue();
                countValue.setI64Value(iValue);
                metricCopy.getMetricValue().setCount(countValue);
                break;
            case GAUGE:
                GaugeValue gaugeValue = resourcePool.getGaugeValue();
                gaugeValue.setDValue(dValue);
                metricCopy.getMetricValue().setGauge(gaugeValue);
                break;
            case TIMER:
                TimerValue timerValue = resourcePool.getTimerValue();
                timerValue.setI64Value(iValue);
                metricCopy.getMetricValue().setTimer(timerValue);
                break;
            default:
                throw new IllegalArgumentException("Unsupported metric type: " + metricType);
        }

        try {
            metricQueue.put(resourcePool.getSizedMetric().setMetric(metricCopy).setSize(size));
        } catch (InterruptedException e) {
            LOG.warn(String.format("Interrupted while putting %s: %s",
                metricType,
                metricCopy.getName()));
        }
    }

    private String valueBucketString(double bucketBound) {
        if (bucketBound == Double.MAX_VALUE) {
            return "infinity";
        }

        if (bucketBound == -Double.MAX_VALUE) {
            return "-infinity";
        }

        return String.format(bucketValFmt, bucketBound);
    }

    private String durationBucketString(Duration bucketBound) {
        if (Duration.MAX_VALUE.equals(bucketBound)) {
            return "infinity";
        }

        if (Duration.MIN_VALUE.equals(bucketBound)) {
            return "-infinity";
        }

        return bucketBound.toString();
    }

    private class Processor implements Runnable {
        @Override
        public void run() {
            try {
                List<Metric> metrics = new ArrayList<>(freeBytes / 10);
                int bytes = 0;

                while (!metricQueue.isEmpty()) {
                    SizedMetric sizedMetric = metricQueue.poll();
                    Metric metric = sizedMetric.getMetric();
                    int size = sizedMetric.getSize();

                    resourcePool.releaseSizedMetric(sizedMetric);

                    if (metric == null) {
                        // Explicit flush requested
                        if (metrics.size() > 0) {
                            metrics.clear();
                            bytes = 0;
                        }

                        continue;
                    }

                    if (bytes + size > freeBytes) {
                        metrics = flush(metrics);
                        bytes = 0;
                    }

                    metrics.add(metric);
                    bytes += size;
                }

                if (metrics.size() > 0) {
                    // Final flush
                    flush(metrics);
                }
            } catch (TException e) {
                throw new RuntimeException("Failed to emit metrics", e);
            } finally {
                // Always arrive at phaser to prevent deadlock
                phaser.arrive();
            }
        }
    }

    private class CachedMetric implements CachedCounter, CachedGauge, CachedTimer, CachedHistogramBucket {
        Metric metric;
        int size;

        CachedMetric(Metric metric, int size) {
            this.metric = metric;
            this.size = size;
        }

        @Override
        public void reportCount(long value) {
            reportCopyMetric(metric, size, MetricType.COUNTER, value, 0);
        }

        @Override
        public void reportGauge(double value) {
            reportCopyMetric(metric, size, MetricType.GAUGE, 0, value);
        }

        @Override
        public void reportTimer(Duration interval) {
            reportCopyMetric(metric, size, MetricType.TIMER, interval.getNanos(), 0);
        }

        @Override
        public void reportSamples(long value) {
            reportCopyMetric(metric, size, MetricType.COUNTER, value, 0);
        }
    }

    private class CachedHistogramImpl implements CachedHistogram {
        String name;
        Map<String, String> tags;
        Buckets buckets;
        List<CachedHistogramBucketImpl> cachedValueBuckets;
        List<CachedHistogramBucketImpl> cachedDurationBuckets;

        CachedHistogramImpl(
            String name,
            Map<String, String> tags,
            Buckets buckets,
            List<CachedHistogramBucketImpl> cachedValueBuckets,
            List<CachedHistogramBucketImpl> cachedDurationBuckets
        ) {
            this.name = name;
            this.tags = tags;
            this.buckets = buckets;
            this.cachedValueBuckets = cachedValueBuckets;
            this.cachedDurationBuckets = cachedDurationBuckets;
        }

        @Override
        public CachedHistogramBucket valueBucket(double bucketLowerBound, double bucketUpperBound) {
            for (CachedHistogramBucketImpl bucket : cachedValueBuckets) {
                if (bucket.getValueUpperBound() >= bucketUpperBound) {
                    return bucket.getMetric();
                }
            }

            return null;
        }

        @Override
        public CachedHistogramBucket durationBucket(Duration bucketLowerBound, Duration bucketUpperBound) {
            for (CachedHistogramBucketImpl bucket : cachedValueBuckets) {
                if (bucket.getDurationUpperBound().compareTo(bucketUpperBound) >= 0) {
                    return bucket.getMetric();
                }
            }

            return null;
        }
    }

    private class CachedHistogramBucketImpl {
        private double valueUpperBound;
        private Duration durationUpperBound;
        private CachedHistogramBucket metric;

        CachedHistogramBucketImpl(double valueUpperBound, Duration durationUpperBound, CachedMetric metric) {
            this.valueUpperBound = valueUpperBound;
            this.durationUpperBound = durationUpperBound;
            this.metric = metric;
        }

        double getValueUpperBound() {
            return valueUpperBound;
        }

        Duration getDurationUpperBound() {
            return durationUpperBound;
        }

        CachedHistogramBucket getMetric() {
            return metric;
        }
    }

    /**
     * Builder pattern to construct an {@link M3Reporter}.
     */
    public static class Builder {
        private String[] hostPorts;
        private String service;
        private String env;
        // Non-generic EMPTY ImmutableMap will never contain any elements
        @SuppressWarnings("unchecked")
        private ImmutableMap<String, String> commonTags = ImmutableMap.EMPTY;
        private boolean includeHost;
        private ThriftProtocol protocol = ThriftProtocol.COMPACT;
        private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        private int maxPacketSizeBytes = DEFAULT_MAX_PACKET_SIZE;
        private String histogramBucketIdName = DEFAULT_HISTOGRAM_BUCKET_ID_NAME;
        private String histogramBucketName = DEFAULT_HISTOGRAM_BUCKET_NAME;
        private int histogramBucketTagPrecision = DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION;

        private ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        /**
         * Constructs a {@link Builder}. Having at least one host/port is required.
         * @param hostPorts the array of host/ports
         */
        public Builder(String[] hostPorts) {
            if (hostPorts == null || hostPorts.length == 0) {
                throw new IllegalArgumentException("Must specify at least one host port");
            }

            this.hostPorts = hostPorts;
        }

        /**
         * Configures the service of this {@link Builder}.
         * @param service the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder service(String service) {
            this.service = service;

            return this;
        }

        /**
         * Configures the env of this {@link Builder}.
         * @param env the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder env(String env) {
            this.env = env;

            return this;
        }

        /**
         * Configures the common tags of this {@link Builder}.
         * @param commonTags the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder commonTags(ImmutableMap<String, String> commonTags) {
            this.commonTags = commonTags;

            return this;
        }

        /**
         * Configures whether to include the host tag of this {@link Builder}.
         * @param includeHost the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder includeHost(boolean includeHost) {
            this.includeHost = includeHost;

            return this;
        }

        /**
         * Configures the protocol of this {@link Builder}.
         * @param protocol the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder protocol(ThriftProtocol protocol) {
            this.protocol = protocol;

            return this;
        }

        /**
         * Configures the maximum queue size of this {@link Builder}.
         * @param maxQueueSize the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder maxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;

            return this;
        }

        /**
         * Configures the maximum packet size in bytes of this {@link Builder}.
         * @param maxPacketSizeBytes the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder maxPacketSizeBytes(int maxPacketSizeBytes) {
            this.maxPacketSizeBytes = maxPacketSizeBytes;

            return this;
        }

        /**
         * Configures the histogram bucket ID name of this {@link Builder}.
         * @param histogramBucketIdName the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder histogramBucketIdName(String histogramBucketIdName) {
            this.histogramBucketIdName = histogramBucketIdName;

            return this;
        }

        /**
         * Configures the histogram bucket name of this {@link Builder}.
         * @param histogramBucketName the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder histogramBucketName(String histogramBucketName) {
            this.histogramBucketName = histogramBucketName;

            return this;
        }

        /**
         * Configures the histogram bucket tag precision of this {@link Builder}.
         * @param histogramBucketTagPrecision the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder histogramBucketTagPrecision(int histogramBucketTagPrecision) {
            this.histogramBucketTagPrecision = histogramBucketTagPrecision;

            return this;
        }

        /**
         * Configures the executor service of this {@link Builder}.
         * @param executor the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;

            return this;
        }

        /**
         * Builds and returns an {@link M3Reporter} with the configured paramters.
         * @return a new {@link M3Reporter} instance with the configured paramters
         */
        public M3Reporter build() {
            return new M3Reporter(this);
        }
    }
}
