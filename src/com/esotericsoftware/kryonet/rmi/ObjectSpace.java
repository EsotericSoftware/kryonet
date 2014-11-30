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

import static com.esotericsoftware.minlog.Log.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.Util;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.EndPoint;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.KryoNetException;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.util.ObjectIntMap;
import com.esotericsoftware.reflectasm.MethodAccess;

/** Allows methods on objects to be invoked remotely over TCP or UDP. Objects are {@link #register(int, Object) registered} with an
 * ID. The remote end of connections that have been {@link #addConnection(Connection) added} are allowed to
 * {@link #getRemoteObject(Connection, int, Class) access} registered objects.
 * <p>
 * It costs at least 2 bytes more to use remote method invocation than just sending the parameters. If the method has a return
 * value which is not {@link RemoteObject#setNonBlocking(boolean) ignored}, an extra byte is written. If the type of a parameter is
 * not final (note primitives are final) then an extra byte is written for that parameter.
 * @author Nathan Sweet <misc@n4te.com> */
public class ObjectSpace {
	static private final int returnValueMask = 1 << 7;
	static private final int returnExceptionMask = 1 << 6;
	static private final int responseIdMask = 0xff & ~returnValueMask & ~returnExceptionMask;

	static private final Object instancesLock = new Object();
	static ObjectSpace[] instances = new ObjectSpace[0];
	static private final HashMap<Class, CachedMethod[]> methodCache = new HashMap();
	static private boolean asm = true;

	final IntMap idToObject = new IntMap();
	final ObjectIntMap objectToID = new ObjectIntMap();
	Connection[] connections = {};
	final Object connectionsLock = new Object();
	Executor executor;

	private final Listener invokeListener = new Listener() {
		public void received (final Connection connection, Object object) {
			if (!(object instanceof InvokeMethod)) return;
			if (connections != null) {
				int i = 0, n = connections.length;
				for (; i < n; i++)
					if (connection == connections[i]) break;
				if (i == n) return; // The InvokeMethod message is not for a connection in this ObjectSpace.
			}
			final InvokeMethod invokeMethod = (InvokeMethod)object;
			final Object target = idToObject.get(invokeMethod.objectID);
			if (target == null) {
				if (WARN) warn("kryonet", "Ignoring remote invocation request for unknown object ID: " + invokeMethod.objectID);
				return;
			}
			if (executor == null)
				invoke(connection, target, invokeMethod);
			else {
				executor.execute(new Runnable() {
					public void run () {
						invoke(connection, target, invokeMethod);
					}
				});
			}
		}

		public void disconnected (Connection connection) {
			removeConnection(connection);
		}
	};

	/** Creates an ObjectSpace with no connections. Connections must be {@link #addConnection(Connection) added} to allow the remote
	 * end of the connections to access objects in this ObjectSpace. */
	public ObjectSpace () {
		synchronized (instancesLock) {
			ObjectSpace[] instances = ObjectSpace.instances;
			ObjectSpace[] newInstances = new ObjectSpace[instances.length + 1];
			newInstances[0] = this;
			System.arraycopy(instances, 0, newInstances, 1, instances.length);
			ObjectSpace.instances = newInstances;
		}
	}

	/** Creates an ObjectSpace with the specified connection. More connections can be {@link #addConnection(Connection) added}. */
	public ObjectSpace (Connection connection) {
		this();
		addConnection(connection);
	}

	/** Sets the executor used to invoke methods when an invocation is received from a remote endpoint. By default, no executor is
	 * set and invocations occur on the network thread, which should not be blocked for long.
	 * @param executor May be null. */
	public void setExecutor (Executor executor) {
		this.executor = executor;
	}

	/** Registers an object to allow the remote end of the ObjectSpace's connections to access it using the specified ID.
	 * <p>
	 * If a connection is added to multiple ObjectSpaces, the same object ID should not be registered in more than one of those
	 * ObjectSpaces.
	 * @param objectID Must not be Integer.MAX_VALUE.
	 * @see #getRemoteObject(Connection, int, Class...) */
	public void register (int objectID, Object object) {
		if (objectID == Integer.MAX_VALUE) throw new IllegalArgumentException("objectID cannot be Integer.MAX_VALUE.");
		if (object == null) throw new IllegalArgumentException("object cannot be null.");
		idToObject.put(objectID, object);
		objectToID.put(object, objectID);
		if (TRACE) trace("kryonet", "Object registered with ObjectSpace as " + objectID + ": " + object);
	}

