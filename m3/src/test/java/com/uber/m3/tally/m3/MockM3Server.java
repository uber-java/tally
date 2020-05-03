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

import com.uber.m3.tally.m3.thrift.TUdpServer;
import com.uber.m3.thrift.gen.M3;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.net.SocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockM3Server {
    private static final Duration MAX_WAIT_TIMEOUT = Duration.ofSeconds(30);

    private final CountDownLatch expectedMetricsLatch;

    private final TProcessor processor;
    private final TTransport transport;
    private final MockM3Service service;

    public MockM3Server(
        int expectedMetricsCount,
        SocketAddress address
    ) {
        this.expectedMetricsLatch = new CountDownLatch(expectedMetricsCount);
        this.service = new MockM3Service(expectedMetricsLatch);
        this.processor = new M3.Processor<>(service);

        try {
            transport = new TUdpServer(address);
        } catch (SocketException e) {
            throw new RuntimeException("Unable to open socket", e);
        }
    }

    public void serve() {
        try {
            transport.open();
        } catch (TTransportException e) {
            throw new RuntimeException("Failed to open socket", e);
        }

        TProtocol protocol = new TCompactProtocol.Factory().getProtocol(transport);

        while (transport.isOpen()) {
            try {
                processor.process(protocol, protocol);
            } catch (TException e) {
                if (transport.isOpen()) {
                    throw new RuntimeException("Error processing metrics", e);
                }
                // Don't care if transport is closed - just ignore that we're
                // trying to read from a closed socket (after someone called close()).
            }
        }
    }

    public void awaitAndClose() throws InterruptedException {
        expectedMetricsLatch.await(MAX_WAIT_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        transport.close();
    }

    public MockM3Service getService() {
        return service;
    }
}
