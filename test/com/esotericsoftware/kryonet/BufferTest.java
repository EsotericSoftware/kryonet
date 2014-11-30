/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;

public class BufferTest extends KryoNetTestCase {
	AtomicInteger received = new AtomicInteger();
	AtomicInteger receivedBytes = new AtomicInteger();

	public void testManyLargeMessages () throws IOException {
		final int messageCount = 1024;
		int objectBufferSize = 10250;
		int writeBufferSize = 10250 * messageCount;

		Server server = new Server(writeBufferSize, objectBufferSize);
		startEndPoint(server);
		register(server.getKryo());
		server.bind(tcpPort);

		server.addListener(new Listener() {
			AtomicInteger received = new AtomicInteger();
			AtomicInteger receivedBytes = new AtomicInteger();

			public void received (Connection connection, Object object) {
				if (object instanceof LargeMessage) {
					System.out.println("Server sending message: " + received.get());
					connection.sendTCP(object);

					receivedBytes.addAndGet(((LargeMessage)object).bytes.length);

					int count = received.incrementAndGet();
					System.out.println("Server received " + count + " messages.");
					if (count == messageCount) {
						System.out.println("Server received all " + messageCount + " messages!");
						System.out.println("Server received and sent " + receivedBytes.get() + " bytes.");
					}
				}
			}
		});

		final Client client = new Client(writeBufferSize, objectBufferSize);
		startEndPoint(client);
		register(client.getKryo());
		client.connect(5000, host, tcpPort);

		client.addListener(new Listener() {
			AtomicInteger received = new AtomicInteger();
			AtomicInteger receivedBytes = new AtomicInteger();

			public void received (Connection connection, Object object) {
				if (object instanceof LargeMessage) {
					int count = received.incrementAndGet();
					System.out.println("Client received " + count + " messages.");
					if (count == messageCount) {
						System.out.println("Client received all " + messageCount + " messages!");
						System.out.println("Client received and sent " + receivedBytes.get() + " bytes.");
						stopEndPoints();
					}
				}
			}
		});

		byte[] b = new byte[1024 * 10];
		for (int i = 0; i < messageCount; i++) {
			System.out.println("Client sending: " + i);
			client.sendTCP(new LargeMessage(b));
		}
		System.out.println("Client has queued " + messageCount + " messages.");

		waitForThreads(5000);
	}

	private void register (Kryo kryo) {
		kryo.register(byte[].class);
		kryo.register(LargeMessage.class);
	}

	public static class LargeMessage {
		public byte[] bytes;

		public LargeMessage () {
		}

		public LargeMessage (byte[] bytes) {
			this.bytes = bytes;
		}
	}
}
