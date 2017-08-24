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

package com.uber.m3.tally;

import com.uber.m3.util.Duration;

/**
 * Default implementation of a {@link BucketPair}
 */
class BucketPairImpl implements BucketPair {
    private double lowerBoundValue;
    private double upperBoundValue;
    private Duration lowerBoundDuration;
    private Duration upperBoundDuration;

    BucketPairImpl(
        double lowerBoundValue,
        double upperBoundValue,
        Duration lowerBoundDuration,
        Duration upperBoundDuration
    ) {
        this.lowerBoundValue = lowerBoundValue;
        this.upperBoundValue = upperBoundValue;
        this.lowerBoundDuration = lowerBoundDuration;
        this.upperBoundDuration = upperBoundDuration;
    }

    @Override
    public double lowerBoundValue() {
        return lowerBoundValue;
    }

    @Override
    public double upperBoundValue() {
        return upperBoundValue;
    }

    @Override
    public Duration lowerBoundDuration() {
        return lowerBoundDuration;
    }

    @Override
    public Duration upperBoundDuration() {
        return upperBoundDuration;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof BucketPairImpl)) {
            return false;
        }

        BucketPairImpl otherBucketPair = (BucketPairImpl) other;

        return lowerBoundValue == otherBucketPair.lowerBoundValue
            && upperBoundValue == otherBucketPair.upperBoundValue
            && lowerBoundDuration.equals(otherBucketPair.lowerBoundDuration)
            && upperBoundDuration.equals(otherBucketPair.upperBoundDuration);
    }

    @Override
    public int hashCode() {
        return ((new Double(lowerBoundValue).hashCode() * 33) ^ new Double(upperBoundValue).hashCode())
            ^ ((lowerBoundDuration.hashCode() * 33) ^ upperBoundDuration.hashCode());
    }

    @Override
    public String toString() {
        return String.format("[%s, %s]", lowerBoundDuration, upperBoundDuration);
    }
}
