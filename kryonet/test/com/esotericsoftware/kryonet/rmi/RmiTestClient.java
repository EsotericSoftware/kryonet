
package com.esotericsoftware.kryonet.rmi;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.NinjaTests;
import com.esotericsoftware.kryonet.rmi.ObjectSpace;
import com.esotericsoftware.kryonet.rmi.RmiTestServer.MessageWithTestObject;
import com.esotericsoftware.kryonet.rmi.RmiTestServer.TestObject;
import com.esotericsoftware.kryonet.rmi.RmiTestServer.TestObjectImpl;
import com.esotericsoftware.minlog.Log;

public class RmiTestClient {
	public static void main (String[] args) throws IOException {
		Log.set(LEVEL_TRACE);

		Client client = new Client();

		Kryo kryo = client.getKryo();
		kryo.register(TestObject.class);
		kryo.register(MessageWithTestObject.class);
		ObjectSpace.registerClasses(kryo);

		ObjectSpace objectSpace = new ObjectSpace(client);
		final TestObjectImpl testObject = new TestObjectImpl();
		objectSpace.register((short)12, testObject);

		new Thread(client).start();
		client.addListener(new Listener() {
			public void connected (final Connection connection) {
				RmiTestServer.runTest(connection, 42);
			}

			public void received (Connection connection, Object object) {
				if (!(object instanceof MessageWithTestObject)) return;
				MessageWithTestObject m = (MessageWithTestObject)object;
				System.out.println(testObject.value);
				System.out.println(((TestObjectImpl)m.testObject).value);
			}
		});
		client.connect(5000, NinjaTests.host, 54555);
	}
}