	/** Removes an object. The remote end of the ObjectSpace's connections will no longer be able to access it. */
	public void remove (int objectID) {
		Object object = idToObject.remove(objectID);
		if (object != null) objectToID.remove(object, 0);
		if (TRACE) trace("kryonet", "Object " + objectID + " removed from ObjectSpace: " + object);
	}

	/** Removes an object. The remote end of the ObjectSpace's connections will no longer be able to access it. */
	public void remove (Object object) {
		if (!idToObject.containsValue(object, true)) return;
		int objectID = idToObject.findKey(object, true, -1);
		idToObject.remove(objectID);
		objectToID.remove(object, 0);
		if (TRACE) trace("kryonet", "Object " + objectID + " removed from ObjectSpace: " + object);
	}

	/** Causes this ObjectSpace to stop listening to the connections for method invocation messages. */
	public void close () {
		Connection[] connections = this.connections;
		for (int i = 0; i < connections.length; i++)
			connections[i].removeListener(invokeListener);

		synchronized (instancesLock) {
			ArrayList<Connection> temp = new ArrayList(Arrays.asList(instances));
			temp.remove(this);
			instances = temp.toArray(new ObjectSpace[temp.size()]);
		}

		if (TRACE) trace("kryonet", "Closed ObjectSpace.");
	}

	/** Allows the remote end of the specified connection to access objects registered in this ObjectSpace. */
	public void addConnection (Connection connection) {
		if (connection == null) throw new IllegalArgumentException("connection cannot be null.");

		synchronized (connectionsLock) {
			Connection[] newConnections = new Connection[connections.length + 1];
			newConnections[0] = connection;
			System.arraycopy(connections, 0, newConnections, 1, connections.length);
			connections = newConnections;
		}

		connection.addListener(invokeListener);

		if (TRACE) trace("kryonet", "Added connection to ObjectSpace: " + connection);
	}

	/** Removes the specified connection, it will no longer be able to access objects registered in this ObjectSpace. */
	public void removeConnection (Connection connection) {
		if (connection == null) throw new IllegalArgumentException("connection cannot be null.");

		connection.removeListener(invokeListener);

		synchronized (connectionsLock) {
			ArrayList<Connection> temp = new ArrayList(Arrays.asList(connections));
			temp.remove(connection);
			connections = temp.toArray(new Connection[temp.size()]);
		}

		if (TRACE) trace("kryonet", "Removed connection from ObjectSpace: " + connection);
	}

	/** Invokes the method on the object and, if necessary, sends the result back to the connection that made the invocation
	 * request. This method is invoked on the update thread of the {@link EndPoint} for this ObjectSpace and unless an
	 * {@link #setExecutor(Executor) executor} has been set.
	 * @param connection The remote side of this connection requested the invocation. */
	protected void invoke (Connection connection, Object target, InvokeMethod invokeMethod) {
		if (DEBUG) {
			String argString = "";
			if (invokeMethod.args != null) {
				argString = Arrays.deepToString(invokeMethod.args);
				argString = argString.substring(1, argString.length() - 1);
			}
			debug("kryonet",
				connection + " received: " + target.getClass().getSimpleName() + "#" + invokeMethod.cachedMethod.method.getName()
					+ "(" + argString + ")");
		}

		byte responseData = invokeMethod.responseData;
		boolean transmitReturnValue = (responseData & returnValueMask) == returnValueMask;
		boolean transmitExceptions = (responseData & returnExceptionMask) == returnExceptionMask;
		int responseID = responseData & responseIdMask;

		CachedMethod cachedMethod = invokeMethod.cachedMethod;
		Object result = null;
		try {
			result = cachedMethod.invoke(target, invokeMethod.args);
		} catch (InvocationTargetException ex) {
			if (transmitExceptions)
				result = ex.getCause();
			else
				throw new KryoNetException("Error invoking method: " + cachedMethod.method.getDeclaringClass().getName() + "."
					+ cachedMethod.method.getName(), ex);
		} catch (Exception ex) {
			throw new KryoNetException("Error invoking method: " + cachedMethod.method.getDeclaringClass().getName() + "."
				+ cachedMethod.method.getName(), ex);
		}

		if (responseID == 0) return;

		InvokeMethodResult invokeMethodResult = new InvokeMethodResult();
		invokeMethodResult.objectID = invokeMethod.objectID;
		invokeMethodResult.responseID = (byte)responseID;

		// Do not return non-primitives if transmitReturnValue is false.
		if (!transmitReturnValue && !invokeMethod.cachedMethod.method.getReturnType().isPrimitive()) {
			invokeMethodResult.result = null;
		} else {
			invokeMethodResult.result = result;
		}

		int length = connection.sendTCP(invokeMethodResult);
		if (DEBUG) debug("kryonet", connection + " sent TCP: " + result + " (" + length + ")");
	}

