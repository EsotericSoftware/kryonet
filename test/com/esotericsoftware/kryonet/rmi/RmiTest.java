
package com.esotericsoftware.kryonet.rmi;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoNetTestCase;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class RmiTest extends KryoNetTestCase {
	public void testRMI () throws IOException {
		Server server = new Server();
		register(server.getKryo());
		startEndPoint(server);
		server.bind(tcpPort);

		final ObjectSpace serverObjectSpace = new ObjectSpace();
		final TestObjectImpl serverTestObject = new TestObjectImpl(4321);
		serverObjectSpace.register((short)42, serverTestObject);

		server.addListener(new Listener() {
			public void connected (final Connection connection) {
				serverObjectSpace.addConnection(connection);
				runTest(connection, 12, 1234);
			}

			public void received (Connection connection, Object object) {
				if (!(object instanceof MessageWithTestObject)) return;
				MessageWithTestObject m = (MessageWithTestObject)object;
				System.out.println(serverTestObject.value);
				System.out.println(((TestObjectImpl)m.testObject).value);
				assertEquals(4321f, m.testObject.other());
				stopEndPoints(2000);
			}
		});

		// ----

		Client client = new Client();
		register(client.getKryo());

		ObjectSpace clientObjectSpace = new ObjectSpace(client);
		final TestObjectImpl clientTestObject = new TestObjectImpl(1234);
		clientObjectSpace.register((short)12, clientTestObject);

		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (final Connection connection) {
				RmiTest.runTest(connection, 42, 4321);
			}

			public void received (Connection connection, Object object) {
				if (!(object instanceof MessageWithTestObject)) return;
				MessageWithTestObject m = (MessageWithTestObject)object;
				System.out.println(clientTestObject.value);
				System.out.println(((TestObjectImpl)m.testObject).value);
				assertEquals(1234f, m.testObject.other());
				stopEndPoints(2000);
			}
		});
		client.connect(5000, host, tcpPort);

		waitForThreads();
	}

	static public void runTest (final Connection connection, final int id, final float other) {
		new Thread() {
			public void run () {
				TestObject test = ObjectSpace.getRemoteObject(connection, (short)id, TestObject.class);
				RemoteObject remoteObject = (RemoteObject)test;

				test.moo();
				remoteObject.setResponseTimeout(500);
				test.moo("Cow");
				assertEquals(other, test.other());

				remoteObject.setNonBlocking(true, true);
				test.moo("Meow");
				assertEquals(0f, test.other());

				remoteObject.setNonBlocking(true, false);
				test.moo("Foo");

				assertEquals(0f, test.other());
				assertEquals(other, remoteObject.waitForLastResponse());

				assertEquals(0f, test.other());
				byte responseID = remoteObject.getLastResponseID();
				assertEquals(other, remoteObject.waitForResponse(responseID));

				// Test sending a reference to a remote object.
				MessageWithTestObject m = new MessageWithTestObject();
				m.number = 678;
				m.text = "sometext";
				m.testObject = ObjectSpace.getRemoteObject(connection, (short)id, TestObject.class);
				connection.sendTCP(m);
			}
		}.start();
	}

	static public void register (Kryo kryo) {
		kryo.register(TestObject.class);
		kryo.register(MessageWithTestObject.class);
		ObjectSpace.registerClasses(kryo);
	}

	static public interface TestObject {
		public void moo ();

		public void moo (String value);

		public float other ();
	}

	static public class TestObjectImpl implements TestObject {
		public long value = System.currentTimeMillis();
		private final float other;

		public TestObjectImpl (int other) {
			this.other = other;
		}

		public void moo () {
			System.out.println("Moo!");
		}

		public void moo (String value) {
			System.out.println("Moo: " + value);
		}

		public float other () {
			return other;
		}
	}

	static public class MessageWithTestObject {
		public int number;
		public String text;
		public TestObject testObject;
	}
}
