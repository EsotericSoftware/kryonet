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
import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.kryonet.Listener.ThreadedListener;
import com.esotericsoftware.kryonet.rmi.ObjectSpace.RemoteObjectSerializer;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class RmiSeveralKryosTest extends KryoNetTestCase {
	/** In this test the server has two objects in an object space. The client uses the first remote object to get the second remote
	 * object. */
	public void testRMI () throws Exception {
		Server server0 = new Server();
		Kryo server0Kryo = server0.getKryo();
		register0(server0Kryo);

		startEndPoint(server0);
		server0.bind(tcpPort);

		// TestObjectImpl has a reference to an OtherObjectImpl.
		final TestObjectImpl server0TestObject = new TestObjectImpl();

		// Both objects must be registered with the ObjectSpace.
		final ObjectSpace serverObjectSpace0 = new ObjectSpace();
		serverObjectSpace0.register(42, server0TestObject);

		server0.addListener(new Listener() {
			public void connected (final Connection connection) {
				// Allow the connection to access objects in the ObjectSpace.
				serverObjectSpace0.addConnection(connection);
			}

			public void received (Connection connection, Object object) {
				// The test is complete when the client sends the OtherObject instance.
				if (object instanceof StopMessage) stopEndPoints();
			}
		});

		Server server1 = new Server();
		Kryo server1Kryo = server1.getKryo();
		register1(server1Kryo);

		startEndPoint(server1);
		server1.bind(tcpPort+1);

		// TestObjectImpl has a reference to an OtherObjectImpl.
		final TestObjectImpl server1TestObject = new TestObjectImpl();

		// Both objects must be registered with the ObjectSpace.
		final ObjectSpace serverObjectSpace1 = new ObjectSpace();
		serverObjectSpace1.register(42, server1TestObject);

		server1.addListener(new Listener() {
			public void connected (final Connection connection) {
				// Allow the connection to access objects in the ObjectSpace.
				serverObjectSpace1.addConnection(connection);
			}

			public void received (Connection connection, Object object) {
				// The test is complete when the client sends the OtherObject instance.
				if (object instanceof StopMessage) stopEndPoints();
			}
		});



		// ----

		Client client0 = new Client();
		register0(client0.getKryo());
		startEndPoint(client0);

		Client client1 = new Client();
		register1(client1.getKryo());
		startEndPoint(client1);

		final Connection[] connections = new Connection[2];

		client0.addListener(new Listener() {
			public void connected (final Connection connection) {
				connections[0] = connection;
			}
		});
		client0.connect(5000, host, tcpPort);

		client1.addListener(new Listener() {
			public void connected (final Connection connection) {
				connections[1] = connection;
			}
		});
		client1.connect(5000, host, tcpPort+1);

		while (connections[0] == null || connections[1] == null) {
			Thread.sleep(100);
		}
		TestObject test0 = ObjectSpace.getRemoteObject(connections[0], 42, TestObject.class);
		TestObject test1 = ObjectSpace.getRemoteObject(connections[1], 42, TestObject.class);
		assertEquals(10, test0.method(BigDecimal.TEN).intValue());
		assertEquals(1, test1.method(BigDecimal.ONE).intValue());
		connections[0].sendTCP(new StopMessage());
		connections[1].sendTCP(new StopMessage());

		waitForThreads();
	}

	/** Registers the same classes in the same order on both the client0 and server0. */
	static public void register0 (Kryo kryo) {
		ObjectSpace.registerClasses(kryo);
		kryo.register(BigDecimal.class);
		kryo.register(StopMessage.class);
		kryo.register(TestObject.class);
	}

	/** Registers the same classes in the same order on both the client1 and server1. */
	static public void register1 (Kryo kryo) {
		ObjectSpace.registerClasses(kryo);
		kryo.register(BigDecimal.class);
		kryo.register(StopMessage.class);
		kryo.register(OtherObject.class);
		kryo.register(TestObject.class);
	}

	static public interface TestObject {
		public BigDecimal method (BigDecimal value);
	}

	static public class TestObjectImpl implements TestObject {
		public BigDecimal method (BigDecimal value) {
			return value;
		}
	}

	static public interface OtherObject {
		public int method (int value);
	}

	static public class OtherObjectImpl implements OtherObject {
		public int method (int value) {
			return value;
		}
	}

	static public class StopMessage {
	}
}
