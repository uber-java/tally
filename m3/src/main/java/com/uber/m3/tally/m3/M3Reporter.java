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
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.tally.m3.thrift.TCalcTransport;
import com.uber.m3.tally.m3.thrift.TMultiUdpClient;
import com.uber.m3.tally.m3.thrift.TUdpClient;
import com.uber.m3.thrift.gen.CountValue;
import com.uber.m3.thrift.gen.GaugeValue;
import com.uber.m3.thrift.gen.M3;
import com.uber.m3.thrift.gen.Metric;
import com.uber.m3.thrift.gen.MetricBatch;
import com.uber.m3.thrift.gen.MetricTag;
import com.uber.m3.thrift.gen.MetricValue;
import com.uber.m3.thrift.gen.TimerValue;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An M3 implementation of a {@link StatsReporter}.
 */
public class M3Reporter implements StatsReporter, AutoCloseable {
    public static final String SERVICE_TAG = "service";
    public static final String ENV_TAG = "env";
    public static final String HOST_TAG = "host";
    public static final String DEFAULT_TAG_VALUE = "default";

    public static final String DEFAULT_HISTOGRAM_BUCKET_ID_NAME = "bucketid";
    public static final String DEFAULT_HISTOGRAM_BUCKET_NAME = "bucket";
    public static final int DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION = 6;

    private static final Logger LOG = LoggerFactory.getLogger(M3Reporter.class);

    private static final int MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS = 1000;

    private static final int DEFAULT_METRIC_SIZE = 100;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = 1440;

    private static final int THREAD_POOL_SIZE = 10;

    private static final int EMIT_METRIC_BATCH_OVERHEAD = 19;
    private static final int MIN_METRIC_BUCKET_ID_TAG_LENGTH = 4;

    private M3.Client client;

    // calcLock protects both calc and calcProtocol
    private final Object calcLock = new Object();
    private TCalcTransport calc;
    private TProtocol calcProtocol;

    private int maxProcessorWaitUntilFlushMillis;

    private int freeBytes;

    private String bucketIdTagName;
    private String bucketTagName;
    private String bucketValFmt;

    // Holds metrics to be flushed
    private final BlockingQueue<SizedMetric> metricQueue;
    // The service used to execute metric flushes
    private ExecutorService executor;
    // Used to keep track of how many processors are in progress in
    // order to wait for them to finish before closing
    private Phaser phaser = new Phaser(1);

    private TTransport transport;

    // Use inner Builder class to construct an M3Reporter
    private M3Reporter(Builder builder) {
        try {
            // Builder verifies non-null, non-empty socketAddresses
            SocketAddress[] socketAddresses = builder.socketAddresses;

            TProtocolFactory protocolFactory = new TCompactProtocol.Factory();

            if (socketAddresses.length > 1) {
                transport = new TMultiUdpClient(socketAddresses);
            } else {
                transport = new TUdpClient(socketAddresses[0]);
            }

            transport.open();

            client = new M3.Client(protocolFactory.getProtocol(transport));

            calcProtocol = new TCompactProtocol.Factory().getProtocol(new TCalcTransport());
            calc = (TCalcTransport) calcProtocol.getTransport();

            freeBytes = calculateFreeBytes(builder.maxPacketSizeBytes, builder.metricTagSet);

            maxProcessorWaitUntilFlushMillis = builder.maxProcessorWaitUntilFlushMillis;

            bucketIdTagName = builder.histogramBucketIdName;
            bucketTagName = builder.histogramBucketName;
            bucketValFmt = String.format("%%.%df", builder.histogramBucketTagPrecision);

            metricQueue = new LinkedBlockingQueue<>(builder.maxQueueSize);

            executor = builder.executor;

            addAndRunProcessor(builder.metricTagSet);
        } catch (TTransportException | SocketException e) {
            throw new RuntimeException("Exception creating M3Reporter", e);
        }
    }

