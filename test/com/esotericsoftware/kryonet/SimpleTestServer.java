
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

public class SimpleTestServer {
	static public class SomeRequest {
		public String text;

		public String toString () {
			return "SomeRequest";
		}
	}

	static public class SomeReply {
		public String text;

		public String toString () {
			return "SomeReply";
		}
	}

	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_DEBUG);

		final Server server = new Server();
		
		Kryo kryo = server.getKryo();
		kryo.register(SomeRequest.class);
		kryo.register(SomeReply.class);
		kryo.register(short[].class);

		server.start(false);
		server.bind(54555, 54777);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP("weee");
				connection.sendUDP(1203f);
			}

			public void received (Connection connection, Object object) {
				if (object instanceof SomeRequest) {
					SomeRequest request = (SomeRequest)object;
					System.out.println("Request: " + request.text);

					SomeReply reply = new SomeReply();
					reply.text = "Thanks!";
					connection.sendTCP(reply);

					System.out.println("Remote TCP address: " + connection.getRemoteAddressTCP());
					System.out.println("Remote UDP address: " + connection.getRemoteAddressUDP());

					new Thread() {
						public void run () {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException ex) {
							}
							//server.stop();
						};
					}.start();
				}
			}
		});
	}
}
