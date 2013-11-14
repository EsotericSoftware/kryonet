
package com.esotericsoftware.kryonet;

import java.io.IOException;

import com.esotericsoftware.kryonet.FrameworkMessage.Ping;

public class PingTest extends KryoNetTestCase {
	public void testPing () throws IOException {
		final Server server = new Server();
		startEndPoint(server);
		server.bind(tcpPort);

		// ----

		final Client client = new Client();
		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (Connection connection) {
				client.updateReturnTripTime();
			}

			public void received (Connection connection, Object object) {
				if (object instanceof Ping) {
					Ping ping = (Ping)object;
					if (ping.isReply) System.out.println("Ping: " + connection.getReturnTripTime());
					client.updateReturnTripTime();
				}
			}
		});
		client.connect(5000, host, tcpPort);

		waitForThreads(5000);
	}
}