    private int calculateFreeBytes(int maxPacketSizeBytes, Set<MetricTag> commonTags) {
        MetricBatch metricBatch = new MetricBatch();
        metricBatch.setCommonTags(commonTags);
        metricBatch.setMetrics(new ArrayList<Metric>());

        int size;

        try {
            metricBatch.write(calcProtocol);
            size = calc.getSizeAndReset();
        } catch (TException e) {
            LOG.warn("Unable to calculate metric batch size. Defaulting to: {}", DEFAULT_METRIC_SIZE);
            size = DEFAULT_METRIC_SIZE;
        }

        int numOverheadBytes = EMIT_METRIC_BATCH_OVERHEAD + size;

        freeBytes = maxPacketSizeBytes - numOverheadBytes;

        if (freeBytes <= 0) {
            throw new IllegalArgumentException("Common tags serialized size exceeds packet size");
        }

        return freeBytes;
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOG.warn("Unable to determine hostname. Defaulting to: {}", DEFAULT_TAG_VALUE);
            return DEFAULT_TAG_VALUE;
        }
    }

    private void addAndRunProcessor(Set<MetricTag> commonTags) {
        phaser.register();

        executor.execute(new Processor(commonTags));
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING_TAGGING;
    }

    @Override
    public void flush() {
        try {
            metricQueue.put(SizedMetric.FLUSH);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while trying to queue flush sentinel");
        }
    }

    private void flush(List<Metric> metrics, Set<MetricTag> commonTags) {
        if (metrics.isEmpty()) {
            return;
        }

        MetricBatch metricBatch = new MetricBatch();
        metricBatch.setCommonTags(commonTags);
        metricBatch.setMetrics(metrics);

        try {
            client.emitMetricBatch(metricBatch);

        } catch (TException tException) {
            LOG.warn("Failed to flush metrics: " + tException.getMessage());
        }

        metricBatch.setMetrics(null);

        metrics.clear();
    }

    @Override
    public void close() {
        // Put sentinal value in queue so that processors know to disregard anything that comes after it.
        queueSizedMetric(SizedMetric.CLOSE);

        // Important to use `shutdownNow` instead of `shutdown` to interrupt processor
        // thread(s) or else they will block forever
        executor.shutdownNow();

        // Wait a maximum of `MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS` for all processors
        // to return from flushing
        try {
            phaser.awaitAdvanceInterruptibly(
                phaser.arrive(),
                MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS,
                TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException e) {
            LOG.warn(
                "M3Reporter closing before Processors complete after waiting timeout of {}ms!",
                MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS
            );
        } catch (InterruptedException e) {
            LOG.warn("M3Reporter closing before Processors complete due to being interrupted!");
        }

        transport.close();
    }

    private static Set<MetricTag> toMetricTagSet(Map<String, String> tags) {
        Set<MetricTag> metricTagSet = new HashSet<>();

        if (tags == null) {
            return metricTagSet;
        }

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            metricTagSet.add(createMetricTag(tag.getKey(), tag.getValue()));
        }

        return metricTagSet;
    }

    private static MetricTag createMetricTag(String tagName, String tagValue) {
        MetricTag metricTag = new MetricTag(tagName);

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
        if (Duration.ZERO.equals(bucketBound)) {
            return "0";
        }

        if (Duration.MAX_VALUE.equals(bucketBound)) {
            return "infinity";
        }

        if (Duration.MIN_VALUE.equals(bucketBound)) {
            return "-infinity";
        }

        return bucketBound.toString();
    }

    @Override
    public void reportCounter(String name, Map<String, String> tags, long value) {
        reportCounterInternal(name, tags, value);
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        GaugeValue gaugeValue = new GaugeValue();
        gaugeValue.setDValue(value);

        MetricValue metricValue = new MetricValue();
        metricValue.setGauge(gaugeValue);

        Metric metric = newMetric(name, tags, metricValue);

        queueSizedMetric(new SizedMetric(metric, calculateSize(metric)));
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        TimerValue timerValue = new TimerValue();
        timerValue.setI64Value(interval.getNanos());

        MetricValue metricValue = new MetricValue();
        metricValue.setTimer(timerValue);

        Metric metric = newMetric(name, tags, metricValue);

        queueSizedMetric(new SizedMetric(metric, calculateSize(metric)));
    }

    @Override
    public void reportHistogramValueSamples(
        String name,
        Map<String, String> tags,
        Buckets buckets,
        double bucketLowerBound,
        double bucketUpperBound,
        long samples
    ) {
        // Append histogram bucket-specific tags
        int bucketIdLen = String.valueOf(buckets.size()).length();
        bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

        String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

        BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(buckets);

        if (tags == null) {
            // We know that the HashMap will only contain two items at this point,
            // therefore initialCapacity of 2 and loadFactor of 1.
            tags = new HashMap<>(2, 1);
        } else {
            // Copy over the map since it might be unmodifiable and, even if it's
            // not, we don't want to modify it.
            tags = new HashMap<>(tags);
        }

        for (int i = 0; i < bucketPairs.length; i++) {
            // Look for the first pair with an upper bound greater than or equal
            // to the given upper bound.
            if (bucketPairs[i].upperBoundValue() >= bucketUpperBound) {
                String idTagValue = String.format(bucketIdFmt, i);

                tags.put(bucketIdTagName, idTagValue);
                tags.put(bucketTagName,
                    String.format("%s-%s",
                        valueBucketString(bucketPairs[i].lowerBoundValue()),
                        valueBucketString(bucketPairs[i].upperBoundValue())
                    )
                );

                break;
            }
        }

        reportCounterInternal(name, tags, samples);
    }

    @Override
    public void reportHistogramDurationSamples(
        String name,
        Map<String, String> tags,
        Buckets buckets,
        Duration bucketLowerBound,
        Duration bucketUpperBound,
        long samples
    ) {
        // Append histogram bucket-specific tags
        int bucketIdLen = String.valueOf(buckets.size()).length();
        bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

        String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

        BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(buckets);

        if (tags == null) {
            // We know that the HashMap will only contain two items at this point,
            // therefore initialCapacity of 2 and loadFactor of 1.
            tags = new HashMap<>(2, 1);
        } else {
            // Copy over the map since it might be unmodifiable and, even if it's
            // not, we don't want to modify it.
            tags = new HashMap<>(tags);
        }

        for (int i = 0; i < bucketPairs.length; i++) {
            // Look for the first pair with an upper bound greater than or equal
            // to the given upper bound.
            if (bucketPairs[i].upperBoundDuration().compareTo(bucketUpperBound) >= 0) {
                String idTagValue = String.format(bucketIdFmt, i);

                tags.put(bucketIdTagName, idTagValue);
                tags.put(bucketTagName,
                    String.format("%s-%s",
                        durationBucketString(bucketPairs[i].lowerBoundDuration()),
                        durationBucketString(bucketPairs[i].upperBoundDuration())
                    )
                );

                break;
            }
        }

        reportCounterInternal(name, tags, samples);
    }

    // Relies on the calling function to provide guarantees of the reporter being open
    private void reportCounterInternal(String name, Map<String, String> tags, long value) {
        CountValue countValue = new CountValue();
        countValue.setI64Value(value);

        MetricValue metricValue = new MetricValue();
        metricValue.setCount(countValue);

        Metric metric = newMetric(name, tags, metricValue);

        queueSizedMetric(new SizedMetric(metric, calculateSize(metric)));
    }

    private Metric newMetric(String name, Map<String, String> tags, MetricValue metricValue) {
        Metric metric = new Metric(name);
        metric.setTags(toMetricTagSet(tags));
        metric.setTimestamp(System.currentTimeMillis() * Duration.NANOS_PER_MILLI);
        metric.setMetricValue(metricValue);

        return metric;
    }

    private void queueSizedMetric(SizedMetric sizedMetric) {
        try {
            metricQueue.put(sizedMetric);
        } catch (InterruptedException e) {
            LOG.warn(String.format("Interrupted queueing metric: {}", sizedMetric.getMetric().getName()));
        }
    }

    private class Processor implements Runnable {
        final Set<MetricTag> commonTags;
        List<Metric> pendingMetrics = new ArrayList<>(freeBytes / 10);
        int metricsSize = 0;

        Processor(Set<MetricTag> commonTags) {
            this.commonTags = commonTags;
        }

        @Override
        public void run() {
            try {
                while (!executor.isShutdown()) {
                    // This `poll` call will block for at most the specified duration to take an item
                    // off the queue. If we get an item, we append it to the queue to be flushed,
                    // otherwise we flush what we have so far.
                    // When this reporter is closed, shutdownNow will be called on the executor,
                    // which will interrupt this thread and proceed to the `InterruptedException`
                    // catch block.
                    SizedMetric sizedMetric = metricQueue.poll(maxProcessorWaitUntilFlushMillis, TimeUnit.MILLISECONDS);

                    // Drop metrics that came in after close
                    if (sizedMetric == SizedMetric.CLOSE) {
                        metricQueue.clear();
                        break;
                    }

                    if (sizedMetric != null) {
                        flushMetricIteration(sizedMetric);
                        continue;
                    }

                    // If we don't get any new metrics after waiting the specified time,
                    // flush what we have so far.
                    if (!pendingMetrics.isEmpty()) {
                        flushMetricIteration(SizedMetric.FLUSH);
                    }
                }
            } catch (InterruptedException e) {
                // Don't care if we get interrupted - the finally block will clean up
            } finally {
                flushRemainingMetrics();

                // Always arrive at phaser to prevent deadlock
                phaser.arrive();
            }
        }

        private void flushMetricIteration(SizedMetric sizedMetric) {
            Metric metric = sizedMetric.getMetric();

            if (sizedMetric == SizedMetric.FLUSH) {
                flush(pendingMetrics, commonTags);
                return;
            }

            int size = sizedMetric.getSize();

            if (metricsSize + size > freeBytes) {
                flush(pendingMetrics, commonTags);
                metricsSize = 0;
            }

            pendingMetrics.add(metric);

            metricsSize += size;
        }

        private void flushRemainingMetrics() {
            while (!metricQueue.isEmpty()) {
                SizedMetric sizedMetric = metricQueue.remove();

                // Don't care about metrics that came in after close
                if (sizedMetric == SizedMetric.CLOSE) {
                    break;
                }

                flushMetricIteration(sizedMetric);
            }

            flush(pendingMetrics, commonTags);
        }
    }

    /**
     * Builder pattern to construct an {@link M3Reporter}.
     */
    public static class Builder {
        protected SocketAddress[] socketAddresses;
        protected String service;
        protected String env;
        protected ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        // Non-generic EMPTY ImmutableMap will never contain any elements
        @SuppressWarnings("unchecked")
        protected ImmutableMap<String, String> commonTags = ImmutableMap.EMPTY;
        protected boolean includeHost = true;
        protected int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        protected int maxPacketSizeBytes = DEFAULT_MAX_PACKET_SIZE;
        protected int maxProcessorWaitUntilFlushMillis = 10_000;
        protected String histogramBucketIdName = DEFAULT_HISTOGRAM_BUCKET_ID_NAME;
        protected String histogramBucketName = DEFAULT_HISTOGRAM_BUCKET_NAME;
        protected int histogramBucketTagPrecision = DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION;

        private Set<MetricTag> metricTagSet;

        /**
         * Constructs a {@link Builder}. Having at least one {@code SocketAddress} is required.
         * @param socketAddresses the array of {@code SocketAddress}es for this {@link M3Reporter}
         */
        public Builder(SocketAddress[] socketAddresses) {
            if (socketAddresses == null || socketAddresses.length == 0) {
                throw new IllegalArgumentException("Must specify at least one SocketAddress");
            }

            this.socketAddresses = socketAddresses;
        }

        /**
         * Constructs a {@link Builder}. Having at least one {@code SocketAddress} is required.
         * @param socketAddress the {@code SocketAddress} for this {@link M3Reporter}
         */
        public Builder(SocketAddress socketAddress) {
            this(new SocketAddress[]{socketAddress});
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
         * Configures the executor of this {@link Builder}.
         * @param executor the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;

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
         * Configures the maximum wait time in milliseconds size in bytes of this {@link Builder}.
         * @param maxProcessorWaitUntilFlushMillis the value to set
         * @return this {@link Builder} with the new value set
         */
        public Builder maxProcessorWaitUntilFlushMillis(int maxProcessorWaitUntilFlushMillis) {
            this.maxProcessorWaitUntilFlushMillis = maxProcessorWaitUntilFlushMillis;

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
         * Builds and returns an {@link M3Reporter} with the configured paramters.
         * @return a new {@link M3Reporter} instance with the configured paramters
         */
        public M3Reporter build() {
            metricTagSet = toMetricTagSet(commonTags);

            // Set and ensure required tags
            if (!commonTags.containsKey(SERVICE_TAG)) {
                if (service == null || service.isEmpty()) {
                    throw new IllegalArgumentException(String.format("Common tag [%s] is required", SERVICE_TAG));
                }

                metricTagSet.add(createMetricTag(SERVICE_TAG, service));
            }
            if (!commonTags.containsKey(ENV_TAG)) {
                if (env == null || env.isEmpty()) {
                    throw new IllegalArgumentException(String.format("Common tag [%s] is required", ENV_TAG));
                }

                metricTagSet.add(createMetricTag(ENV_TAG, env));
            }
            if (includeHost && !commonTags.containsKey(HOST_TAG)) {
                metricTagSet.add(createMetricTag(HOST_TAG, getHostName()));
            }

            return new M3Reporter(this);
        }
    }
}
