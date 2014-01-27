
package com.esotericsoftware.kryonet.rmi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoNetTestCase;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Listener.ThreadedListener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.rmi.ObjectSpace.RemoteObjectSerializer;

import java.io.IOException;

public class RmiSendObjectTest extends KryoNetTestCase {
	/** In this test the server has two objects in an object space. The client uses the first remote object to get the second remote
	 * object. */
	public void testRMI () throws IOException {
		Server server = new Server();
		Kryo serverKryo = server.getKryo();
		register(serverKryo);

		// After all common registrations, register OtherObjectImpl only on the server using the remote object interface ID.
		// This causes OtherObjectImpl to be serialized as OtherObject.
		int otherObjectID = serverKryo.getRegistration(OtherObject.class).getId();
		serverKryo.register(OtherObjectImpl.class, new RemoteObjectSerializer(), otherObjectID);

		startEndPoint(server);
		server.bind(tcpPort);

		// TestObjectImpl has a reference to an OtherObjectImpl.
		final TestObjectImpl serverTestObject = new TestObjectImpl();
		serverTestObject.otherObject = new OtherObjectImpl();

		// Both objects must be registered with the ObjectSpace.
		final ObjectSpace serverObjectSpace = new ObjectSpace();
		serverObjectSpace.register(42, serverTestObject);
		serverObjectSpace.register(777, serverTestObject.getOtherObject());

		server.addListener(new Listener() {
			public void connected (final Connection connection) {
				// Allow the connection to access objects in the ObjectSpace.
				serverObjectSpace.addConnection(connection);
			}

			public void received (Connection connection, Object object) {
				// The test is complete when the client sends the OtherObject instance.
				if (object == serverTestObject.getOtherObject()) stopEndPoints();
			}
		});

		// ----

		Client client = new Client();
		register(client.getKryo());
		startEndPoint(client);

		// The ThreadedListener means the network thread won't be blocked when waiting for RMI responses.
		client.addListener(new ThreadedListener(new Listener() {
			public void connected (final Connection connection) {
				TestObject test = ObjectSpace.getRemoteObject(connection, 42, TestObject.class);
				// Normal remote method call.
				assertEquals(43.21f, test.other());
				// Make a remote method call that returns another remote proxy object.
				OtherObject otherObject = test.getOtherObject();
				// Normal remote method call on the second object.
				assertEquals(12.34f, otherObject.value());
				// When a remote proxy object is sent, the other side recieves its actual remote object.
				connection.sendTCP(otherObject);
			}
		}));
		client.connect(5000, host, tcpPort);

		waitForThreads();
	}

	/** Registers the same classes in the same order on both the client and server. */
	static public void register (Kryo kryo) {
		kryo.register(TestObject.class);
		kryo.register(OtherObject.class, new RemoteObjectSerializer());
		ObjectSpace.registerClasses(kryo);
	}

	static public interface TestObject {
		public float other ();

		public OtherObject getOtherObject ();
	}

	static public class TestObjectImpl implements TestObject {
		public OtherObject otherObject;

		public float other () {
			return 43.21f;
		}

		public OtherObject getOtherObject () {
			return otherObject;
		}
	}

	static public interface OtherObject {
		public float value ();
	}

	static public class OtherObjectImpl implements OtherObject {
		public float value () {
			return 12.34f;
		}
	}
}
