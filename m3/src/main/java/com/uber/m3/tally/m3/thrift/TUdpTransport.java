// Copyright (c) 2021 Uber Technologies, Inc.
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

import org.apache.http.annotation.GuardedBy;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    // NOTE: This is the maximum size of a single UDP packet's payload in IPv4
    //       which is set at 65,535, we reserve 512 byte of buffering for various
    //       network configurations therefore setting payload size to be no more than:
    //       65535 - 512 = 65023 bytes
    public static final int PACKET_DATA_PAYLOAD_MAX_SIZE = 65023;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Object sendLock = new Object();

    protected final SocketAddress socketAddress;
    protected final DatagramSocket socket;

    // NOTE: We're using dedicated boolean flag to avoid invoking {@link DatagramSocket#isOpen} directly
    //       on the hot-path which is checking the state of the socket under lock.
    protected volatile boolean open;

    @GuardedBy("sendLock")
    protected ByteBuffer writeBuffer;

    private final Object receiveLock = new Object();

    @GuardedBy("receiveLock")
    private ByteBuffer receiveBuffer;

    // NOTE: This is used in tests
    TUdpTransport(SocketAddress socketAddress, DatagramSocket socket) {
        this.socketAddress = socketAddress;
        this.socket = socket;

        this.writeBuffer = ByteBuffer.allocate(PACKET_DATA_PAYLOAD_MAX_SIZE);
        this.receiveBuffer = ByteBuffer.allocate(PACKET_DATA_PAYLOAD_MAX_SIZE);

        // Resets receiving buffer to 0, to make sure it isn't readable
        // until it would be first written
        this.receiveBuffer.limit(0);
        this.open = false;
    }

    protected TUdpTransport(SocketAddress socketAddress) throws SocketException {
        this(socketAddress, new DatagramSocket(null));
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public abstract void open() throws TTransportException;

    @Override
    public void close() {
        socket.close();
        open = false;

        logger.info("UDP socket has been closed");
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
                DatagramPacket packet = new DatagramPacket(receiveBuffer.array(), PACKET_DATA_PAYLOAD_MAX_SIZE);

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
            if (writeBuffer.position() + length > PACKET_DATA_PAYLOAD_MAX_SIZE) {
                throw new TTransportException(
                    String.format("Message size too large: %d is greater than available size %d",
                        length,
                        PACKET_DATA_PAYLOAD_MAX_SIZE - writeBuffer.position()
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
        synchronized (receiveLock) {
            return receiveBuffer.remaining();
        }
    }

    @Override
    public byte[] getBuffer() {
        synchronized (receiveLock) {
            return receiveBuffer.array();
        }
    }

    @Override
    public int getBufferPosition() {
        synchronized (receiveLock) {
            return receiveBuffer.position();
        }
    }

    @Override
    public void consumeBuffer(int length) {
        synchronized (receiveLock) {
            receiveBuffer.position(receiveBuffer.position() + length);
        }
    }
}
