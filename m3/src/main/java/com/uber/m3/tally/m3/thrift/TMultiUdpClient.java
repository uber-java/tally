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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * A Thrift transport that sends to multiple connections
 */
public class TMultiUdpClient extends TTransport implements AutoCloseable {
    public static final int MAX_BUFFER_SIZE = 65536;

    public final Object sendLock = new Object();

    protected TTransport[] transports;

    protected TMultiUdpClient(SocketAddress[] socketAddresses) throws SocketException {
        if (socketAddresses == null || socketAddresses.length == 0) {
            throw new IllegalArgumentException("Must provide at least one SocketAddress");
        }

        transports = new TTransport[socketAddresses.length];

        for (int i = 0; i < socketAddresses.length; i++) {
            transports[0] = new TUdpClient(socketAddresses[i]);
        }
    }

    @Override
    public boolean isOpen() {
        for (TTransport transport : transports) {
            if (!transport.isOpen()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void open() throws TTransportException {
        for (TTransport transport : transports) {
            transport.open();
        }
    }

    @Override
    public void close() {
        for (TTransport transport : transports) {
            transport.close();
        }
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws TTransportException {
        throw new UnsupportedOperationException("Reading from multiple transports is not supported");
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws TTransportException {
        if (!isOpen()) {
            throw new TTransportException(TTransportException.NOT_OPEN);
        }


    }

    @Override
    public void flush() throws TTransportException {
        for (TTransport transport : transports) {
            transport.flush();
        }
    }
}
