
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
