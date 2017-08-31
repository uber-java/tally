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

import com.uber.m3.thrift.generated.CountValue;
import com.uber.m3.thrift.generated.GaugeValue;
import com.uber.m3.thrift.generated.Metric;
import com.uber.m3.thrift.generated.MetricBatch;
import com.uber.m3.thrift.generated.MetricTag;
import com.uber.m3.thrift.generated.MetricValue;
import com.uber.m3.thrift.generated.TimerValue;
import com.uber.m3.util.Construct;
import com.uber.m3.util.ObjectPool;
import com.uber.m3.util.TCalcTransport;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import java.util.HashSet;
import java.util.List;

/**
 * A collection of {@link ObjectPool}s used for the {@link M3Reporter}.
 */
public class ResourcePool {
    private static final int BATCH_POOL_SIZE = 10;
    private static final int PROTOCOL_POOL_SIZE = 10;

    private ObjectPool<MetricBatch> metricBatchPool;
    private ObjectPool<Metric> metricPool;
    private ObjectPool<MetricTag> metricTagPool;
    private ObjectPool<MetricValue> metricValuePool;
    private ObjectPool<CountValue> counterValuePool;
    private ObjectPool<GaugeValue> gaugeValuePool;
    private ObjectPool<TimerValue> timerValuePool;
    private ObjectPool<TProtocol> protocolPool;

    /**
     * Creates a {@link ResourcePool}, initializing all its inner pools.
     * @param maxQueueSize    the maximum metric queue size
     * @param protocolFactory a factory to create the {@link TProtocol} we want
     */
    ResourcePool(int maxQueueSize, final TProtocolFactory protocolFactory) {
        metricBatchPool = new ObjectPool<>(BATCH_POOL_SIZE, new Construct<MetricBatch>() {
            @Override
            public MetricBatch instance() {
                return new MetricBatch();
            }
        });

        metricPool = new ObjectPool<>(maxQueueSize, new Construct<Metric>() {
            @Override
            public Metric instance() {
                return new Metric();
            }
        });

        metricTagPool = new ObjectPool<>(maxQueueSize, new Construct<MetricTag>() {
            @Override
            public MetricTag instance() {
                return new MetricTag();
            }
        });

        metricValuePool = new ObjectPool<>(maxQueueSize, new Construct<MetricValue>() {
            @Override
            public MetricValue instance() {
                return new MetricValue();
            }
        });

        counterValuePool = new ObjectPool<>(maxQueueSize, new Construct<CountValue>() {
            @Override
            public CountValue instance() {
                return new CountValue();
            }
        });

        gaugeValuePool = new ObjectPool<>(maxQueueSize, new Construct<GaugeValue>() {
            @Override
            public GaugeValue instance() {
                return new GaugeValue();
            }
        });

        timerValuePool = new ObjectPool<>(maxQueueSize, new Construct<TimerValue>() {
            @Override
            public TimerValue instance() {
                return new TimerValue();
            }
        });

        protocolPool = new ObjectPool<>(PROTOCOL_POOL_SIZE, new Construct<TProtocol>() {
            @Override
            public TProtocol instance() {
                return protocolFactory.getProtocol(new TCalcTransport());
            }
        });
    }

    /**
     * Returns a {@link MetricBatch} from the pool.
     * @return a {@link MetricBatch} from the pool
     */
    public MetricBatch getMetricBatch() {
        return metricBatchPool.get();
    }

    /**
     * Returns a {@link Metric} from the pool.
     * @return a {@link Metric} from the pool
     */
    public Metric getMetric() {
        return metricPool.get();
    }

    /**
     * Returns an empty {@code Set} of {@link MetricTag}s.
     * @return an empty {@code Set} of {@link MetricTag}s
     */
    public HashSet<MetricTag> getTagList() {
        return new HashSet<>();
    }

    /**
     * Returns a {@link MetricTag} from the pool.
     * @return a {@link MetricTag} from the pool
     */
    public MetricTag getMetricTag() {
        return metricTagPool.get();
    }

