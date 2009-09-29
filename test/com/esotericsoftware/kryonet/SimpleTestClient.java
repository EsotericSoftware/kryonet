
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.SimpleTestServer.SomeReply;
import com.esotericsoftware.kryonet.SimpleTestServer.SomeRequest;
import com.esotericsoftware.minlog.Log;

public class SimpleTestClient {
	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_DEBUG);

		final Client client = new Client();
		
		Kryo kryo = client.getKryo();
		kryo.register(SomeRequest.class);
		kryo.register(SomeReply.class);
		kryo.register(short[].class);

		client.start(false);
		client.addListener(new Listener() {
			public void connected (Connection connection) {
				client.sendUDP("This is freaking sweet!");
				client.sendTCP("This is freaking sweet!");
				client.sendTCP("meow2");
				client.sendUDP("aaaaaabbbbbbccccccddddddeeeeeeffffffgggggghhhhhh");
				client.sendTCP("aaaaaabbbbbbccccccddddddeeeeeeffffffgggggghhhhhh");
				client.sendUDP(4);
				client.sendTCP(new short[] {1, 2, 3});
			}

			public void received (Connection connection, Object object) {
				if (object instanceof SomeReply) {
					SomeReply reply = (SomeReply)object;
					System.out.println("Reply: " + reply.text);
				}
			}
		});

		client.connect(5000, NinjaTests.host, 54555, 54777);

		client.sendTCP("meow1");

		SomeRequest request = new SomeRequest();
		request.text = "Here is the request!";
		client.sendTCP(request);

		Thread.sleep(500);

		System.out.println("Remote TCP address: " + client.getRemoteAddressTCP());
		System.out.println("Remote UDP address: " + client.getRemoteAddressUDP());

		client.stop();
	}
}
