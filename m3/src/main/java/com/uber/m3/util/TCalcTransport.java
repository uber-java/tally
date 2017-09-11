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

package com.uber.m3.util;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dummy transport used for calculating size of metrics only.
 */
public class TCalcTransport extends TTransport {
    private AtomicInteger size = new AtomicInteger(0);

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void open() throws TTransportException {
    }

    @Override
    public void close() {
    }

    @Override
    public int read(byte[] bytes, int i, int i1) throws TTransportException {
        return 0;
    }

    @Override
    public void write(byte[] bytes, int i, int len) throws TTransportException {
        size.getAndAdd(len);
    }

    /**
     * Returns the size that has been written to this transport since the last reset
     * and resets the size.
     * @return the size that has been written to this transport since the last reset
     */
    public int getSizeAndReset() {
        return size.getAndSet(0);
    }

    /**
     * Returns the size that has been written to this transport since the last reset.
     * @return the size that has been written to this transport since the last reset
     */
    public int getSize() {
        return size.get();
    }

    /**
     * Resets the size that has been written to this transport since the last reset.
     */
    public void resetSize() {
        size.set(0);
    }
}