	/** Identical to {@link #getRemoteObject(Connection, int, Class...)} except returns the object cast to the specified interface
	 * type. The returned object still implements {@link RemoteObject}. */
	static public <T> T getRemoteObject (final Connection connection, int objectID, Class<T> iface) {
		return (T)getRemoteObject(connection, objectID, new Class[] {iface});
	}

	/** Returns a proxy object that implements the specified interfaces. Methods invoked on the proxy object will be invoked
	 * remotely on the object with the specified ID in the ObjectSpace for the specified connection. If the remote end of the
	 * connection has not {@link #addConnection(Connection) added} the connection to the ObjectSpace, the remote method invocations
	 * will be ignored.
	 * <p>
	 * Methods that return a value will throw {@link TimeoutException} if the response is not received with the
	 * {@link RemoteObject#setResponseTimeout(int) response timeout}.
	 * <p>
	 * If {@link RemoteObject#setNonBlocking(boolean) non-blocking} is false (the default), then methods that return a value must
	 * not be called from the update thread for the connection. An exception will be thrown if this occurs. Methods with a void
	 * return value can be called on the update thread.
	 * <p>
	 * If a proxy returned from this method is part of an object graph sent over the network, the object graph on the receiving
	 * side will have the proxy object replaced with the registered object.
	 * @see RemoteObject */
	static public RemoteObject getRemoteObject (Connection connection, int objectID, Class... ifaces) {
		if (connection == null) throw new IllegalArgumentException("connection cannot be null.");
		if (ifaces == null) throw new IllegalArgumentException("ifaces cannot be null.");
		Class[] temp = new Class[ifaces.length + 1];
		temp[0] = RemoteObject.class;
		System.arraycopy(ifaces, 0, temp, 1, ifaces.length);
		return (RemoteObject)Proxy.newProxyInstance(ObjectSpace.class.getClassLoader(), temp, new RemoteInvocationHandler(
			connection, objectID));
	}

	/** Handles network communication when methods are invoked on a proxy. */
	static private class RemoteInvocationHandler implements InvocationHandler {
		private final Connection connection;
		final int objectID;
		private int timeoutMillis = 3000;
		private boolean nonBlocking;
		private boolean transmitReturnValue = true;
		private boolean transmitExceptions = true;
		private boolean remoteToString;
		private boolean udp;
		private Byte lastResponseID;
		private byte nextResponseId = 1;
		private Listener responseListener;

		final ReentrantLock lock = new ReentrantLock();
		final Condition responseCondition = lock.newCondition();
		final InvokeMethodResult[] responseTable = new InvokeMethodResult[64];
		final boolean[] pendingResponses = new boolean[64];

