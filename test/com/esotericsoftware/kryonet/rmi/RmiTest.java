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
import com.esotericsoftware.kryonet.Server;

import java.io.IOException;

public class RmiTest extends KryoNetTestCase {
	/** In this test both the client and server have an ObjectSpace that contains a TestObject. When the client connects, the same
	 * test is run on both the client and server. The test excersizes a number of remote method calls and other features. */
	public void testRMI () throws IOException {
		Server server = new Server();
		Kryo serverKryo = server.getKryo();
		register(serverKryo);

		startEndPoint(server);
		server.bind(tcpPort, udpPort);

		final TestObjectImpl serverTestObject = new TestObjectImpl(4321);

		final ObjectSpace serverObjectSpace = new ObjectSpace();
		serverObjectSpace.register(42, serverTestObject);

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
		clientObjectSpace.register(12, clientTestObject);

		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (final Connection connection) {
				runTest(connection, 42, 4321);
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
		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads();
	}

	public void testMany () throws IOException {
		Server server = new Server();
		Kryo serverKryo = server.getKryo();
		register(serverKryo);

		startEndPoint(server);
		server.bind(tcpPort);

		final TestObjectImpl serverTestObject = new TestObjectImpl(4321);

		final ObjectSpace serverObjectSpace = new ObjectSpace();
		serverObjectSpace.register(42, serverTestObject);

		server.addListener(new Listener() {
			public void connected (final Connection connection) {
				serverObjectSpace.addConnection(connection);
			}

			public void received (Connection connection, Object object) {
				if (object instanceof MessageWithTestObject) {
					assertEquals(256 + 512 + 1024, serverTestObject.moos);
					stopEndPoints(2000);
				}
			}
		});

		// ----

		Client client = new Client();
		register(client.getKryo());

		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (final Connection connection) {
				new Thread() {
					public void run () {
						TestObject test = ObjectSpace.getRemoteObject(connection, 42, TestObject.class);
						test.other();
						// Timeout on purpose.
						try {
							((RemoteObject)test).setResponseTimeout(200);
							test.slow();
							fail();
						} catch (TimeoutException ignored) {
						}
						try {
							Thread.sleep(300);
						} catch (InterruptedException ex) {
						}
						((RemoteObject)test).setResponseTimeout(3000);
						for (int i = 0; i < 256; i++)
							assertEquals(4321f, (float)test.other());
						for (int i = 0; i < 256; i++)
							test.moo();
						for (int i = 0; i < 256; i++)
							test.moo("" + i);
						for (int i = 0; i < 256; i++)
							test.moo("" + i, 0);
						connection.sendTCP(new MessageWithTestObject());
					}
				}.start();
			}
		});
		client.connect(5000, host, tcpPort);

		waitForThreads();
	}

	static public void runTest (final Connection connection, final int id, final float other) {
		new Thread() {
			public void run () {
				TestObject test = ObjectSpace.getRemoteObject(connection, id, TestObject.class);
				RemoteObject remoteObject = (RemoteObject)test;
				// Default behavior. RMI is transparent, method calls behave like normal
				// (return values and exceptions are returned, call is synchronous)
				System.out.println("hashCode: " + test.hashCode());
				System.out.println("toString: " + test);
				test.moo();
				test.moo("Cow");
				assertEquals(other, test.other());

				// UDP calls that ignore the return value
				remoteObject.setUDP(true);
				test.moo("Meow");
				assertEquals(0f, test.other());
				remoteObject.setUDP(false);

				// Test that RMI correctly waits for the remotely invoked method to exit
				remoteObject.setResponseTimeout(5000);
				test.moo("You should see this two seconds before...", 2000);
				System.out.println("...This");
				remoteObject.setResponseTimeout(1000);

				// Try exception handling
				boolean caught = false;
				try {
					test.throwException();
				} catch (UnsupportedOperationException ex) {
					caught = true;
				}
				assertTrue(caught);

				// Return values are ignored, but exceptions are still dealt with properly

				remoteObject.setTransmitReturnValue(false);
				test.moo("Baa");
				test.other();
				caught = false;
				try {
					test.throwException();
				} catch (UnsupportedOperationException ex) {
					caught = true;
				}
				assertTrue(caught);

				// Non-blocking call that ignores the return value
				remoteObject.setNonBlocking(true);
				remoteObject.setTransmitReturnValue(false);
				test.moo("Meow");
				assertEquals(0f, test.other());

				// Non-blocking call that returns the return value
				remoteObject.setTransmitReturnValue(true);
				test.moo("Foo");

				assertEquals(0f, test.other());
				assertEquals(other, remoteObject.waitForLastResponse());

				assertEquals(0f, test.other());
				byte responseID = remoteObject.getLastResponseID();
				assertEquals(other, remoteObject.waitForResponse(responseID));

				// Non-blocking call that errors out
				remoteObject.setTransmitReturnValue(false);
				test.throwException();
				assertEquals(remoteObject.waitForLastResponse().getClass(), UnsupportedOperationException.class);

				// Call will time out if non-blocking isn't working properly
				remoteObject.setTransmitExceptions(false);
				test.moo("Mooooooooo", 3000);

				// Test sending a reference to a remote object.
				MessageWithTestObject m = new MessageWithTestObject();
				m.number = 678;
				m.text = "sometext";
				m.testObject = ObjectSpace.getRemoteObject(connection, id, TestObject.class);
				connection.sendTCP(m);
			}
		}.start();
	}

	/** Registers the same classes in the same order on both the client and server. */
	static public void register (Kryo kryo) {
		kryo.register(Object.class); // Needed for Object#toString, hashCode, etc.
		kryo.register(TestObject.class);
		kryo.register(MessageWithTestObject.class);
		kryo.register(StackTraceElement.class);
		kryo.register(StackTraceElement[].class);
		kryo.register(UnsupportedOperationException.class);
		kryo.setReferences(true); // Needed for UnsupportedOperationException, which has a circular reference in the cause field.
		ObjectSpace.registerClasses(kryo);
	}

	static public interface TestObject {
		public void throwException ();

		public void moo ();

		public void moo (String value);

		public void moo (String value, long delay);

		public float other ();

		public float slow ();
	}

	static public class TestObjectImpl implements TestObject {
		public long value = System.currentTimeMillis();
		private final float other;
		public int moos;

		public TestObjectImpl (int other) {
			this.other = other;
		}

		public void throwException () {
			throw new UnsupportedOperationException("Why would I do that?");
		}

		public void moo () {
			moos++;
			System.out.println("Moo!");
		}

		public void moo (String value) {
			moos += 2;
			System.out.println("Moo: " + value);
		}

		public void moo (String value, long delay) {
			moos += 4;
			System.out.println("Moo: " + value);
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public float other () {
			return other;
		}

		public float slow () {
			try {
				Thread.sleep(300);
			} catch (InterruptedException ex) {
			}
			return 666;
		}
	}

	static public class MessageWithTestObject {
		public int number;
		public String text;
		public TestObject testObject;
	}
}
