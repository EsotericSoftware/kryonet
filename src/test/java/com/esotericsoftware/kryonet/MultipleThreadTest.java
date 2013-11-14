
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.Arrays;

import com.esotericsoftware.kryo.Kryo;

public class MultipleThreadTest extends KryoNetTestCase {
	int receivedServer, receivedClient1, receivedClient2;

	public void testMultipleThreads () throws IOException {
		receivedServer = 0;

		final int messageCount = 10;
		final int threads = 5;
		final int sleepMillis = 50;
		final int clients = 3;

		final Server server = new Server(16384, 8192);
		server.getKryo().register(String[].class);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				receivedServer++;
				if (receivedServer == messageCount * clients) stopEndPoints();
			}
		});

		// ----

		for (int i = 0; i < clients; i++) {
			Client client = new Client(16384, 8192);
			client.getKryo().register(String[].class);
			startEndPoint(client);
			client.addListener(new Listener() {
				int received;

				public void received (Connection connection, Object object) {
					if (object instanceof String) {
						received++;
						if (received == messageCount * threads) {
							for (int i = 0; i < messageCount; i++) {
								connection.sendTCP("message" + i);
								try {
									Thread.sleep(50);
								} catch (InterruptedException ignored) {
								}
							}
						}
					}
				}
			});
			client.connect(5000, host, tcpPort, udpPort);
		}

		for (int i = 0; i < threads; i++) {
			new Thread() {
				public void run () {
					Connection[] connections = server.getConnections();
					for (int i = 0; i < messageCount; i++) {
						for (int ii = 0, n = connections.length; ii < n; ii++)
							connections[ii].sendTCP("message" + i);
						try {
							Thread.sleep(sleepMillis);
						} catch (InterruptedException ignored) {
						}
					}
				}
			}.start();
		}

		waitForThreads(5000);

		assertEquals(messageCount * clients, receivedServer);
	}
}
