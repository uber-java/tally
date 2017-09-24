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

import com.uber.m3.tally.m3.thrift.TCalcTransport;
import com.uber.m3.thrift.generated.CountValue;
import com.uber.m3.thrift.generated.GaugeValue;
import com.uber.m3.thrift.generated.Metric;
import com.uber.m3.thrift.generated.MetricBatch;
import com.uber.m3.thrift.generated.MetricTag;
import com.uber.m3.thrift.generated.MetricValue;
import com.uber.m3.thrift.generated.TimerValue;
import com.uber.m3.util.Construct;
import com.uber.m3.util.ObjectPool;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A collection of {@link ObjectPool}s used for the {@link M3Reporter}.
 */
public class ResourcePool {
    public static final int BATCH_POOL_SIZE = 10;
    public static final int PROTOCOL_POOL_SIZE = 10;

    private ObjectPool<MetricBatch> metricBatchPool;
    private ObjectPool<Metric> metricPool;
    private ObjectPool<MetricTag> metricTagPool;
    private ObjectPool<MetricValue> metricValuePool;
    private ObjectPool<CountValue> countValuePool;
    private ObjectPool<GaugeValue> gaugeValuePool;
    private ObjectPool<TimerValue> timerValuePool;
    private ObjectPool<TProtocol> protocolPool;
    private ObjectPool<SizedMetric> sizedMetricPool;

    /**
     * Creates a {@link ResourcePool}, initializing all its inner pools.
     * @param maxQueueSize    the maximum metric queue size
     */
    ResourcePool(int maxQueueSize) {
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

        countValuePool = new ObjectPool<>(maxQueueSize, new Construct<CountValue>() {
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
                return new TCompactProtocol.Factory().getProtocol(new TCalcTransport());
            }
        });

        sizedMetricPool = new ObjectPool<>(maxQueueSize, new Construct<SizedMetric>() {
            @Override
            public SizedMetric instance() {
                return new SizedMetric();
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
        return countValuePool.get();
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
     * Returns a {@link SizedMetric} from the pool.
     * @return a {@link SizedMetric} from the pool
     */
    public SizedMetric getSizedMetric() {
        return sizedMetricPool.get();
    }

    /**
     * Puts the given {@link TProtocol} back to the pool, reseting the
     * {@link TCalcTransport} internal count.
     * @param protocol the {@link TProtocol} to put back to the pool
     * @return whether the {@link TProtocol} was put back into the pool
     */
    public boolean releaseProtocol(TProtocol protocol) {
        ((TCalcTransport) protocol.getTransport()).resetSize();

        return protocolPool.put(protocol);
    }

    /**
     * Puts the given {@link MetricBatch} back to the pool after reseting inner
     * values and releasing each of its internal {@link Metric}s as well.
     * @param metricBatch the {@link MetricBatch} to put back to the pool
     * @return whether the {@link MetricBatch} was put back into the pool
     */
    public boolean releaseMetricBatch(MetricBatch metricBatch) {
        metricBatch.setCommonTagsIsSet(false);

        List<Metric> metrics = metricBatch.getMetrics();

        if (metrics != null) {
            for (Metric metric : metrics) {
                releaseMetric(metric);
            }
        }

        metricBatch.setMetricsIsSet(false);

        return metricBatchPool.put(metricBatch);
    }

    /**
     * Puts the given {@link Metric} back to the pool after reseting its
     * internal attributes.
     * @param metric the {@link Metric} to put back to the pool
     * @return whether the {@link Metric} was put back into the pool
     */
    public boolean releaseMetric(Metric metric) {
        metric.setNameIsSet(false);

        Set<MetricTag> tags = metric.getTags();

        if (tags != null) {
            for (MetricTag tag : tags) {
                tag.setTagNameIsSet(false);
                tag.setTagValueIsSet(false);
                metricTagPool.put(tag);
            }
        }

        metric.setTagsIsSet(false);

        return releaseShallowMetric(metric);
    }

    /**
     * Puts a tag-less {@link Metric} back to the pool as well as
     * its {@link MetricValue}.
     * @param metric the {@link Metric} to put back to the pool
     * @return whether the {@link Metric} was put back into the pool
     */
    public boolean releaseShallowMetric(Metric metric) {
        metric.setTimestampIsSet(false);

        MetricValue metricValue = metric.getMetricValue();

        if (metricValue == null) {
            return metricPool.put(metric);
        }

        releaseMetricValue(metricValue);
        metric.unsetMetricValue();

        return metricPool.put(metric);
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
     * @return whether the {@link MetricValue} was put back into the pool
     */
    public boolean releaseMetricValue(MetricValue metricValue) {
        if (metricValue.isSetCount()) {
            metricValue.getCount().setI64ValueIsSet(false);

            countValuePool.put(metricValue.getCount());

            metricValue.unsetCount();
        } else if (metricValue.isSetGauge()) {
            metricValue.getGauge().setI64ValueIsSet(false);
            metricValue.getGauge().setDValueIsSet(false);

            gaugeValuePool.put(metricValue.getGauge());

            metricValue.unsetGauge();
        } else if (metricValue.isSetTimer()) {
            metricValue.getTimer().setI64ValueIsSet(false);
            metricValue.getTimer().setDValueIsSet(false);

            timerValuePool.put(metricValue.getTimer());

            metricValue.unsetTimer();
        }

        return metricValuePool.put(metricValue);
    }

    /**
     * Puts the given {@link SizedMetric} back to the pool after reseting
     * inner values.
     * @param sizedMetric the {@link SizedMetric} to put back to the pool
     * @return whether the {@link SizedMetric} was put back into the pool
     */
    public boolean releaseSizedMetric(SizedMetric sizedMetric) {
        sizedMetric.setMetric(null);
        sizedMetric.setSize(0);

        return sizedMetricPool.put(sizedMetric);
    }

    /**
     * Puts the given {@link MetricTag} back to the pool after reseting
     * inner values.
     * @param metricTag the {@link MetricTag} to put back to the pool
     * @return whether the {@link MetricTag} was put back into the pool
     */
    public boolean releaseMetricTag(MetricTag metricTag) {
        metricTag.setTagNameIsSet(false);
        metricTag.setTagValueIsSet(false);

        return metricTagPool.put(metricTag);
    }

    /**
     * Puts the given {@link CountValue} back to the pool after reseting
     * inner values.
     * @param countValue the {@link CountValue} to put back to the pool
     * @return whether the {@link CountValue} was put back into the pool
     */
    public boolean releaseCountValue(CountValue countValue) {
        countValue.setI64ValueIsSet(false);

        return countValuePool.put(countValue);
    }

    /**
     * Puts the given {@link GaugeValue} back to the pool after reseting
     * inner values.
     * @param gaugeValue the {@link GaugeValue} to put back to the pool
     * @return whether the {@link GaugeValue} was put back into the pool
     */
    public boolean releaseGaugeValue(GaugeValue gaugeValue) {
        gaugeValue.setDValueIsSet(false);
        gaugeValue.setI64ValueIsSet(false);

        return gaugeValuePool.put(gaugeValue);
    }

    /**
     * Puts the given {@link TimerValue} back to the pool after reseting
     * inner values.
     * @param timerValue the {@link TimerValue} to put back to the pool
     * @return whether the {@link TimerValue} was put back into the pool
     */
    public boolean releaseTimerValue(TimerValue timerValue) {
        timerValue.setDValueIsSet(false);
        timerValue.setI64ValueIsSet(false);

        return timerValuePool.put(timerValue);
    }
}
