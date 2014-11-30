/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

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
