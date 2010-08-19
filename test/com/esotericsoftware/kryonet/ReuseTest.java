
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class ReuseTest extends KryoNetTestCase {
	public void testPingPong () throws IOException {
		final AtomicInteger stringCount = new AtomicInteger(0);

		final Server server = new Server();
		startEndPoint(server);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP("TCP from server");
				connection.sendUDP("UDP from server");
			}

			public void received (Connection connection, Object object) {
				if (object instanceof String) {
					stringCount.incrementAndGet();
					System.out.println(object);
				}
			}
		});

		// ----

		final Client client = new Client();
		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP("TCP from client");
				connection.sendUDP("UDP from client");
			}

			public void received (Connection connection, Object object) {
				if (object instanceof String) {
					stringCount.incrementAndGet();
					System.out.println(object);
				}
			}
		});

		int count = 5;
		for (int i = 0; i < count; i++) {
			server.bind(tcpPort, udpPort);
			client.connect(5000, host, tcpPort, udpPort);
			try {
				Thread.sleep(250);
			} catch (InterruptedException ex) {
			}
			server.close();
		}
		assertEquals(count * 2 * 2, stringCount.get());

		stopEndPoints();
		waitForThreads(10000);
	}
}
