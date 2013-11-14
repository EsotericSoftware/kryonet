
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.kryo.Kryo;

public class MultipleServerTest extends KryoNetTestCase {
	AtomicInteger received = new AtomicInteger();
	
	public void testMultipleThreads () throws IOException {
		final Server server1 = new Server(16384, 8192);
		server1.getKryo().register(String[].class);
		startEndPoint(server1);
		server1.bind(tcpPort, udpPort);
		server1.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof String) {
					if (!object.equals("client1")) fail();
					if (received.incrementAndGet() == 2) stopEndPoints();
				}
			}
		});

		final Server server2 = new Server(16384, 8192);
		server2.getKryo().register(String[].class);
		startEndPoint(server2);
		server2.bind(tcpPort + 1, udpPort + 1);
		server2.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof String) {
					if (!object.equals("client2")) fail();
					if (received.incrementAndGet() == 2) stopEndPoints();
				}
			}
		});

		// ----

		Client client1 = new Client(16384, 8192);
		client1.getKryo().register(String[].class);
		startEndPoint(client1);
		client1.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP("client1");
			}
		});
		client1.connect(5000, host, tcpPort, udpPort);

		Client client2 = new Client(16384, 8192);
		client2.getKryo().register(String[].class);
		startEndPoint(client2);
		client2.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP("client2");
			}
		});
		client2.connect(5000, host, tcpPort + 1, udpPort + 1);

		waitForThreads(5000);
	}
}