		public RemoteInvocationHandler (Connection connection, final int objectID) {
			super();
			this.connection = connection;
			this.objectID = objectID;

			responseListener = new Listener() {
				public void received (Connection connection, Object object) {
					if (!(object instanceof InvokeMethodResult)) return;
					InvokeMethodResult invokeMethodResult = (InvokeMethodResult)object;
					if (invokeMethodResult.objectID != objectID) return;

					int responseID = invokeMethodResult.responseID;
					synchronized (this) {
						if (pendingResponses[responseID]) responseTable[responseID] = invokeMethodResult;
					}

					lock.lock();
					try {
						responseCondition.signalAll();
					} finally {
						lock.unlock();
					}
				}

				public void disconnected (Connection connection) {
					close();
				}
			};
			connection.addListener(responseListener);
		}

		public Object invoke (Object proxy, Method method, Object[] args) throws Exception {
			Class declaringClass = method.getDeclaringClass();
			if (declaringClass == RemoteObject.class) {
				String name = method.getName();
				if (name.equals("close")) {
					close();
					return null;
				} else if (name.equals("setResponseTimeout")) {
					timeoutMillis = (Integer)args[0];
					return null;
				} else if (name.equals("setNonBlocking")) {
					nonBlocking = (Boolean)args[0];
					return null;
				} else if (name.equals("setTransmitReturnValue")) {
					transmitReturnValue = (Boolean)args[0];
					return null;
				} else if (name.equals("setUDP")) {
					udp = (Boolean)args[0];
					return null;
				} else if (name.equals("setTransmitExceptions")) {
					transmitExceptions = (Boolean)args[0];
					return null;
				} else if (name.equals("setRemoteToString")) {
					remoteToString = (Boolean)args[0];
					return null;
				} else if (name.equals("waitForLastResponse")) {
					if (lastResponseID == null) throw new IllegalStateException("There is no last response to wait for.");
					return waitForResponse(lastResponseID);
				} else if (name.equals("getLastResponseID")) {
					if (lastResponseID == null) throw new IllegalStateException("There is no last response ID.");
					return lastResponseID;
				} else if (name.equals("waitForResponse")) {
					if (!transmitReturnValue && !transmitExceptions && nonBlocking)
						throw new IllegalStateException("This RemoteObject is currently set to ignore all responses.");
					return waitForResponse((Byte)args[0]);
				} else if (name.equals("getConnection")) {
					return connection;
				}
				// Should never happen, for debugging purposes only
				throw new KryoNetException("Invocation handler could not find RemoteObject method. Check ObjectSpace.java");
			} else if (!remoteToString && declaringClass == Object.class && method.getName().equals("toString")) //
				return "<proxy>";

			InvokeMethod invokeMethod = new InvokeMethod();
			invokeMethod.objectID = objectID;
			invokeMethod.args = args;

			CachedMethod[] cachedMethods = getMethods(connection.getEndPoint().getKryo(), method.getDeclaringClass());
			for (int i = 0, n = cachedMethods.length; i < n; i++) {
				CachedMethod cachedMethod = cachedMethods[i];
				if (cachedMethod.method.equals(method)) {
					invokeMethod.cachedMethod = cachedMethod;
					break;
				}
			}
			if (invokeMethod.cachedMethod == null) throw new KryoNetException("Method not found: " + method);

			// A invocation doesn't need a response if it's async and no return values or exceptions are wanted back.
			boolean needsResponse = !udp && (transmitReturnValue || transmitExceptions || !nonBlocking);
			byte responseID = 0;
			if (needsResponse) {
				synchronized (this) {
					// Increment the response counter and put it into the low bits of the responseID.
					responseID = nextResponseId++;
					if (nextResponseId > responseIdMask) nextResponseId = 1;
					pendingResponses[responseID] = true;
				}
				// Pack other data into the high bits.
				byte responseData = responseID;
				if (transmitReturnValue) responseData |= returnValueMask;
				if (transmitExceptions) responseData |= returnExceptionMask;
				invokeMethod.responseData = responseData;
			} else {
				invokeMethod.responseData = 0; // A response data of 0 means to not respond.
			}
			int length = udp ? connection.sendUDP(invokeMethod) : connection.sendTCP(invokeMethod);
			if (DEBUG) {
				String argString = "";
				if (args != null) {
					argString = Arrays.deepToString(args);
					argString = argString.substring(1, argString.length() - 1);
				}
				debug("kryonet", connection + " sent " + (udp ? "UDP" : "TCP") + ": " + method.getDeclaringClass().getSimpleName()
					+ "#" + method.getName() + "(" + argString + ") (" + length + ")");
			}

			lastResponseID = (byte)(invokeMethod.responseData & responseIdMask);
			if (nonBlocking || udp) {
				Class returnType = method.getReturnType();
				if (returnType.isPrimitive()) {
					if (returnType == int.class) return 0;
					if (returnType == boolean.class) return Boolean.FALSE;
					if (returnType == float.class) return 0f;
					if (returnType == char.class) return (char)0;
					if (returnType == long.class) return 0l;
					if (returnType == short.class) return (short)0;
					if (returnType == byte.class) return (byte)0;
					if (returnType == double.class) return 0d;
				}
				return null;
			}
			try {
				Object result = waitForResponse(lastResponseID);
				if (result != null && result instanceof Exception)
					throw (Exception)result;
				else
					return result;
			} catch (TimeoutException ex) {
				throw new TimeoutException("Response timed out: " + method.getDeclaringClass().getName() + "." + method.getName());
			} finally {
				synchronized (this) {
					pendingResponses[responseID] = false;
					responseTable[responseID] = null;
				}
			}
		}