    /**
     * Returns a {@link MetricValue} from the pool.
     * @return a {@link MetricValue} from the pool
     */
    public MetricValue getMetricValue() {
        return metricValuePool.get();
    }

    /**
     * Returns a {@link CountValue} from the pool.
     * @return a {@link CountValue} from the pool
     */
    public CountValue getCountValue() {
        return counterValuePool.get();
    }

    /**
     * Returns a {@link GaugeValue} from the pool.
     * @return a {@link GaugeValue} from the pool
     */
    public GaugeValue getGaugeValue() {
        return gaugeValuePool.get();
    }

    /**
     * Returns a {@link TimerValue} from the pool.
     * @return a {@link TimerValue} from the pool
     */
    public TimerValue getTimerValue() {
        return timerValuePool.get();
    }

    /**
     * Returns a {@link TProtocol} from the pool.
     * @return a {@link TProtocol} from the pool
     */
    public TProtocol getProtocol() {
        return protocolPool.get();
    }

    /**
     * Puts the given {@link TProtocol} back to the pool, reseting the
     * {@link TCalcTransport} internal count.
     * @param protocol the {@link TProtocol} to put back to the pool
     */
    public void releaseProtocol(TProtocol protocol) {
        ((TCalcTransport) protocol.getTransport()).resetCount();

        protocolPool.put(protocol);
    }

    /**
     * Puts the given {@link MetricBatch} back to the pool after reseting inner
     * values and releasing each of its internal {@link Metric}s as well.
     * @param metricBatch the {@link MetricBatch} to put back to the pool
     */
    public void releaseMetricBatch(MetricBatch metricBatch) {
        metricBatch.setCommonTags(null);

        for (Metric metric : metricBatch.getMetrics()) {
            releaseMetric(metric);
        }

        metricBatch.setMetrics(null);

        metricBatchPool.put(metricBatch);
    }

    /**
     * Puts the given {@link Metric} back to the pool after reseting its
     * internal attributes.
     * @param metric the {@link Metric} to put back to the pool
     */
    public void releaseMetric(Metric metric) {
        metric.setName("");

        for (MetricTag tag : metric.getTags()) {
            tag.setTagName("");
            tag.setTagValue(null);
            metricTagPool.put(tag);
        }

        metric.setTags(null);

        releaseShallowMetric(metric);
    }

    /**
     * Puts a tag-less {@link Metric} back to the pool as well as
     * its {@link MetricValue}.
     * @param metric the {@link Metric} to put back to the pool
     */
    public void releaseShallowMetric(Metric metric) {
        metric.setTimestamp(0);

        MetricValue metricValue = metric.getMetricValue();

        if (metricValue == null) {
            metricPool.put(metric);
            return;
        }

        releaseMetricValue(metricValue);
        metric.setMetricValue(null);

        metricPool.put(metric);
    }

    /**
     * Puts a list of metrics back to the pool.
     * @param metrics the list of metrics to put back to the pool
     */
    public void releaseShallowMetrics(List<Metric> metrics) {
        for (Metric metric : metrics) {
            releaseShallowMetric(metric);
        }
    }

    /**
     * Puts the given {@link MetricValue} back to the pool after reseting its
     * internal attributes and values.
     * @param metricValue the {@link MetricValue} to put back to the pool
     */
    public void releaseMetricValue(MetricValue metricValue) {
        if (metricValue.isSetCount()) {
            metricValue.getCount().setI64Value(0);

            counterValuePool.put(metricValue.getCount());

            metricValue.unsetCount();
        } else if (metricValue.isSetGauge()) {
            metricValue.getGauge().setI64Value(0);
            metricValue.getGauge().setDValue(0);

            gaugeValuePool.put(metricValue.getGauge());

            metricValue.unsetGauge();
        } else if (metricValue.isSetTimer()) {
            metricValue.getTimer().setI64Value(0);
            metricValue.getTimer().setDValue(0);

            timerValuePool.put(metricValue.getTimer());

            metricValue.unsetTimer();
        }

        metricValuePool.put(metricValue);
    }
}
