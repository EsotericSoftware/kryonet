
package com.esotericsoftware.kryonet.compress;

import static com.esotericsoftware.minlog.Log.LEVEL_TRACE;

import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.compress.DeflateCompressor;
import com.esotericsoftware.kryo.serialize.CollectionSerializer;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

public class DeflateTestServer {
	public DeflateTestServer () throws Exception {
		final Server server = new Server();
		
		Kryo kryo = server.getKryo();
		kryo.register(short[].class);
		kryo.register(SomeData.class, new DeflateCompressor(new FieldSerializer(kryo)));
		kryo.register(ArrayList.class, new CollectionSerializer(kryo));

		final SomeData data = new SomeData();
		data.text = "some text here aaaaaaaaaabbbbbbbbbbbcccccccccc";
		data.stuff = new short[] {1, 2, 3, 4, 5, 6, 7, 8};

		final ArrayList a = new ArrayList();
		a.add(12);
		a.add(null);
		a.add(34);

		new Thread(server).start();
		server.bind(54555, 54777);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				server.sendToAllTCP(data);
				connection.sendTCP(data);
				connection.sendTCP(a);
			}
		});
	}

	static public class SomeData {
		public String text;
		public short[] stuff;
	}

	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_TRACE);
		new DeflateTestServer();
	}
}
