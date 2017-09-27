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

    private static final int DEFAULT_FLUSH_TRY_COUNT = 2;
    private static final int FLUSH_RETRY_DELAY_MILLIS = 200;

    private static final int DEFAULT_METRIC_SIZE = 100;

    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = 1440;

    private static final int THREAD_POOL_SIZE = 10;

    private static final int EMIT_METRIC_BATCH_OVERHEAD = 19;
    private static final int MIN_METRIC_BUCKET_ID_TAG_LENGTH = 4;

    private M3.Client client;

    private final MetricBatch metricBatch = new MetricBatch();

    private final Object calcLock = new Object();
    private TCalcTransport calc;
    private TProtocol calcProtocol;

    private int freeBytes;

    private String bucketIdTagName;
    private String bucketTagName;
    private String bucketValFmt;

    // Holds metrics to be flushed
    private final BlockingQueue<SizedMetric> metricQueue;
    // The service used to execute metric flushes
    private ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    // Used to keep track of how many processors are in progress in
    // order to wait for them to finish before closing
    private Phaser phaser = new Phaser(1);

    private final Object isOpenLock = new Object();
    private boolean isOpen = true;

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

            bucketIdTagName = builder.histogramBucketIdName;
            bucketTagName = builder.histogramBucketName;
            bucketValFmt = String.format("%%.%df", builder.histogramBucketTagPrecision);

            metricQueue = new LinkedBlockingQueue<>(builder.maxQueueSize);

            addAndRunProcessor();
        } catch (TTransportException | SocketException e) {
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
            LOG.warn("Unable to calculate metric batch size. Defaulting to: %s", DEFAULT_METRIC_SIZE);
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
            LOG.warn("Unable to determine hostname. Defaulting to: %s", DEFAULT_TAG_VALUE);
            return DEFAULT_TAG_VALUE;
        }
    }

    private void addAndRunProcessor() {
        phaser.register();

        executor.execute(new Processor());
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
            Thread.currentThread().interrupt();

            throw new RuntimeException(e);
        }
    }

    private void flush(List<Metric> metrics) {
        flush(metrics, DEFAULT_FLUSH_TRY_COUNT);
    }

    private void flush(List<Metric> metrics, int maxTries) {
        if (metrics.isEmpty()) {
            return;
        }

        TException exception = null;

        int tries = 0;
        boolean tryFlush = true;

        synchronized (metricBatch) {
            metricBatch.setMetrics(metrics);

            while (tryFlush) {
                try {
                    client.emitMetricBatch(metricBatch);

                    tryFlush = false;
                } catch (TException tException) {
                    tries++;

                    if (tries < maxTries) {
                        try {
                            Thread.sleep(FLUSH_RETRY_DELAY_MILLIS);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();

                            // If someone interrupts this thread, we should probably
                            // not still try to flush
                            tryFlush = false;

                            exception = new TException("Interrupted while about to retry emitting metrics", tException);
                        }
                    } else {
                        tryFlush = false;

                        // Store exception to throw later after clean up.
                        // Don't want to put everything in a finally block here,
                        // which will hold the lock unnecessarily long
                        exception = tException;
                    }
                }
            }

            metricBatch.setMetrics(null);
        }

        metrics.clear();

        if (exception != null) {
            throw new RuntimeException(
                String.format("Failed to flush metrics after %s tries", maxTries),
                exception
            );
        }
    }

    @Override
    public void close() {
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.warn("M3Reporter already closed");
                return;
            }

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
                    "M3Reporter closing before Processors complete after waiting timeout of %dms!",
                    MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                LOG.warn("M3Reporter closing before Processors complete due to being interrupted!");
            }

            transport.close();

            isOpen = false;
        }
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
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.error("Reporter already closed; no more metrics can be flushed");
                return;
            }

            reportCounterInternal(name, tags, value);
        }
    }

    @Override
    public void reportGauge(String name, Map<String, String> tags, double value) {
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.error("Reporter already closed; no more metrics can be flushed");
                return;
            }

            GaugeValue gaugeValue = new GaugeValue();
            gaugeValue.setDValue(value);

            MetricValue metricValue = new MetricValue();
            metricValue.setGauge(gaugeValue);

            Metric metric = newMetric(name, tags, metricValue);

            queueSizedMetric(new SizedMetric(metric, calculateSize(metric)));
        }
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.error("Reporter already closed; no more metrics can be flushed");
                return;
            }

            TimerValue timerValue = new TimerValue();
            timerValue.setI64Value(interval.getNanos());

            MetricValue metricValue = new MetricValue();
            metricValue.setTimer(timerValue);

            Metric metric = newMetric(name, tags, metricValue);

            queueSizedMetric(new SizedMetric(metric, calculateSize(metric)));
        }
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
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.error("Reporter already closed; no more metrics can be flushed");
                return;
            }

            // Append histogram bucket-specific tags
            int bucketIdLen = String.valueOf(buckets.size()).length();
            bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

            String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

            BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(buckets);

            if (tags == null) {
                tags = new HashMap<>(2, 1);
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
        synchronized (isOpenLock) {
            if (!isOpen) {
                LOG.error("Reporter already closed; no more metrics can be flushed");
                return;
            }

            // Append histogram bucket-specific tags
            int bucketIdLen = String.valueOf(buckets.size()).length();
            bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

            String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

            BucketPair[] bucketPairs = BucketPairImpl.bucketPairs(buckets);

            if (tags == null) {
                tags = new HashMap<>(2, 1);
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
            Thread.currentThread().interrupt();

            LOG.warn(String.format("Interrupted queueing metric: %s", sizedMetric.getMetric().getName()));
        }
    }

    private class Processor implements Runnable {
        List<Metric> metrics = new ArrayList<>(freeBytes / 10);
        int metricsSize = 0;

        @Override
        public void run() {
            try {
                while (!executor.isShutdown()) {
                    // This `take` call will block until there is an item on the queue to take.
                    // When this reporter is closed, shutdownNow will be called on the executor,
                    // which will interrupt this thread and proceed to the `InterruptedException`
                    // catch block.
                    SizedMetric sizedMetric = metricQueue.take();

                    flushMetricIteration(sizedMetric);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                flushRemainingMetrics();

                // Always arrive at phaser to prevent deadlock
                phaser.arrive();
            }
        }

        private void flushMetricIteration(SizedMetric sizedMetric) {
            Metric metric = sizedMetric.getMetric();

            if (sizedMetric == SizedMetric.FLUSH) {
                flush(metrics);

                return;
            }

            int size = sizedMetric.getSize();

            if (metricsSize + size > freeBytes) {
                flush(metrics);
                metricsSize = 0;
            }

            metrics.add(metric);

            metricsSize += size;
        }

        private void flushRemainingMetrics() {
            while (!metricQueue.isEmpty()) {
                flushMetricIteration(metricQueue.remove());
            }

            flush(metrics);
        }
    }

    /**
     * Builder pattern to construct an {@link M3Reporter}.
     */
    public static class Builder {
        private SocketAddress[] socketAddresses;
        private String service;
        private String env;
        // Non-generic EMPTY ImmutableMap will never contain any elements
        @SuppressWarnings("unchecked")
        private ImmutableMap<String, String> commonTags = ImmutableMap.EMPTY;
        private Set<MetricTag> metricTagSet;
        private boolean includeHost = true;
        private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        private int maxPacketSizeBytes = DEFAULT_MAX_PACKET_SIZE;
        private String histogramBucketIdName = DEFAULT_HISTOGRAM_BUCKET_ID_NAME;
        private String histogramBucketName = DEFAULT_HISTOGRAM_BUCKET_NAME;
        private int histogramBucketTagPrecision = DEFAULT_HISTOGRAM_BUCKET_TAG_PRECISION;

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