		private Object waitForResponse (byte responseID) {
			if (connection.getEndPoint().getUpdateThread() == Thread.currentThread())
				throw new IllegalStateException("Cannot wait for an RMI response on the connection's update thread.");

			long endTime = System.currentTimeMillis() + timeoutMillis;

			while (true) {
				long remaining = endTime - System.currentTimeMillis();
				InvokeMethodResult invokeMethodResult;
				synchronized (this) {
					invokeMethodResult = responseTable[responseID];
				}
				if (invokeMethodResult != null) {
					lastResponseID = null;
					return invokeMethodResult.result;
				} else {
					if (remaining <= 0) throw new TimeoutException("Response timed out.");

					lock.lock();
					try {
						responseCondition.await(remaining, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new KryoNetException(e);
					} finally {
						lock.unlock();
					}
				}
			}
		}

		void close () {
			connection.removeListener(responseListener);
		}
	}

	/** Internal message to invoke methods remotely. */
	static public class InvokeMethod implements FrameworkMessage, KryoSerializable {
		public int objectID;
		public CachedMethod cachedMethod;
		public Object[] args;

		// The top bits of the ID indicate if the remote invocation should respond with return values and exceptions, respectively.
		// The remaining bites are a counter. This means up to 63 responses can be stored before undefined behavior occurs due to
		// possible duplicate IDs. A response data of 0 means to not respond.
		public byte responseData;

		public void write (Kryo kryo, Output output) {
			output.writeInt(objectID, true);
			output.writeInt(cachedMethod.methodClassID, true);
			output.writeByte(cachedMethod.methodIndex);

			Serializer[] serializers = cachedMethod.serializers;
			Object[] args = this.args;
			for (int i = 0, n = serializers.length; i < n; i++) {
				Serializer serializer = serializers[i];
				if (serializer != null)
					kryo.writeObjectOrNull(output, args[i], serializer);
				else
					kryo.writeClassAndObject(output, args[i]);
			}

			output.writeByte(responseData);
		}

		public void read (Kryo kryo, Input input) {
			objectID = input.readInt(true);

			int methodClassID = input.readInt(true);
			Class methodClass = kryo.getRegistration(methodClassID).getType();

			byte methodIndex = input.readByte();
			try {
				cachedMethod = getMethods(kryo, methodClass)[methodIndex];
			} catch (IndexOutOfBoundsException ex) {
				throw new KryoException("Invalid method index " + methodIndex + " for class: " + methodClass.getName());
			}

			Serializer[] serializers = cachedMethod.serializers;
			Class[] parameterTypes = cachedMethod.method.getParameterTypes();
			Object[] args = new Object[serializers.length];
			this.args = args;
			for (int i = 0, n = args.length; i < n; i++) {
				Serializer serializer = serializers[i];
				if (serializer != null)
					args[i] = kryo.readObjectOrNull(input, parameterTypes[i], serializer);
				else
					args[i] = kryo.readClassAndObject(input);
			}

			responseData = input.readByte();
		}
	}

