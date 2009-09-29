
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import java.io.IOException;
import java.util.Timer;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.minlog.Log;

public class PingTestClient {
	private Timer timer = new Timer();

	public PingTestClient () throws IOException {
		final Client client = new Client();
		client.start(false);
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
		client.connect(5000, NinjaTests.host, 54555);
	}

	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_DEBUG);
		new PingTestClient();
	}
}
