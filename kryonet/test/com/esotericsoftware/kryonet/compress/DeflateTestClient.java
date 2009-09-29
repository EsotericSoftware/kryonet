
package com.esotericsoftware.kryonet.compress;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.compress.DeflateCompressor;
import com.esotericsoftware.kryo.serialize.CollectionSerializer;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.NinjaTests;
import com.esotericsoftware.kryonet.compress.DeltaTestServer.SomeData;
import com.esotericsoftware.minlog.Log;

public class DeflateTestClient {
	public DeflateTestClient () throws IOException {
		Client client = new Client();

		Kryo kryo = client.getKryo();
		kryo.register(short[].class);
		kryo.register(SomeData.class, new DeflateCompressor(new FieldSerializer(kryo)));
		kryo.register(ArrayList.class, new CollectionSerializer(kryo));

		new Thread(client).start();
		client.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof SomeData) {
					SomeData data = (SomeData)object;
					System.out.println(data.stuff[3]);
				}
			}
		});
		client.connect(5000, NinjaTests.host, 54555, 54777);
	}

	public static void main (String[] args) throws IOException {
		Log.set(LEVEL_TRACE);
		new DeflateTestClient();
	}
}
