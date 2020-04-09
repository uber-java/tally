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

package com.uber.m3.tally.m3.thrift;

import org.apache.commons.codec.Charsets;
import org.apache.thrift.transport.TTransportException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

public class TUdpClientTest {

    @Test
    public void testWritingAfterFlushingSequence() throws TTransportException, IOException {
        DatagramSocket mockedSocket = mock(DatagramSocket.class);

        TUdpClient client = createUdpClient(mockedSocket);

        byte[][] payloads =
                Stream.of("0xDEEDDEED", "0xABBAABBA")
                    .map(p -> p.getBytes(Charsets.US_ASCII))
                    .toArray(byte[][]::new);

        for (byte[] writtenBytes : payloads) {
            client.write(writtenBytes);
            client.flush();

            // Writes could only be asserted one by one, due to underlying write buffer sharing within the
            // {@code TUdpClient}
            assertBytesWritten(mockedSocket, writtenBytes);

            // We have to reset mocks between invocations
            reset(mockedSocket);
        }
    }

    private static void assertBytesWritten(DatagramSocket mockedSocket, byte[] expectedPayload) throws IOException {
        ArgumentCaptor<DatagramPacket> argCaptor = ArgumentCaptor.forClass(DatagramPacket.class);

        verify(mockedSocket, times(1))
                .send(argCaptor.capture());

        DatagramPacket sentPacket = argCaptor.getValue();

        // Validate that outgoing buffer is of `TUdpClient.UDP_DATA_PAYLOAD_MAX_SIZE` size
        Assert.assertEquals(TUdpClient.UDP_DATA_PAYLOAD_MAX_SIZE, sentPacket.getData().length);

        // Validate actual data being written to the socket
        Assert.assertEquals(expectedPayload.length, sentPacket.getLength());
        Assert.assertEquals(0, sentPacket.getOffset());
        Assert.assertEquals(
                new String(expectedPayload, Charsets.US_ASCII),
                new String(sentPacket.getData(), sentPacket.getOffset(), sentPacket.getLength(), Charsets.US_ASCII)
        );
    }

    private static TUdpClient createUdpClient(DatagramSocket mockedSocket) {
        return new TUdpClient(InetSocketAddress.createUnresolved("0.0.0.0", 0), mockedSocket);
    }

}