	/** Internal message to return the result of a remotely invoked method. */
	static public class InvokeMethodResult implements FrameworkMessage {
		public int objectID;
		public byte responseID;
		public Object result;
	}

	static CachedMethod[] getMethods (Kryo kryo, Class type) {
		CachedMethod[] cachedMethods = methodCache.get(type); // Maybe should cache per Kryo instance?
		if (cachedMethods != null) return cachedMethods;

		ArrayList<Method> allMethods = new ArrayList();
		Class nextClass = type;
		while (nextClass != null) {
			Collections.addAll(allMethods, nextClass.getDeclaredMethods());
			nextClass = nextClass.getSuperclass();
			if (nextClass == Object.class) break;
		}
		ArrayList<Method> methods = new ArrayList(Math.max(1, allMethods.size()));
		for (int i = 0, n = allMethods.size(); i < n; i++) {
			Method method = allMethods.get(i);
			int modifiers = method.getModifiers();
			if (Modifier.isStatic(modifiers)) continue;
			if (Modifier.isPrivate(modifiers)) continue;
			if (method.isSynthetic()) continue;
			methods.add(method);
		}
		Collections.sort(methods, new Comparator<Method>() {
			public int compare (Method o1, Method o2) {
				// Methods are sorted so they can be represented as an index.
				int diff = o1.getName().compareTo(o2.getName());
				if (diff != 0) return diff;
				Class[] argTypes1 = o1.getParameterTypes();
				Class[] argTypes2 = o2.getParameterTypes();
				if (argTypes1.length > argTypes2.length) return 1;
				if (argTypes1.length < argTypes2.length) return -1;
				for (int i = 0; i < argTypes1.length; i++) {
					diff = argTypes1[i].getName().compareTo(argTypes2[i].getName());
					if (diff != 0) return diff;
				}
				throw new RuntimeException("Two methods with same signature!"); // Impossible.
			}
		});

		Object methodAccess = null;
		if (asm && !Util.isAndroid && Modifier.isPublic(type.getModifiers())) methodAccess = MethodAccess.get(type);

		int n = methods.size();
		cachedMethods = new CachedMethod[n];
		for (int i = 0; i < n; i++) {
			Method method = methods.get(i);
			Class[] parameterTypes = method.getParameterTypes();

			CachedMethod cachedMethod = null;
			if (methodAccess != null) {
				try {
					AsmCachedMethod asmCachedMethod = new AsmCachedMethod();
					asmCachedMethod.methodAccessIndex = ((MethodAccess)methodAccess).getIndex(method.getName(), parameterTypes);
					asmCachedMethod.methodAccess = (MethodAccess)methodAccess;
					cachedMethod = asmCachedMethod;
				} catch (RuntimeException ignored) {
				}
			}

			if (cachedMethod == null) cachedMethod = new CachedMethod();
			cachedMethod.method = method;
			cachedMethod.methodClassID = kryo.getRegistration(method.getDeclaringClass()).getId();
			cachedMethod.methodIndex = i;

			// Store the serializer for each final parameter.
			cachedMethod.serializers = new Serializer[parameterTypes.length];
			for (int ii = 0, nn = parameterTypes.length; ii < nn; ii++)
				if (kryo.isFinal(parameterTypes[ii])) cachedMethod.serializers[ii] = kryo.getSerializer(parameterTypes[ii]);

			cachedMethods[i] = cachedMethod;
		}
		methodCache.put(type, cachedMethods);
		return cachedMethods;
	}

	/** Returns the first object registered with the specified ID in any of the ObjectSpaces the specified connection belongs to. */
	static Object getRegisteredObject (Connection connection, int objectID) {
		ObjectSpace[] instances = ObjectSpace.instances;
		for (int i = 0, n = instances.length; i < n; i++) {
			ObjectSpace objectSpace = instances[i];
			// Check if the connection is in this ObjectSpace.
			Connection[] connections = objectSpace.connections;
			for (int j = 0; j < connections.length; j++) {
				if (connections[j] != connection) continue;
				// Find an object with the objectID.
				Object object = objectSpace.idToObject.get(objectID);
				if (object != null) return object;
			}
		}
		return null;
	}

