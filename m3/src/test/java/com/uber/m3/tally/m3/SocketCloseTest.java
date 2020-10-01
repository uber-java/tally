// Copyright (c) 2020 Uber Technologies, Inc.
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

import com.uber.m3.tally.Timer;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.StatsReporter;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class SocketCloseTest {
    public static final int MAX_PACKET_SIZE = 1024;
    public static final int MAX_QUEUE_SIZE = 5;
    public static final int PORT = 9998;
    public static final SocketAddress SOCKET_ADDRESS = new InetSocketAddress("localhost", PORT);
    private DatagramSocket socket;

    @Before
    public void setup() {
        try {
            socket = new DatagramSocket(PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        new UDPServer(socket).start();
    }

    @Test
    public void main() {
        StatsReporter reporter = new M3Reporter.Builder(SOCKET_ADDRESS)
            .maxPacketSizeBytes(MAX_PACKET_SIZE)
            .maxQueueSize(MAX_QUEUE_SIZE)
            .service("test-service")
            .env("test")
            .build();

        Scope scope = new RootScopeBuilder()
            .reporter(reporter)
            .reportEvery(Duration.ofMillis(1000));

        Runnable emitter = new MetricsEmitter(scope);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new EmitterThreadFact());
        scheduler.scheduleAtFixedRate(emitter, 0, 200, TimeUnit.MILLISECONDS);

        try {
            Thread.sleep(10_000);

            System.out.println("closing socket");
            socket.close();
            System.out.println("socket closed!");

            Thread.sleep(30_000);

            System.out.println(">>>>>>>TEST COMPLETED<<<<<<<");

            System.out.println(threadDump(true, true));
        } catch (InterruptedException e) {
            System.out.println("Interrupted");
        }
    }

    class EmitterThreadFact implements ThreadFactory {
        public Thread newThread(Runnable r) {
            return new Thread(r, "EMITTER THREAD");
        }
    }

    private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
        StringBuffer threadDump = new StringBuffer(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for(ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString());
        }
        return threadDump.toString();
    }

    public class MetricsEmitter extends Thread {
        private Scope scope;

        MetricsEmitter(Scope scope) {
            this.scope = scope;
        }

        public void run() {
            String timerName = String.format("timer-%d", System.currentTimeMillis());
            Timer g = scope.timer(timerName);
            g.record(Duration.ofMillis(1234));
            System.out.println(String.format("Recorded %s", timerName));
        }
    }

    public class UDPServer extends Thread {
        private DatagramSocket socket;
        private byte[] buf = new byte[MAX_PACKET_SIZE];

        UDPServer(DatagramSocket socket) {
            this.socket = socket;
        }

        public void run() {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);

//                    InetAddress address = packet.getAddress();
//                    int port = packet.getPort();
//                    packet = new DatagramPacket(buf, buf.length, address, port);
//                    String received = new String(packet.getData(), 0, packet.getLength());
//                    System.out.println(String.format("Received: %s", received));
                } catch (IOException e) {
                    break;
                }
            }
        }
    }
}
