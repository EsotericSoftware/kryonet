
package com.esotericsoftware.kryonet.compress;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.compress.DeltaCompressor;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoNetTestCase;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class DeltaTest extends KryoNetTestCase {
	public void testDelta () throws IOException {
		final Server server = new Server();
		register(server.getKryo());

		final SomeData data = new SomeData();
		data.text = "some text here aaaaaaabbbbbccccc";
		data.stuff = new short[] {1, 2, 3, 4, 5, 6, 7, 8};

		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				data.stuff[3] = 4;
				server.sendToAllTCP(data);

				data.stuff[3] = 123;
				connection.sendTCP(data);

				data.stuff[3] = 125;
				connection.sendTCP(data);

				data.stuff[3] = 127;
				SomeOtherData someOtherData = new SomeOtherData();
				someOtherData.data = data; // This child object will be delta compressed.
				someOtherData.text = "abcdefghijklmnop";
				connection.sendTCP(someOtherData);
			}

			public void received (Connection connection, Object object) {
			}
		});

		// ----

		Client client = new Client();
		register(client.getKryo());
		startEndPoint(client);
		client.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof SomeData) {
					SomeData data = (SomeData)object;
					System.out.println(data.stuff[3]);
				} else if (object instanceof SomeOtherData) {
					SomeOtherData otherData = (SomeOtherData)object;
					System.out.println(otherData.data.stuff[3]);
					assertEquals(127, data.stuff[3]);
					stopEndPoints();
				}
			}
		});
		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads();
	}

	static public void register (Kryo kryo) {
		kryo.register(short[].class);
		kryo.register(SomeData.class, new DeltaCompressor(kryo, new FieldSerializer(kryo, SomeData.class), 2048, 4));
		kryo.register(SomeOtherData.class);
	}

	static public class SomeData {
		public String text;
		public short[] stuff;
	}

	static public class SomeOtherData {
		public String text;
		public SomeData data;
	}
}
