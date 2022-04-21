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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsdAssertingUdpServer implements Runnable {
    private final int TIMEOUT_MILLIS = 1000;
    private final int RECEIVE_MAX_SIZE = 1024;
    private final SocketAddress socketAddress;
    private final List<ReportedMetric> errored;
    private Set<ReportedMetric> expected;

    StatsdAssertingUdpServer(String hostname, int port, Set<ReportedMetric> expected) {
        this.expected = expected;
        this.errored = new ArrayList<>();

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

            for (int i = 0; i < expected.size(); i++) {
                byte[] receiveData = new byte[RECEIVE_MAX_SIZE];

                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                String receivedStr = new String(receivePacket.getData());

                // Buffer might contain NUL chars at the end so we trim that
                // And then we split the lines as sometimes we get two messages at once
                String[] strs = receivedStr.trim().split("\n");

                for (String str : strs) {
                    final ReportedMetric metric = ReportedMetric.valueOf(str);
                    if (!expected.contains(metric)) {
                        errored.add(metric);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while running server for assertions", e);
        }
    }

    public List<ReportedMetric> getErrored() {
        return errored;
    }

    static class ReportedMetric {

        private static Pattern lineRegex = Pattern.compile(
            "(?<scope>[a-z\\-.0-9]+):(?<value>[a-z0-9.]+)(\\|(?<type>\\w+))(\\|@\\d\\.\\d+){0,1}(\\|#(?<tags>\\S+)){0,1}");
        private String scope;
        private String value;
        private String type;
        private Set<String> tags;

        ReportedMetric(String scope, String value, String type, Set<String> tags) {
            this.scope = scope;
            this.value = value;
            this.type = type;
            this.tags = tags;
        }

        public static ReportedMetric valueOf(String line) {
            final Matcher matcher = lineRegex.matcher(line);
            if (!matcher.matches()) {
                throw new RuntimeException("Input line cannot be handled");
            }

            Set<String> tags = new HashSet<>();
            final String tagString = matcher.group("tags");
            if (tagString != null) {
                tags.addAll(Arrays.asList(tagString.split(",")));
            }

            return new ReportedMetric(
                matcher.group("scope"),
                matcher.group("value"),
                matcher.group("type"),
                tags);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ReportedMetric that = (ReportedMetric) o;
            return Objects.equals(scope, that.scope)
                && Objects.equals(value, that.value)
                && Objects.equals(type, that.type)
                && Objects.equals(tags, that.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scope, value, type, tags);
        }

        @Override
        public String toString() {
            return "ReportedMetric{" +
                "scope='" + scope + '\'' +
                ", value='" + value + '\'' +
                ", type='" + type + '\'' +
                ", tags=" + tags +
                '}';
        }
    }
}
