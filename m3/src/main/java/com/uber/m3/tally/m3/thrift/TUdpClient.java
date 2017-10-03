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

import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * A client for sending data via Thrift UDP.
 */
public class TUdpClient extends TUdpTransport implements AutoCloseable {
    /**
     * Constructs a UDP client with the given host and port.
     * @param socketAddress the {@code SocketAddress} for this transport
     * @throws SocketException if the underlying socket cannot be opened
     */
    public TUdpClient(SocketAddress socketAddress) throws SocketException {
        super(socketAddress);
    }

    @Override
    public void open() throws TTransportException {
        try {
            socket.connect(socketAddress);
        } catch (SocketException e) {
            throw new TTransportException("Error opening transport", e);
        }
    }

    @Override
    public void flush() throws TTransportException {
        synchronized (sendLock) {
            byte[] bytes = new byte[MAX_BUFFER_SIZE];
            int length = writeBuffer.position();

            writeBuffer.flip();
            writeBuffer.get(bytes, 0, length);

            try {
                socket.send(new DatagramPacket(bytes, length));
            } catch (IOException e) {
                throw new TTransportException(e);
            } finally {
                writeBuffer.clear();
            }
        }
    }
}
