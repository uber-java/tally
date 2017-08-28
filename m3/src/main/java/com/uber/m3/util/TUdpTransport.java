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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * Abstract class that supports Thrift UDP functionality
 */
public abstract class TUdpTransport extends TTransport implements AutoCloseable {
    public static final int MAX_PACKET_SIZE = 14400;
    public static final int MAX_BUFFER_SIZE = 65536;

    public final Object receiveLock = new Object();
    public final Object sendLock = new Object();

    protected final DatagramSocket socket = new DatagramSocket(null);
    protected byte[] receiveBuf;
    protected int receiveOffSet = -1;
    protected int receiveLength = 0;
    protected ByteBuffer writeButter;

    protected String host;
    protected int port;

    protected TUdpTransport(String host, int port) throws SocketException {
        this.host = host;
        this.port = port;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public abstract void open() throws TTransportException;

    @Override
    public void close() {
        socket.close();
    }

    @Override
    public int getBytesRemainingInBuffer() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int read(byte[] bytes, int offset, int len) throws TTransportException {
        synchronized (receiveLock) {
            if (!isOpen()) {
                throw new TTransportException(TTransportException.NOT_OPEN);
            }

            if (receiveOffSet == -1) {
                receiveBuf = new byte[MAX_BUFFER_SIZE];
                DatagramPacket dg = new DatagramPacket(receiveBuf, MAX_BUFFER_SIZE);

                try {
                    socket.receive(dg);
                } catch (IOException e) {
                    throw new TTransportException("Error from underlying socket", e);
                }

                receiveOffSet = 0;
                receiveLength = dg.getLength();
            }

            int curDataSize = receiveLength - receiveOffSet;

            if (curDataSize <= len) {
                System.arraycopy(receiveBuf, receiveOffSet, bytes, offset, curDataSize);
                receiveOffSet = -1;
                return curDataSize;
            } else {
                System.arraycopy(receiveBuf, receiveOffSet, bytes, offset, len);
                receiveOffSet += len;
                return len;
            }
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int len) throws TTransportException {
        synchronized (sendLock) {
            if (!isOpen()) {
                throw new TTransportException(TTransportException.NOT_OPEN);
            }
            if (writeButter == null) {
                writeButter = ByteBuffer.allocate(MAX_BUFFER_SIZE);
            }
            if (writeButter.position() + len > MAX_PACKET_SIZE) {
                throw new TTransportException("Message size too large: " + len + " > " + MAX_BUFFER_SIZE);
            }
            writeButter.put(bytes, offset, len);
        }
    }

    @Override
    public void flush() throws TTransportException {
        synchronized (sendLock) {
            if (writeButter != null) {
                byte[] bytes = new byte[MAX_BUFFER_SIZE];
                int len = writeButter.position();

                writeButter.flip();
                writeButter.get(bytes, 0, len);

                try {
                    socket.send(new DatagramPacket(bytes, len));
                } catch (IOException e) {
                    throw new TTransportException("Cannot flush closed transport", e);
                } finally {
                    writeButter = null;
                }
            }
        }
    }
}
