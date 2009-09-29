
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.PingPongTestServer.Data;
import com.esotericsoftware.minlog.Log;

public class PingPongTestClient {
	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_DEBUG);

		final Data dataTCP = new Data();
		dataTCP.isTCP = true;
		final Data dataUDP = new Data();

		final Client client = new Client();
		PingPongTestServer.register(client.getKryo());
		new Thread(client).start();
		client.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof Data) {
					Data data = (Data)object;
					if (data.isTCP) {
						if (!data.equals(dataTCP)) throw new RuntimeException("Fail!");
						connection.sendTCP(data);
					} else {
						if (!data.equals(dataUDP)) throw new RuntimeException("Fail!");
						connection.sendUDP(data);
					}
				}
			}
		});

		client.connect(5000, NinjaTests.host, 54555, 54777);
	}
}
