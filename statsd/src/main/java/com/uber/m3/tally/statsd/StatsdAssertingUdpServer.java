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

package com.uber.m3.tally.statsd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

public class StatsdAssertingUdpServer implements Runnable {
    private final int TIMEOUT_MILLIS = 1000;
    private final int RECEIVE_MAX_SIZE = 1024;
    private final SocketAddress socketAddress;
    private Set<String> expectedStrs;

    StatsdAssertingUdpServer(String hostname, int port, Set<String> expectedStrs) {
        this.expectedStrs = expectedStrs;

        try {
            this.socketAddress = new InetSocketAddress(InetAddress.getByName(hostname), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unable to open server", e);
        }
    }

    @Override
    public synchronized void run() {
        try (DatagramSocket serverSocket = new DatagramSocket(socketAddress)) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);

            for (int i = 0; i < expectedStrs.size(); i++) {
                byte[] receiveData = new byte[RECEIVE_MAX_SIZE];

                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                String receivedStr = new String(receivePacket.getData());

                // Sometimes we get two messages at once
                String[] strs = receivedStr.split("\n");

                for (String str : strs) {
                    // Clean the received message
                    str = str.substring(0, str.lastIndexOf('|'));

                    if (!expectedStrs.contains(str)) {
                        throw new IllegalStateException(String.format("Unexpected message: %s", str));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while running server for assertions", e);
        }
    }
}