	/** Returns the first ID registered for the specified object with any of the ObjectSpaces the specified connection belongs to,
	 * or Integer.MAX_VALUE if not found. */
	static int getRegisteredID (Connection connection, Object object) {
		ObjectSpace[] instances = ObjectSpace.instances;
		for (int i = 0, n = instances.length; i < n; i++) {
			ObjectSpace objectSpace = instances[i];
			// Check if the connection is in this ObjectSpace.
			Connection[] connections = objectSpace.connections;
			for (int j = 0; j < connections.length; j++) {
				if (connections[j] != connection) continue;
				// Find an ID with the object.
				int id = objectSpace.objectToID.get(object, Integer.MAX_VALUE);
				if (id != Integer.MAX_VALUE) return id;
			}
		}
		return Integer.MAX_VALUE;
	}

	/** Registers the classes needed to use ObjectSpaces. This should be called before any connections are opened.
	 * @see Kryo#register(Class, Serializer) */
	static public void registerClasses (final Kryo kryo) {
		kryo.register(Object[].class);
		kryo.register(InvokeMethod.class);

		FieldSerializer<InvokeMethodResult> resultSerializer = new FieldSerializer<InvokeMethodResult>(kryo,
			InvokeMethodResult.class) {
			public void write (Kryo kryo, Output output, InvokeMethodResult result) {
				super.write(kryo, output, result);
				output.writeInt(result.objectID, true);
			}

			public InvokeMethodResult read (Kryo kryo, Input input, Class<InvokeMethodResult> type) {
				InvokeMethodResult result = super.read(kryo, input, type);
				result.objectID = input.readInt(true);
				return result;
			}
		};
		resultSerializer.removeField("objectID");
		kryo.register(InvokeMethodResult.class, resultSerializer);

		kryo.register(InvocationHandler.class, new Serializer() {
			public void write (Kryo kryo, Output output, Object object) {
				RemoteInvocationHandler handler = (RemoteInvocationHandler)Proxy.getInvocationHandler(object);
				output.writeInt(handler.objectID, true);
			}

			public Object read (Kryo kryo, Input input, Class type) {
				int objectID = input.readInt(true);
				Connection connection = (Connection)kryo.getContext().get("connection");
				Object object = getRegisteredObject(connection, objectID);
				if (WARN && object == null) warn("kryonet", "Unknown object ID " + objectID + " for connection: " + connection);
				return object;
			}
		});
	}

	/** If true, an attempt will be made to use ReflectASM for invoking methods. Default is true. */
	static public void setAsm (boolean asm) {
		ObjectSpace.asm = asm;
	}

	static class CachedMethod {
		Method method;
		int methodClassID;
		int methodIndex;
		Serializer[] serializers;

		public Object invoke (Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
			return method.invoke(target, args);
		}
	}

	static class AsmCachedMethod extends CachedMethod {
		MethodAccess methodAccess;
		int methodAccessIndex = -1;

		public Object invoke (Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
			try {
				return methodAccess.invoke(target, methodAccessIndex, args);
			} catch (Exception ex) {
				throw new InvocationTargetException(ex);
			}
		}
	}

	/** Serializes an object registered with an ObjectSpace so the receiving side gets a {@link RemoteObject} proxy rather than the
	 * bytes for the serialized object.
	 * @author Nathan Sweet <misc@n4te.com> */
	static public class RemoteObjectSerializer extends Serializer {
		public void write (Kryo kryo, Output output, Object object) {
			Connection connection = (Connection)kryo.getContext().get("connection");
			int id = getRegisteredID(connection, object);
			if (id == Integer.MAX_VALUE) throw new KryoNetException("Object not found in an ObjectSpace: " + object);
			output.writeInt(id, true);
		}

		public Object read (Kryo kryo, Input input, Class type) {
			int objectID = input.readInt(true);
			Connection connection = (Connection)kryo.getContext().get("connection");
			return ObjectSpace.getRemoteObject(connection, objectID, type);
		}
	}
}
