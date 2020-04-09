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

import com.uber.m3.tally.*;
import com.uber.m3.tally.m3.thrift.TCalcTransport;
import com.uber.m3.tally.m3.thrift.TMultiUdpClient;
import com.uber.m3.tally.m3.thrift.TUdpClient;
import com.uber.m3.tally.m3.thrift.TUdpTransport;
import com.uber.m3.thrift.gen.*;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static final int MAX_DELAY_BEFORE_FLUSHING_MILLIS = 1_000;

    private static final int MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS = 5_000;

    private static final int DEFAULT_METRIC_SIZE = 100;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = TUdpTransport.UDP_DATA_PAYLOAD_MAX_SIZE;

    private static final int EMIT_METRIC_BATCH_OVERHEAD = 19;
    private static final int MIN_METRIC_BUCKET_ID_TAG_LENGTH = 4;

    private static final int NUM_PROCESSORS = 1;

    private final static ThreadLocal<SerializedPayloadSizeEstimator> PAYLOAD_SIZE_ESTIMATOR =
            ThreadLocal.withInitial(SerializedPayloadSizeEstimator::new);

    private M3.Client client;

    private Duration maxBufferingDelay;

    private final int payloadCapacity;

    private String bucketIdTagName;
    private String bucketTagName;
    private String bucketValFmt;

    private final Set<MetricTag> commonTags;

    // Holds metrics to be flushed
    private final BlockingQueue<SizedMetric> metricQueue;
    // The service used to execute metric flushes
    private final ExecutorService executor;

    private final Clock clock;

    // This is a synchronization barrier to make sure that reporter
    // is being shutdown only after all of its processor had done so
    private CountDownLatch shutdownLatch = new CountDownLatch(NUM_PROCESSORS);

    private TTransport transport;

    private AtomicBoolean isShutdown = new AtomicBoolean(false);

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

            payloadCapacity = calculatePayloadCapacity(builder.maxPacketSizeBytes, builder.metricTagSet);

            maxBufferingDelay = Duration.ofMillis(builder.maxProcessorWaitUntilFlushMillis);

            bucketIdTagName = builder.histogramBucketIdName;
            bucketTagName = builder.histogramBucketName;
            bucketValFmt = String.format("%%.%df", builder.histogramBucketTagPrecision);

            metricQueue = new LinkedBlockingQueue<>(builder.maxQueueSize);

            executor = builder.executor != null ? builder.executor : Executors.newFixedThreadPool(NUM_PROCESSORS);

            clock = Clock.systemUTC();

            commonTags = builder.metricTagSet;

            for (int i = 0; i < NUM_PROCESSORS; ++i) {
                addAndRunProcessor();
            }
        } catch (TTransportException | SocketException e) {
            throw new RuntimeException("Exception creating M3Reporter", e);
        }
    }

    private int calculatePayloadCapacity(int maxPacketSizeBytes, Set<MetricTag> commonTags) {
        MetricBatch metricBatch = new MetricBatch();
        metricBatch.setCommonTags(commonTags);
        metricBatch.setMetrics(new ArrayList<Metric>());

        int size = PAYLOAD_SIZE_ESTIMATOR.get().calculateSize(metricBatch);

        int numOverheadBytes = EMIT_METRIC_BATCH_OVERHEAD + size;

        int payloadCapacity = maxPacketSizeBytes - numOverheadBytes;

        if (payloadCapacity <= 0) {
            throw new IllegalArgumentException("Common tags serialized size exceeds packet size");
        }

        return payloadCapacity;
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOG.warn("Unable to determine hostname. Defaulting to: {}", DEFAULT_TAG_VALUE);
            return DEFAULT_TAG_VALUE;
        }
    }

    private void addAndRunProcessor() {
        executor.execute(new Processor());
    }

    @Override
    public Capabilities capabilities() {
        return CapableOf.REPORTING_TAGGING;
    }

    @Override
    public void flush() {
        if (isShutdown.get()) {
            return;
        }

        try {
            metricQueue.put(SizedMetric.FLUSH);
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while trying to queue flush sentinel");
        }
    }

    @Override
    public void close() {
        if (!isShutdown.compareAndSet(false, true)) {
            // Shutdown already
            return;
        }

        // Put sentinal value in queue so that processors know to disregard anything that comes after it.
        queueSizedMetric(SizedMetric.CLOSE);

        // Important to use `shutdownNow` instead of `shutdown` to interrupt processor
        // thread(s) or else they will block forever
        executor.shutdownNow();

        try {
            // Wait a maximum of `MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS` for all processors
            // to complete
            if (!shutdownLatch.await(MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS, TimeUnit.MILLISECONDS)) {
                LOG.warn(
                        "M3Reporter closing before Processors complete after waiting timeout of {}ms!",
                        MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS
                );
            }
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

        queueSizedMetric(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().calculateSize(metric)));
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        TimerValue timerValue = new TimerValue();
        timerValue.setI64Value(interval.getNanos());

        MetricValue metricValue = new MetricValue();
        metricValue.setTimer(timerValue);

        Metric metric = newMetric(name, tags, metricValue);

        queueSizedMetric(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().calculateSize(metric)));
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

        queueSizedMetric(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().calculateSize(metric)));
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

        private final List<Metric> metricsBuffer =
                new ArrayList<>(payloadCapacity / 10);

        private Instant lastBufferFlushTimestamp = Instant.now(clock);

        private int metricsSize = 0;

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
                    SizedMetric sizedMetric = metricQueue.poll(maxBufferingDelay.toMillis(), TimeUnit.MILLISECONDS);

                    // Drop metrics that came in after close
                    if (sizedMetric == SizedMetric.CLOSE) {
                        metricQueue.clear();
                        break;
                    }

                    if (sizedMetric == null) {
                        // If we didn't get any new metrics after waiting the specified time,
                        // flush what we have so far.
                        process(SizedMetric.FLUSH);
                    } else {
                        process(sizedMetric);
                    }

                }
            } catch (InterruptedException e) {
                // Don't care if we get interrupted - the finally block will clean up
            } finally {
                drainQueue();
                flushBuffered();
                // Count down shutdown latch to notify reporter
                shutdownLatch.countDown();
            }
        }

        private void process(SizedMetric sizedMetric) {
            if (sizedMetric == SizedMetric.FLUSH) {
                flushBuffered();
                return;
            }

            int size = sizedMetric.getSize();
            if (metricsSize + size > payloadCapacity || elapsedMaxDelaySinceLastFlush()) {
                flushBuffered();
            }

            Metric metric = sizedMetric.getMetric();

            metricsBuffer.add(metric);
            metricsSize += size;
        }

        private boolean elapsedMaxDelaySinceLastFlush() {
            return lastBufferFlushTimestamp.plus(maxBufferingDelay.toMillis(), ChronoUnit.MILLIS)
                    .isBefore(Instant.now(clock));
        }

        private void drainQueue() {
            while (!metricQueue.isEmpty()) {
                SizedMetric sizedMetric = metricQueue.remove();
                // Don't care about metrics that came in after close
                if (sizedMetric == SizedMetric.CLOSE) {
                    break;
                }

                process(sizedMetric);
            }
        }

        private void flushBuffered() {
            if (metricsBuffer.isEmpty()) {
                return;
            }

            MetricBatch metricBatch = new MetricBatch();
            metricBatch.setCommonTags(commonTags);
            metricBatch.setMetrics(metricsBuffer);

            try {
                client.emitMetricBatch(metricBatch);
            } catch (TException tException) {
                LOG.warn("Failed to flush metrics: " + tException.getMessage());
            }

            metricBatch.setMetrics(null);

            metricsBuffer.clear();
            metricsSize = 0;
            lastBufferFlushTimestamp = Instant.now(clock);
        }
    }

    /**
     * This class provides the facility to calculate the size of the payload serialized through {@link TCompactProtocol},
     * using phony {@link TCalcTransport} as a measurer
     */
    @NotThreadSafe
    private static class SerializedPayloadSizeEstimator {
        private final TCalcTransport calculatingPhonyTransport = new TCalcTransport();
        private final TProtocol calculatingPhonyProtocol =
                new TCompactProtocol.Factory().getProtocol(calculatingPhonyTransport);

        public int calculateSize(TBase<?, ?> metric) {
            try {
                metric.write(calculatingPhonyProtocol);
                return calculatingPhonyTransport.getSizeAndReset();
            } catch (TException e) {
                LOG.warn("Unable to calculate metric batch size. Defaulting to: " + DEFAULT_METRIC_SIZE);
                return DEFAULT_METRIC_SIZE;
            }
        }
    }

    /**
     * Builder pattern to construct an {@link M3Reporter}.
     */
    public static class Builder {
        protected SocketAddress[] socketAddresses;
        protected String service;
        protected String env;
        protected ExecutorService executor;
        // Non-generic EMPTY ImmutableMap will never contain any elements
        @SuppressWarnings("unchecked")
        protected ImmutableMap<String, String> commonTags = ImmutableMap.EMPTY;
        protected boolean includeHost = false;
        protected int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
        protected int maxPacketSizeBytes = DEFAULT_MAX_PACKET_SIZE;
        protected int maxProcessorWaitUntilFlushMillis = MAX_DELAY_BEFORE_FLUSHING_MILLIS;
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
