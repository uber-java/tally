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
 * Abstract class that supports Thrift UDP functionality.
 */
public abstract class TUdpTransport extends TTransport implements AutoCloseable {
    public static final int MAX_BUFFER_SIZE = 65536;

    public final Object receiveLock = new Object();
    public final Object sendLock = new Object();

    protected final DatagramSocket socket = new DatagramSocket(null);
    protected ByteBuffer receiveBuffer;
    protected ByteBuffer writeBuffer;

    protected SocketAddress socketAddress;

    protected TUdpTransport(SocketAddress socketAddress) throws SocketException {
        this.socketAddress = socketAddress;

        writeBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);

        receiveBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE);
        receiveBuffer.flip();
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
    public int read(byte[] bytes, int offset, int length) throws TTransportException {
        if (!isOpen()) {
            throw new TTransportException(TTransportException.NOT_OPEN);
        }

        synchronized (receiveLock) {
            if (!receiveBuffer.hasRemaining()) {
                // Use ByteBuffer's backing array and manually set the position and limit to
                // avoid having to re-copy contents to a new array via `get`
                DatagramPacket packet = new DatagramPacket(receiveBuffer.array(), MAX_BUFFER_SIZE);

                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    throw new TTransportException("Error from underlying socket", e);
                }

                receiveBuffer.position(0);
                receiveBuffer.limit(packet.getLength());
            }

            length = Math.min(length, receiveBuffer.remaining());

            receiveBuffer.get(bytes, offset, length);

            return length;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws TTransportException {
        if (!isOpen()) {
            throw new TTransportException(TTransportException.NOT_OPEN);
        }

        synchronized (sendLock) {
            if (writeBuffer.position() + length > MAX_BUFFER_SIZE) {
                throw new TTransportException(
                    String.format("Message size too large: %d is greater than available size %d",
                        length,
                        MAX_BUFFER_SIZE - writeBuffer.position()
                    )
                );
            }

            writeBuffer.put(bytes, offset, length);
        }
    }

    @Override
    public abstract void flush() throws TTransportException;

    @Override
    public int getBytesRemainingInBuffer() {
        return receiveBuffer.remaining();
    }

    @Override
    public byte[] getBuffer() {
        return receiveBuffer.array();
    }

    @Override
    public int getBufferPosition() {
        return receiveBuffer.position();
    }

    @Override
    public void consumeBuffer(int length) {
        receiveBuffer.position(receiveBuffer.position() + length);
    }
}
