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

import org.apache.thrift.transport.TTransportException;

import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * A server for receiving data via Thrift UDP.
 */
public class TUdpServer extends TUdpTransport implements AutoCloseable {
    private int timeoutMillis;

    /**
     * Constructs a UDP server with the given host and port. Defaults to zero timeout.
     * @param host the host for this transport
     * @param port the port for this transport
     * @throws SocketException if the underlying socket cannot be opened
     */
    public TUdpServer(String host, int port) throws SocketException {
        this(host, port, 0);
    }

    /**
     * Constructs a UDP server with the given host and port.
     * @param host          the host for this transport
     * @param port          the port for this transport
     * @param timeoutMillis the timeout in milliseconds
     * @throws SocketException if the underlying socket cannot be opened
     */
    public TUdpServer(String host, int port, int timeoutMillis) throws SocketException {
        super(host, port);

        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void open() throws TTransportException {
        try {
            socket.bind(new InetSocketAddress(host, port));
            socket.setSoTimeout(timeoutMillis);
        } catch (SocketException e) {
            throw new TTransportException("Error opening transport", e);
        }
    }
}
