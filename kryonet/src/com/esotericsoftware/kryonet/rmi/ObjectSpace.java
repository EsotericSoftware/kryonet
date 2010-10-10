
package com.esotericsoftware.kryonet.rmi;

import static com.esotericsoftware.minlog.Log.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import com.esotericsoftware.kryo.CustomSerialization;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryo.serialize.IntSerializer;
import com.esotericsoftware.kryo.util.IntHashMap;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.EndPoint;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;

/**
 * Allows methods on objects to be invoked remotely over TCP. Objects are {@link #register(int, Object) registered} with an ID.
 * The remote end of connections that have been {@link #addConnection(Connection) added} are allowed to
 * {@link #getRemoteObject(Connection, int, Class) access} registered objects.
 * <p>
 * It costs at least 2 bytes more to use remote method invocation than just sending the parameters. If the method has a return
 * value which is not {@link RemoteObject#setNonBlocking(boolean, boolean) ignored}, an extra byte is written. If the type of a
 * parameter is not final (note primitives are final) then an extra byte is written for that parameter.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class ObjectSpace {
	static private final Object instancesLock = new Object();
	static ObjectSpace[] instances = new ObjectSpace[0];
	static private final HashMap<Class, CachedMethod[]> methodCache = new HashMap();

	final IntHashMap idToObject = new IntHashMap();
	Connection[] connections = {};
	final Object connectionsLock = new Object();

	private final Listener invokeListener = new Listener() {
		public void received (Connection connection, Object object) {
			if (!(object instanceof InvokeMethod)) return;
			if (connections != null) {
				int i = 0, n = connections.length;
				for (; i < n; i++)
					if (connection == connections[i]) break;
				if (i == n) return; // The InvokeMethod message is not for a connection in this ObjectSpace.
			}
			InvokeMethod invokeMethod = (InvokeMethod)object;
			Object target = idToObject.get(invokeMethod.objectID);
			if (target == null) {
				if (WARN) warn("kryonet", "Ignoring remote invocation request for unknown object ID: " + invokeMethod.objectID);
				return;
			}
			invoke(connection, target, invokeMethod);
		}

		public void disconnected (Connection connection) {
			removeConnection(connection);
		}
	};

	/**
	 * Creates an ObjectSpace with no connections. Connections must be {@link #addConnection(Connection) added} to allow the remote
	 * end of the connections to access objects in this ObjectSpace.
	 */
	public ObjectSpace () {
		synchronized (instancesLock) {
			ObjectSpace[] instances = ObjectSpace.instances;
			ObjectSpace[] newInstances = new ObjectSpace[instances.length + 1];
			newInstances[0] = this;
			System.arraycopy(instances, 0, newInstances, 1, instances.length);
			ObjectSpace.instances = newInstances;
		}
	}

	/**
	 * Creates an ObjectSpace with the specified connection. More connections can be {@link #addConnection(Connection) added}.
	 */
	public ObjectSpace (Connection connection) {
		this();
		addConnection(connection);
	}

	/**
	 * Registers an object to allow the remote end of the ObjectSpace's connections to access it using the specified ID.
	 * <p>
	 * If a connection is added to multiple ObjectSpaces, the same object ID should not be registered in more than one of those
	 * ObjectSpaces.
	 * @see #getRemoteObject(Connection, int, Class...)
	 */
	public void register (int objectID, Object object) {
		if (object == null) throw new IllegalArgumentException("object cannot be null.");
		idToObject.put(objectID, object);
		if (TRACE) trace("kryonet", "Object registered with ObjectSpace as " + objectID + ": " + object);
	}

	/**
	 * Causes this ObjectSpace to stop listening to the connections for method invocation messages.
	 */
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

	/**
	 * Allows the remote end of the specified connection to access objects registered in this ObjectSpace.
	 */
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

	/**
	 * Removes the specified connection, it will no longer be able to access objects registered in this ObjectSpace.
	 */
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

	/**
	 * Invokes the method on the object and, if necessary, sends the result back to the connection that made the invocation
	 * request. This method is invoked on the update thread of the {@link EndPoint} for this ObjectSpace and can be overridden to
	 * perform invocations on a different thread.
	 * @param connection The remote side of this connection requested the invocation.
	 */
	protected void invoke (Connection connection, Object target, InvokeMethod invokeMethod) {
		if (DEBUG) {
			String argString = "";
			if (invokeMethod.args != null) {
				argString = Arrays.deepToString(invokeMethod.args);
				argString = argString.substring(1, argString.length() - 1);
			}
			debug("kryonet", connection + " received: " + target.getClass().getSimpleName() + "#" + invokeMethod.method.getName()
				+ "(" + argString + ")");
		}

		Object result;
		Method method = invokeMethod.method;
		try {
			result = method.invoke(target, invokeMethod.args);
		} catch (Exception ex) {
			throw new RuntimeException("Error invoking method: " + method.getDeclaringClass().getName() + "." + method.getName(), ex);
		}

		byte responseID = invokeMethod.responseID;
		if (method.getReturnType() == void.class || responseID == 0) return;

		InvokeMethodResult invokeMethodResult = new InvokeMethodResult();
		invokeMethodResult.objectID = invokeMethod.objectID;
		invokeMethodResult.responseID = responseID;
		invokeMethodResult.result = result;
		int length = connection.sendTCP(invokeMethodResult);
		if (DEBUG) debug("kryonet", connection + " sent: " + result + " (" + length + ")");
	}

	/**
	 * Identical to {@link #getRemoteObject(Connection, int, Class...)} except returns the object cast to the specified interface
	 * type. The returned object still implements {@link RemoteObject}.
	 */
	static public <T> T getRemoteObject (final Connection connection, int objectID, Class<T> iface) {
		return (T)getRemoteObject(connection, objectID, new Class[] {iface});
	}

	/**
	 * Returns a proxy object that implements the specified interfaces. Methods invoked on the proxy object will be invoked
	 * remotely on the object with the specified ID in the ObjectSpace for the specified connection. If the remote end of the
	 * connection has not {@link #addConnection(Connection) added} the connection to the ObjectSpace, the remote method invocations
	 * will be ignored.
	 * <p>
	 * Methods that return a value will throw {@link TimeoutException} if the response is not received with the
	 * {@link RemoteObject#setResponseTimeout(int) response timeout}.
	 * <p>
	 * If {@link RemoteObject#setNonBlocking(boolean, boolean) non-blocking} is false (the default), then methods that return a
	 * value must not be called from the update thread for the connection. An exception will be thrown if this occurs. Methods with
	 * a void return value can be called on the update thread.
	 * <p>
	 * If a proxy returned from this method is part of an object graph sent over the network, the object graph on the receiving
	 * side will have the proxy object replaced with the registered object.
	 * @see RemoteObject
	 */
	static public RemoteObject getRemoteObject (Connection connection, int objectID, Class... ifaces) {
		if (connection == null) throw new IllegalArgumentException("connection cannot be null.");
		if (ifaces == null) throw new IllegalArgumentException("ifaces cannot be null.");
		Class[] temp = new Class[ifaces.length + 1];
		temp[0] = RemoteObject.class;
		System.arraycopy(ifaces, 0, temp, 1, ifaces.length);
		return (RemoteObject)Proxy.newProxyInstance(ObjectSpace.class.getClassLoader(), temp, new RemoteInvocationHandler(
			connection, objectID));
	}

	/**
	 * Handles network communication when methods are invoked on a proxy.
	 */
	static private class RemoteInvocationHandler implements InvocationHandler {
		private final Connection connection;
		final int objectID;
		private int timeoutMillis = 3000;
		private boolean nonBlocking, ignoreResponses;
		private Byte lastResponseID;
		final ArrayList<InvokeMethodResult> responseQueue = new ArrayList();
		private byte nextResponseID = 1;
		private Listener responseListener;

		public RemoteInvocationHandler (Connection connection, final int objectID) {
			super();
			this.connection = connection;
			this.objectID = objectID;

			responseListener = new Listener() {
				public void received (Connection connection, Object object) {
					if (!(object instanceof InvokeMethodResult)) return;
					InvokeMethodResult invokeMethodResult = (InvokeMethodResult)object;
					if (invokeMethodResult.objectID != objectID) return;
					synchronized (responseQueue) {
						responseQueue.add(invokeMethodResult);
						responseQueue.notifyAll();
					}
				}

				public void disconnected (Connection connection) {
					close();
				}
			};
			connection.addListener(responseListener);
		}

		public Object invoke (Object proxy, Method method, Object[] args) {
			if (method.getDeclaringClass() == RemoteObject.class) {
				String name = method.getName();
				if (name.equals("close")) {
					close();
					return null;
				} else if (name.equals("setResponseTimeout")) {
					timeoutMillis = (Integer)args[0];
					return null;
				} else if (name.equals("setNonBlocking")) {
					nonBlocking = (Boolean)args[0];
					ignoreResponses = (Boolean)args[1];
					return null;
				} else if (name.equals("waitForLastResponse")) {
					if (lastResponseID == null) throw new IllegalStateException("There is no last response to wait for.");
					return waitForResponse(lastResponseID);
				} else if (name.equals("getLastResponseID")) {
					if (lastResponseID == null) throw new IllegalStateException("There is no last response ID.");
					return lastResponseID;
				} else if (name.equals("waitForResponse")) {
					if (ignoreResponses) throw new IllegalStateException("This RemoteObject is configured to ignore all responses.");
					return waitForResponse((Byte)args[0]);
				}
			}

			InvokeMethod invokeMethod = new InvokeMethod();
			invokeMethod.objectID = objectID;
			invokeMethod.method = method;
			invokeMethod.args = args;
			boolean hasReturnValue = method.getReturnType() != void.class;
			if (hasReturnValue && !ignoreResponses) {
				byte responseID = nextResponseID++;
				if (nextResponseID == 0) nextResponseID++; // Zero means don't send back a response.
				invokeMethod.responseID = responseID;
			}
			int length = connection.sendTCP(invokeMethod);
			if (DEBUG) {
				String argString = "";
				if (args != null) {
					argString = Arrays.deepToString(args);
					argString = argString.substring(1, argString.length() - 1);
				}
				debug("kryonet", connection + " sent: " + method.getDeclaringClass().getSimpleName() + "#" + method.getName() + "("
					+ argString + ") (" + length + ")");
			}

			if (!hasReturnValue) return null;
			if (nonBlocking) {
				if (!ignoreResponses) lastResponseID = invokeMethod.responseID;
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
				return waitForResponse(invokeMethod.responseID);
			} catch (TimeoutException ex) {
				throw new TimeoutException("Response timed out: " + method.getDeclaringClass().getName() + "." + method.getName());
			}
		}

		private Object waitForResponse (int responseID) {
			if (connection.getEndPoint().getUpdateThread() == Thread.currentThread())
				throw new IllegalStateException("Cannot wait for an RMI response on the connection's update thread.");

			long endTime = System.currentTimeMillis() + timeoutMillis;
			synchronized (responseQueue) {
				while (true) {
					int remaining = (int)(endTime - System.currentTimeMillis());
					for (int i = responseQueue.size() - 1; i >= 0; i++) {
						InvokeMethodResult invokeMethodResult = responseQueue.get(i);
						if (invokeMethodResult.responseID == responseID) {
							responseQueue.remove(invokeMethodResult);
							lastResponseID = null;
							return invokeMethodResult.result;
						}
					}
					if (remaining <= 0) throw new TimeoutException("Response timed out.");
					try {
						responseQueue.wait(remaining);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}

		void close () {
			connection.removeListener(responseListener);
		}
	}

	/**
	 * Internal message to invoke methods remotely.
	 */
	static public class InvokeMethod implements FrameworkMessage, CustomSerialization {
		public int objectID;
		public Method method;
		public Object[] args;
		public byte responseID;

		public void writeObjectData (Kryo kryo, ByteBuffer buffer) {
			IntSerializer.put(buffer, objectID, true);

			int methodClassID = kryo.getRegisteredClass(method.getDeclaringClass()).getID();
			IntSerializer.put(buffer, methodClassID, true);

			CachedMethod[] cachedMethods = getMethods(kryo, method.getDeclaringClass());
			CachedMethod cachedMethod = null;
			for (int i = 0, n = cachedMethods.length; i < n; i++) {
				cachedMethod = cachedMethods[i];
				if (cachedMethod.method.equals(method)) {
					buffer.put((byte)i);
					break;
				}
			}

			for (int i = 0, n = cachedMethod.serializers.length; i < n; i++) {
				Serializer serializer = cachedMethod.serializers[i];
				if (serializer != null)
					serializer.writeObject(buffer, args[i]);
				else
					kryo.writeClassAndObject(buffer, args[i]);
			}

			if (method.getReturnType() != void.class) buffer.put(responseID);
		}

		public void readObjectData (Kryo kryo, ByteBuffer buffer) {
			objectID = IntSerializer.get(buffer, true);

			int methodClassID = IntSerializer.get(buffer, true);
			Class methodClass = kryo.getRegisteredClass(methodClassID).getType();
			byte methodIndex = buffer.get();
			CachedMethod cachedMethod;
			try {
				cachedMethod = getMethods(kryo, methodClass)[methodIndex];
			} catch (IndexOutOfBoundsException ex) {
				throw new SerializationException("Invalid method index " + methodIndex + " for class: " + methodClass.getName());
			}
			method = cachedMethod.method;

			args = new Object[cachedMethod.serializers.length];
			for (int i = 0, n = args.length; i < n; i++) {
				Serializer serializer = cachedMethod.serializers[i];
				if (serializer != null)
					args[i] = serializer.readObject(buffer, method.getParameterTypes()[i]);
				else
					args[i] = kryo.readClassAndObject(buffer);
			}

			if (method.getReturnType() != void.class) responseID = buffer.get();
		}
	}

	/**
	 * Internal message to return the result of a remotely invoked method.
	 */
	static public class InvokeMethodResult implements FrameworkMessage {
		public int objectID;
		public byte responseID;
		public Object result;
	}

	static CachedMethod[] getMethods (Kryo kryo, Class type) {
		CachedMethod[] cachedMethods = methodCache.get(type);
		if (cachedMethods != null) return cachedMethods;

		ArrayList<Method> allMethods = new ArrayList();
		Class nextClass = type;
		while (nextClass != null && nextClass != Object.class) {
			Collections.addAll(allMethods, nextClass.getDeclaredMethods());
			nextClass = nextClass.getSuperclass();
		}
		PriorityQueue<Method> methods = new PriorityQueue(Math.max(1, allMethods.size()), new Comparator<Method>() {
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
		for (int i = 0, n = allMethods.size(); i < n; i++) {
			Method method = allMethods.get(i);
			int modifiers = method.getModifiers();
			if (Modifier.isStatic(modifiers)) continue;
			if (Modifier.isPrivate(modifiers)) continue;
			if (method.isSynthetic()) continue;
			methods.add(method);
		}

		int n = methods.size();
		cachedMethods = new CachedMethod[n];
		for (int i = 0; i < n; i++) {
			CachedMethod cachedMethod = new CachedMethod();
			cachedMethod.method = methods.poll();

			// Store the serializer for each final parameter.
			Class[] parameterTypes = cachedMethod.method.getParameterTypes();
			cachedMethod.serializers = new Serializer[parameterTypes.length];
			for (int ii = 0, nn = parameterTypes.length; ii < nn; ii++)
				if (Kryo.isFinal(parameterTypes[ii])) cachedMethod.serializers[ii] = kryo.getSerializer(parameterTypes[ii]);

			cachedMethods[i] = cachedMethod;
		}
		methodCache.put(type, cachedMethods);
		return cachedMethods;
	}

	/**
	 * Returns the first object registered with the specified ID in any of the ObjectSpaces the specified connection belongs to.
	 */
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

	/**
	 * Registers the classes needed to use ObjectSpaces. This should be called before any connections are opened.
	 * @see EndPoint#getKryo()
	 * @see Kryo#register(Class, Serializer)
	 */
	static public void registerClasses (Kryo kryo) {
		kryo.register(Object[].class);
		kryo.register(InvokeMethod.class);

		FieldSerializer serializer = (FieldSerializer)kryo.register(InvokeMethodResult.class).getSerializer();
		serializer.getField("objectID").setClass(int.class, new IntSerializer(true));

		kryo.register(InvocationHandler.class, new Serializer() {
			public void writeObjectData (ByteBuffer buffer, Object object) {
				RemoteInvocationHandler handler = (RemoteInvocationHandler)Proxy.getInvocationHandler(object);
				IntSerializer.put(buffer, handler.objectID, true);
			}

			public <T> T readObjectData (ByteBuffer buffer, Class<T> type) {
				int objectID = IntSerializer.get(buffer, true);
				Connection connection = (Connection)Kryo.getContext().get("connection");
				Object object = getRegisteredObject(connection, objectID);
				if (WARN && object == null) warn("kryonet", "Unknown object ID " + objectID + " for connection: " + connection);
				return (T)object;
			}
		});
	}

	static class CachedMethod {
		Method method;
		Serializer[] serializers;
	}
}
