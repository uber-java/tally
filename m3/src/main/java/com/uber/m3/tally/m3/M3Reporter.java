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
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.CapableOf;
import com.uber.m3.tally.DurationBuckets;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.tally.ValueBuckets;
import com.uber.m3.tally.m3.thrift.TCalcTransport;
import com.uber.m3.tally.m3.thrift.TMultiUdpClient;
import com.uber.m3.tally.m3.thrift.TUdpClient;
import com.uber.m3.tally.m3.thrift.TUdpTransport;
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
import org.apache.http.annotation.NotThreadSafe;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * NOTE: DO NOT CHANGE THIS NUMBER!
     *       Reporter architecture is not suited for multi-processor setup and might cause some disruption
     *       to how metrics are processed and eventually submitted to M3 collectors;
     */
    static final int NUM_PROCESSORS = 1;

    private static final Logger LOG = LoggerFactory.getLogger(M3Reporter.class);

    private static final int MAX_DELAY_BEFORE_FLUSHING_MILLIS = 1_000;

    private static final int MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS = 5_000;

    private static final int DEFAULT_METRIC_SIZE = 100;
    private static final int DEFAULT_MAX_QUEUE_SIZE = 4096;
    private static final int DEFAULT_MAX_PACKET_SIZE = TUdpTransport.PACKET_DATA_PAYLOAD_MAX_SIZE;

    // NOTE: 256 bytes of overhead is reserved for Thrift metadata within UDP datagram payload
    private static final int THRIFT_METADATA_PADDING = 256;

    private static final int MIN_METRIC_BUCKET_ID_TAG_LENGTH = 4;

    private static final ThreadLocal<SerializedPayloadSizeEstimator> PAYLOAD_SIZE_ESTIMATOR =
            ThreadLocal.withInitial(SerializedPayloadSizeEstimator::new);

    private final Duration maxBufferingDelay;

    private final int payloadCapacity;

    private final String bucketIdTagKey;
    private final String bucketValueTagKey;
    private final String bucketValFmt;

    private final Set<MetricTag> commonTags;

    // TODO replace w/ an evicting queue
    private final BlockingQueue<SizedMetric> queue;

    // Executor service running processors flushing metrics to collectors
    private final ExecutorService executorService;

    private final ScheduledExecutorService scheduledExecutorService;

    private final Clock clock;

    // This is a synchronization barrier to make sure that reporter
    // is being shutdown only after all of its processor had done so
    private final CountDownLatch processorsShutdownLatch;

    // List of socket addresses for M3 collector endpoint
    private final SocketAddress[] collectorEndpointSockedAddresses;

    private final Processor[] processors;

    private final TProtocolFactory protocolFactory;

    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    // Use inner Builder class to construct an M3Reporter
    M3Reporter(Builder builder, TProtocolFactory thriftProtocolFactory) {
        payloadCapacity = calculatePayloadCapacity(builder.maxPacketSizeBytes, builder.metricTagSet);

        maxBufferingDelay = Duration.ofMillis(builder.maxProcessorWaitUntilFlushMillis);

        bucketIdTagKey = builder.histogramBucketIdName;
        bucketValueTagKey = builder.histogramBucketName;
        bucketValFmt = String.format("%%.%df", builder.histogramBucketTagPrecision);

        queue = new LinkedBlockingQueue<>(builder.maxQueueSize);

        ThreadFactory namedThreadFactory = createThreadFactory();

        executorService = builder.executor != null ? builder.executor : Executors.newFixedThreadPool(NUM_PROCESSORS, namedThreadFactory);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);

        clock = Clock.systemUTC();

        commonTags = builder.metricTagSet;

        protocolFactory = thriftProtocolFactory;

        processorsShutdownLatch = new CountDownLatch(NUM_PROCESSORS);

        collectorEndpointSockedAddresses = builder.endpointSocketAddresses;
        processors = new Processor[NUM_PROCESSORS];

        for (int i = 0; i < NUM_PROCESSORS; ++i) {
            processors[i] = bootProcessor(collectorEndpointSockedAddresses);
        }

        // Schedule regular heartbeat up-keeping processors up and running
        scheduledExecutorService.scheduleAtFixedRate(this::heartbeat, 1, 1, TimeUnit.SECONDS);
    }

    // NOTE: This method is not concurrent
    void heartbeat() {
        synchronized (this) {
            for (int i = 0; i < processors.length; ++i) {
                if (processors[i].getState() != ProcessorState.RUNNING) {
                    processors[i] = bootProcessor(collectorEndpointSockedAddresses);
                }
            }
        }
    }

    private int calculatePayloadCapacity(int maxPacketSizeBytes, Set<MetricTag> commonTags) {
        MetricBatch metricBatch = new MetricBatch();
        metricBatch.setCommonTags(commonTags);
        metricBatch.setMetrics(new ArrayList<>());

        int thriftRequestShellSize = PAYLOAD_SIZE_ESTIMATOR.get().evaluateThriftRequestWireSize(metricBatch);

        int payloadCapacity = maxPacketSizeBytes - (THRIFT_METADATA_PADDING + thriftRequestShellSize);
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

    private Processor bootProcessor(SocketAddress[] endpointSocketAddresses) {
        try {
            Processor processor = new Processor(endpointSocketAddresses, protocolFactory);
            executorService.execute(processor);
            return processor;
        } catch (TTransportException | SocketException e) {
            LOG.error("Failed to boot processor", e);
            throw new RuntimeException(e);
        }
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

        for (Processor processor : processors) {
            processor.scheduleFlush();
        }
    }

    // NOTE: This should only be used in tests
    void flushNow() throws TException {
        for (Processor processor : processors) {
            processor.flushBuffered();
        }
    }

    @Override
    public void close() {
        if (!isShutdown.compareAndSet(false, true)) {
            // Shutdown already
            return;
        }

        // Important to use `shutdownNow` instead of `shutdown` to interrupt processor
        // thread(s) or else they will block forever
        scheduledExecutorService.shutdownNow();
        executorService.shutdownNow();

        try {
            // Wait a maximum of `MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS` for all processors
            // to complete
            if (!processorsShutdownLatch.await(MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS, TimeUnit.MILLISECONDS)) {
                LOG.warn(
                        "M3Reporter closing before Processors complete after waiting timeout of {}ms!",
                        MAX_PROCESSOR_WAIT_ON_CLOSE_MILLIS
                );
            }
        } catch (InterruptedException e) {
            LOG.warn("M3Reporter closing before Processors complete due to being interrupted!");
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

        enqueue(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().evaluateByteSize(metric)));
    }

    @Override
    public void reportTimer(String name, Map<String, String> tags, Duration interval) {
        TimerValue timerValue = new TimerValue();
        timerValue.setI64Value(interval.getNanos());

        MetricValue metricValue = new MetricValue();
        metricValue.setTimer(timerValue);

        Metric metric = newMetric(name, tags, metricValue);

        enqueue(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().evaluateByteSize(metric)));
    }

    /**
     * @deprecated DO NOT USE
     *
     * Please use {@link #reportHistogramValueSamples(String, Map, Buckets, int, long)} instead
     */
    @Deprecated
    @Override
    public void reportHistogramValueSamples(
            String name,
            Map<String, String> tags,
            Buckets buckets,
            double bucketLowerBound,
            double bucketUpperBound,
            long samples
    ) {
        reportHistogramValueSamples(name, tags, buckets, buckets.getBucketIndexFor(bucketLowerBound), samples);
    }

    /**
     * @deprecated DO NOT USE
     *
     * Please use {@link #reportHistogramValueSamples(String, Map, Buckets, int, long)} instead
     */
    @Override
    @Deprecated
    public void reportHistogramDurationSamples(
            String name,
            Map<String, String> tags,
            Buckets buckets,
            Duration bucketLowerBound,
            Duration bucketUpperBound,
            long samples
    ) {
        reportHistogramValueSamples(name, tags, buckets, buckets.getBucketIndexFor(bucketLowerBound), samples);
    }

    public void reportHistogramValueSamples(
        String name,
        Map<String, String> tags,
        Buckets buckets,
        int bucketIndex,
        long samples
    ) {
        // Append histogram bucket-specific tags
        int bucketIdLen = String.valueOf(buckets.size()).length();
        bucketIdLen = Math.max(bucketIdLen, MIN_METRIC_BUCKET_ID_TAG_LENGTH);

        String bucketIdFmt = String.format("%%0%sd", bucketIdLen);

        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();

        if (tags != null) {
            builder.putAll(tags);
        }

        String bucketValueTag;
        if (buckets instanceof ValueBuckets) {
            bucketValueTag =
                    String.format("%s-%s",
                            valueBucketString(buckets.getValueLowerBoundFor(bucketIndex)),
                            valueBucketString(buckets.getValueUpperBoundFor(bucketIndex))
                    );
        } else if (buckets instanceof DurationBuckets) {
            bucketValueTag =
                    String.format("%s-%s",
                            durationBucketString(buckets.getDurationLowerBoundFor(bucketIndex)),
                            durationBucketString(buckets.getDurationUpperBoundFor(bucketIndex))
                    );
        } else {
            throw new IllegalArgumentException("unsupported buckets format");
        }

        builder
            .put(bucketIdTagKey, String.format(bucketIdFmt, bucketIndex))
            .put(bucketValueTagKey, bucketValueTag);

        reportCounterInternal(name, builder.build(), samples);
    }

    // Relies on the calling function to provide guarantees of the reporter being open
    private void reportCounterInternal(String name, Map<String, String> tags, long value) {
        CountValue countValue = new CountValue();
        countValue.setI64Value(value);

        MetricValue metricValue = new MetricValue();
        metricValue.setCount(countValue);

        Metric metric = newMetric(name, tags, metricValue);

        enqueue(new SizedMetric(metric, PAYLOAD_SIZE_ESTIMATOR.get().evaluateByteSize(metric)));
    }

    private Metric newMetric(String name, Map<String, String> tags, MetricValue metricValue) {
        Metric metric = new Metric(name);
        metric.setTags(toMetricTagSet(tags));
        metric.setTimestamp(System.currentTimeMillis() * Duration.NANOS_PER_MILLI);
        metric.setMetricValue(metricValue);

        return metric;
    }

    private void enqueue(SizedMetric sizedMetric) {
        // Short-circuit if already shutdown
        if (isShutdown.get()) {
            return;
        }

        try {
            boolean enqueued = queue.offer(sizedMetric, 10, TimeUnit.MILLISECONDS);

            if (!enqueued) {
                LOG.warn("Failed to enqueue metric for emission");
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting to enqueuing metric; dropping");
        }
    }

    private static void runNoThrow(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            // no-op
        }
    }

    private static ThreadFactory createThreadFactory() {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("m3-reporter-%d", counter.getAndIncrement()));
            }
        };
    }

    private class Processor implements Runnable {

        private final List<Metric> metricsBuffer =
                new ArrayList<>(payloadCapacity / 10);

        private Instant lastBufferFlushTimestamp = Instant.now(clock);

        private int bufferedBytes = 0;

        private final M3.Client client;
        private final TTransport transport;

        private final AtomicReference<ProcessorState> state = new AtomicReference<>();
        private final AtomicBoolean shouldFlush = new AtomicBoolean(false);

        Processor(SocketAddress[] socketAddresses, TProtocolFactory protocolFactory) throws TTransportException, SocketException {
            if (socketAddresses.length > 1) {
                transport = new TMultiUdpClient(socketAddresses);
            } else {
                transport = new TUdpClient(socketAddresses[0]);
            }

            // Open the socket
            transport.open();

            client = new M3.Client(protocolFactory.getProtocol(transport));

            state.set(ProcessorState.RUNNING);

            LOG.info("Booted reporting processor");
        }

        @Override
        public void run() {
            while (!isShutdown.get()) {
                try {
                    // Check whether flush has been requested by the reporter
                    if (shouldFlush.compareAndSet(true, false)) {
                        flushBuffered();
                    }

                    // This `poll` call will block for at most the specified duration to take an item
                    // off the queue. If we get an item, we append it to the queue to be flushed,
                    // otherwise we flush what we have so far.
                    // When this reporter is closed, shutdownNow will be called on the executor,
                    // which will interrupt this thread and proceed to the `InterruptedException`
                    // catch block.
                    SizedMetric sizedMetric = queue.poll(maxBufferingDelay.toMillis(), TimeUnit.MILLISECONDS);

                    if (sizedMetric == null) {
                        // If we didn't get any new metrics after waiting the specified time,
                        // flush what we have so far.
                        flushBuffered();
                    } else {
                        process(sizedMetric);
                    }
                } catch (InterruptedException t) {
                    // no-op
                } catch (Throwable t) {
                    // This is fly-away guard making sure that uncaught exception
                    // will be logged
                    LOG.error("Unhandled exception in processor", t);
                    break;
                }
            }

            state.set(ProcessorState.SHUTDOWN);

            LOG.warn("Processor shutting down");

            shutdown();

            LOG.warn("Processor shut down");
        }

        private void shutdown() {
            // Drain queue of any remaining metrics submitted prior to shutdown;
            runNoThrow(this::drainQueue);
            // Flush remaining buffers at last (best effort)
            runNoThrow(this::flushBuffered);

            // Close transport
            transport.close();

            // Count down shutdown latch to notify reporter
            processorsShutdownLatch.countDown();
        }

        private void process(SizedMetric sizedMetric) throws TException {
            int size = sizedMetric.getSize();
            if (bufferedBytes + size > payloadCapacity || elapsedMaxDelaySinceLastFlush()) {
                flushBuffered();
            }

            Metric metric = sizedMetric.getMetric();

            metricsBuffer.add(metric);
            bufferedBytes += size;
        }

        private boolean elapsedMaxDelaySinceLastFlush() {
            return Instant.now(clock).isAfter(
                    lastBufferFlushTimestamp.plus(maxBufferingDelay.toMillis(), ChronoUnit.MILLIS)
            );
        }

        private void drainQueue() throws TException {
            SizedMetric metrics;

            while ((metrics = queue.poll()) != null) {
                process(metrics);
            }
        }

        private void flushBuffered() throws TException {
            if (metricsBuffer.isEmpty()) {
                return;
            }

            try {
                client.emitMetricBatch(
                        new MetricBatch()
                                .setCommonTags(commonTags)
                                .setMetrics(metricsBuffer)
                );
            } catch (TException t) {
                LOG.error("Failed to flush metrics", t);
                throw t;
            }

            metricsBuffer.clear();
            bufferedBytes = 0;
            lastBufferFlushTimestamp = Instant.now(clock);
        }

        public void scheduleFlush() {
            shouldFlush.set(true);
        }

        public ProcessorState getState() {
            return state.get();
        }
    }

    enum ProcessorState {
        RUNNING,
        SHUTDOWN
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
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

        private final M3.Client phonyClient = new M3.Client(calculatingPhonyProtocol);

        public int evaluateThriftRequestWireSize(MetricBatch metricBatch) {
            try {
                phonyClient.emitMetricBatch(metricBatch);
                return calculatingPhonyTransport.getSizeAndReset();
            } catch (TException e) {
                LOG.warn("Unable to calculate metric batch size", e);
                throw new RuntimeException(e);
            }
        }

        public int evaluateByteSize(Metric metric) {
            try {
                metric.write(calculatingPhonyProtocol);
                return calculatingPhonyTransport.getSizeAndReset();
            } catch (TException e) {
                LOG.warn("Unable to calculate metric batch size. Defaulting to: " + DEFAULT_METRIC_SIZE, e);
                return DEFAULT_METRIC_SIZE;
            }
        }
    }

    /**
     * Builder pattern to construct an {@link M3Reporter}.
     */
    public static class Builder {
        protected SocketAddress[] endpointSocketAddresses;
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
         * @param endpointSocketAddresses the array of {@code SocketAddress}es for this {@link M3Reporter}
         */
        public Builder(SocketAddress[] endpointSocketAddresses) {
            if (endpointSocketAddresses == null || endpointSocketAddresses.length == 0) {
                throw new IllegalArgumentException("Must specify at least one SocketAddress");
            }

            this.endpointSocketAddresses = endpointSocketAddresses;
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

            return new M3Reporter(this, new TCompactProtocol.Factory());
        }
    }
}
