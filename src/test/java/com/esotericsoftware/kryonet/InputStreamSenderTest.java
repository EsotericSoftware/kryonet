
package com.esotericsoftware.kryonet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryonet.util.InputStreamSender;

public class InputStreamSenderTest extends KryoNetTestCase {
	boolean success;

	public void testStream () throws IOException {
		final int largeDataSize = 12345;

		final Server server = new Server(16384, 8192);
		server.getKryo().setRegistrationRequired(false);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				ByteArrayOutputStream output = new ByteArrayOutputStream(largeDataSize);
				for (int i = 0; i < largeDataSize; i++)
					output.write(i);
				ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
				// Send data in 512 byte chunks.
				connection.addListener(new InputStreamSender(input, 512) {
					protected void start () {
						// Normally would send an object so the receiving side knows how to handle the chunks we are about to send.
						System.out.println("starting");
					}

					protected Object next (byte[] bytes) {
						System.out.println("sending " + bytes.length);
						return bytes; // Normally would wrap the byte[] with an object so the receiving side knows how to handle it.
					}
				});
			}
		});

		// ----

		final Client client = new Client(16384, 8192);
		client.getKryo().setRegistrationRequired(false);
		startEndPoint(client);
		client.addListener(new Listener() {
			int total;

			public void received (Connection connection, Object object) {
				if (object instanceof byte[]) {
					int length = ((byte[])object).length;
					System.out.println("received " + length);
					total += length;
					if (total == largeDataSize) {
						success = true;
						stopEndPoints();
					}
				}
			}
		});

		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads(5000);
		if (!success) fail();
	}
}
