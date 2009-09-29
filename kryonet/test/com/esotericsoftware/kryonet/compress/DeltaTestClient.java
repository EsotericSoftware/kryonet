
package com.esotericsoftware.kryonet.compress;

import static com.esotericsoftware.minlog.Log.LEVEL_TRACE;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.compress.DeltaCompressor;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.NinjaTests;
import com.esotericsoftware.kryonet.compress.DeltaTestServer.SomeData;
import com.esotericsoftware.kryonet.compress.DeltaTestServer.SomeOtherData;
import com.esotericsoftware.minlog.Log;

public class DeltaTestClient {
	public DeltaTestClient () throws IOException {
		Client client = new Client();
		
		Kryo kryo = client.getKryo();
		kryo.register(short[].class);
		kryo.register(SomeData.class, new DeltaCompressor(kryo, new FieldSerializer(kryo)));
		kryo.register(SomeOtherData.class);

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
		new DeltaTestClient();
	}
}
