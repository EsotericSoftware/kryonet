
package com.esotericsoftware.kryonet.rmi;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.rmi.ObjectSpace;
import com.esotericsoftware.kryonet.rmi.RemoteObject;
import com.esotericsoftware.minlog.Log;

public class RmiTestServer {
	public RmiTestServer () throws IOException {
		Log.set(LEVEL_TRACE);

		Server server = new Server();
		
		Kryo kryo = server.getKryo();
		kryo.register(TestObject.class);
		kryo.register(MessageWithTestObject.class);
		ObjectSpace.registerClasses(kryo);

		server.bind(54555);
		new Thread(server).start();

		ObjectSpace objectSpace = new ObjectSpace(server);
		final TestObjectImpl testObject = new TestObjectImpl();
		objectSpace.register((short)42, new TestObjectImpl());

		server.addListener(new Listener() {
			public void connected (final Connection connection) {
				runTest(connection, 12);
			}

			public void received (Connection connection, Object object) {
				if (!(object instanceof MessageWithTestObject)) return;
				MessageWithTestObject m = (MessageWithTestObject)object;
				System.out.println(testObject.value);
				System.out.println(((TestObjectImpl)m.testObject).value);
			}
		});
	}

	static public void runTest (final Connection connection, final int id) {
		new Thread() {
			public void run () {
				TestObject test = ObjectSpace.getRemoteObject(connection, (short)id, TestObject.class);
				test.moo();
				RemoteObject remoteObject = (RemoteObject)test;
				remoteObject.setResponseTimeout(500);
				test.moo("Cow");
				System.out.println("blocking other: " + test.other());
				remoteObject.setNonBlocking(true, true);
				test.moo("Meow");
				System.out.println("nonblocking other(), ignore response: " + test.other());
				remoteObject.setNonBlocking(true, false);
				test.moo("Foo");
				System.out.println("nonblocking other(): " + test.other());
				System.out.println("waitForLastResponse: " + remoteObject.waitForLastResponse());
				System.out.println(test.other());
				System.out.println("response ID: " + remoteObject.getLastResponseID());
				System.out.println("waitForResponse(id): " + remoteObject.waitForResponse(remoteObject.getLastResponseID()));

				// Test sending a reference to a remote object.
				MessageWithTestObject m = new MessageWithTestObject();
				m.number = 678;
				m.text = "sometext";
				m.testObject = ObjectSpace.getRemoteObject(connection, (short)id, TestObject.class);
				connection.sendTCP(m);
			}
		}.start();
	}

	static public interface TestObject {
		public void moo ();

		public void moo (String value);

		public float other ();
	}

	static public class TestObjectImpl implements TestObject {
		public long value = System.currentTimeMillis();

		public void moo () {
			System.out.println("Moo!");
		}

		public void moo (String value) {
			System.out.println("Moo: " + value);
		}

		public float other () {
			System.out.println("other");
			return 123;
		}
	}

	static public class MessageWithTestObject {
		public int number;
		public String text;
		public TestObject testObject;
	}

	public static void main (String[] args) throws IOException {
		new RmiTestServer();
	}
}
