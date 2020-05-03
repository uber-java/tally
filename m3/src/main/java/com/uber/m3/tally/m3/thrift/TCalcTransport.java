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

package com.uber.m3.tally.m3.thrift;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dummy transport used for calculating size of metrics only.
 */
public class TCalcTransport extends TTransport {
    private final AtomicInteger size = new AtomicInteger(0);

    /**
     * Dummy override to satisfy interface. Not used for our purposes.
     * @return not used for our purposes
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * Dummy override to satisfy interface. Not used for our purposes.
     * @throws TTransportException not used for our purposes
     */
    @Override
    public void open() throws TTransportException {
    }

    /**
     * Dummy override to satisfy interface. Not used for our purposes.
     */
    @Override
    public void close() {
    }

    /**
     * Dummy override to satisfy interface. Not used for our purposes.
     * @param bytes  not used for our purposes
     * @param offset not used for our purposes
     * @param length not used for our purposes
     * @return not used for our purposes
     * @throws TTransportException not used for our purposes
     */
    @Override
    public int read(byte[] bytes, int offset, int length) throws TTransportException {
        return 0;
    }

    /**
     * Record the number of bytes being written to this transport.
     * @param bytes  not used for our purposes
     * @param offset not used for our purposes
     * @param length the length of bytes being written
     * @throws TTransportException this will never throw
     */
    @Override
    public void write(byte[] bytes, int offset, int length) throws TTransportException {
        size.getAndAdd(length);
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
