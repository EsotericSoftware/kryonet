
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Used to be notified about connection events. These methods will be invoked on the same thread as {@link Client#update(int)} and
 * {@link Server#update(int)}. They should not block for long periods as other network activity will not be processed until they
 * return.
 */
public class Listener {
	public void connected (Connection connection) {
	}

	public void disconnected (Connection connection) {
	}

	public void received (Connection connection, Object object) {
	}

	/**
	 * Uses reflection to called "received(Connection, XXX)" on the listener, where XXX is the received object type. Note this
	 * class uses a HashMap lookup and (cached) reflection, so is not as efficient as writing a series of "instanceof" statements.
	 */
	static public class ReflectionListener extends Listener {
		private HashMap<Class, Method> classToMethod = new HashMap();

		public void received (Connection connection, Object object) {
			Class type = object.getClass();
			Method method = classToMethod.get(type);
			if (method == null) {
				if (classToMethod.containsKey(type)) return; // Only fail on the first attempt to find the method.
				try {
					method = getClass().getMethod("received", new Class[] {Connection.class, type});
				} catch (SecurityException ex) {
					if (ERROR) error("kryonet", "Unable to access method: received(Connection, " + type.getName() + ")", ex);
					return;
				} catch (NoSuchMethodException ex) {
					if (DEBUG)
						debug("kryonet", "Unable to find listener method: " + getClass().getName() + "#received(Connection, "
							+ type.getName() + ")");
					return;
				} finally {
					classToMethod.put(type, method);
				}
			}
			try {
				method.invoke(this, connection, object);
			} catch (Throwable ex) {
				if (ex instanceof InvocationTargetException && ex.getCause() != null) ex = ex.getCause();
				if (ex instanceof RuntimeException) throw (RuntimeException)ex;
				throw new RuntimeException("Error invoking method: " + getClass().getName() + "#received(Connection, "
					+ type.getName() + ")", ex);
			}
		}
	}

	/**
	 * Wraps a listener and queues notifications as {@link Runnable runnables}. This allows the runnables to be processed on a
	 * different thread, preventing the connection's update thread from being blocked.
	 */
	static public abstract class QueuedListener extends Listener {
		final Listener listener;

		public QueuedListener (Listener listener) {
			if (listener == null) throw new IllegalArgumentException("listener cannot be null.");
			this.listener = listener;
		}

		public void connected (final Connection connection) {
			queue(new Runnable() {
				public void run () {
					listener.connected(connection);
				}
			});
		}

		public void disconnected (final Connection connection) {
			queue(new Runnable() {
				public void run () {
					listener.disconnected(connection);
				}
			});
		}

		public void received (final Connection connection, final Object object) {
			queue(new Runnable() {
				public void run () {
					listener.received(connection, object);
				}
			});
		}

		abstract protected void queue (Runnable runnable);
	}

	/**
	 * Wraps a listener and processes notification events on a separate thread.
	 */
	static public class ThreadedListener extends QueuedListener {
		private ExecutorService threadPool;

		/**
		 * Creates a single thread to process notification events.
		 */
		public ThreadedListener (Listener listener) {
			this(listener, Executors.newFixedThreadPool(1));
		}

		/**
		 * Uses the specified threadPool to process notification events.
		 */
		public ThreadedListener (Listener listener, ExecutorService threadPool) {
			super(listener);
			if (threadPool == null) throw new IllegalArgumentException("threadPool cannot be null.");
			this.threadPool = threadPool;
		}

		public void queue (Runnable runnable) {
			threadPool.execute(runnable);
		}
	}
}
