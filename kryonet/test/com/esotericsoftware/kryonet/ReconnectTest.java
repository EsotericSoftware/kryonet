
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ReconnectTest extends KryoNetTestCase {
	public void testReconnect () throws IOException {
		final Timer timer = new Timer();

		final Server server = new Server();
		startEndPoint(server);
		server.bind(tcpPort);
		server.addListener(new Listener() {
			public void connected (final Connection connection) {
				timer.schedule(new TimerTask() {
					public void run () {
						System.out.println("Disconnecting after 2 seconds.");
						connection.close();
					}
				}, 2000);
			}
		});

		// ----

		final AtomicInteger reconnetCount = new AtomicInteger();
		final Client client = new Client();
		startEndPoint(client);
		client.addListener(new Listener() {
			public void disconnected (Connection connection) {
				if (reconnetCount.getAndIncrement() == 2) {
					stopEndPoints();
					return;
				}
				new Thread() {
					public void run () {
						try {
							System.out.println("Reconnecting: " + reconnetCount.get());
							client.reconnect();
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
				}.start();
			}
		});
		client.connect(5000, host, tcpPort);

		waitForThreads(10000);
		assertEquals(3, reconnetCount.getAndIncrement());
	}
}
