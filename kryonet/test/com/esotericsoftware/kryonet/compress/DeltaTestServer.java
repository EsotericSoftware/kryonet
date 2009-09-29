
package com.esotericsoftware.kryonet.compress;

import static com.esotericsoftware.minlog.Log.LEVEL_TRACE;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.compress.DeltaCompressor;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

public class DeltaTestServer {
	public DeltaTestServer () throws Exception {
		final Server server = new Server();

		Kryo kryo = server.getKryo();
		kryo.register(short[].class);
		kryo.register(SomeData.class, new DeltaCompressor(kryo, new FieldSerializer(kryo), 2048, 4));
		kryo.register(SomeOtherData.class);

		final SomeData data = new SomeData();
		data.text = "some text here aaaaaaabbbbbccccc";
		data.stuff = new short[] {1, 2, 3, 4, 5, 6, 7, 8};

		new Thread(server).start();
		server.bind(54555, 54777);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				data.stuff[3] = 4;
				server.sendToAllTCP(data);

				data.stuff[3] = 123;
				connection.sendTCP(data);

				data.stuff[3] = 125;
				connection.sendTCP(data);

				SomeOtherData someOtherData = new SomeOtherData();
				someOtherData.data = data; // This child object will be delta compressed.
				someOtherData.text = "abcdefghijklmnop";
				connection.sendTCP(someOtherData);
			}

			public void received (Connection connection, Object object) {
			}
		});
	}

	static public class SomeData {
		public String text;
		public short[] stuff;
	}

	static public class SomeOtherData {
		public String text;
		public SomeData data;
	}

	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_TRACE);
		new DeltaTestServer();
	}
}
