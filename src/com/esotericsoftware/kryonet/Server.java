
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryo.util.ShortHashMap;
import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterTCP;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterUDP;

/**
 * Manages TCP and optionally UDP connections from many {@link Client Clients}.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Server implements EndPoint {
	private final Kryo kryo;
	private int bufferSize;
	private Selector selector;
	private ServerSocketChannel serverChannel;
	private UdpConnection udp;
	private Connection[] connections = {};
	private ShortHashMap<Connection> pendingConnections = new ShortHashMap();
	Listener[] listeners = {};
	private Object listenerLock = new Object();
	private short nextConnectionID = 1;
	private volatile boolean shutdown;
	private Object updateLock = new Object();
	private Thread updateThread;

	private Listener dispatchListener = new Listener() {
		public void connected (Connection connection) {
			Listener[] listeners = Server.this.listeners;
			for (int i = 0, n = listeners.length; i < n; i++)
				listeners[i].connected(connection);
		}

		public void disconnected (Connection connection) {
			removeConnection(connection);
			Listener[] listeners = Server.this.listeners;
			for (int i = 0, n = listeners.length; i < n; i++)
				listeners[i].disconnected(connection);
		}

		public void received (Connection connection, Object object) {
			Listener[] listeners = Server.this.listeners;
			for (int i = 0, n = listeners.length; i < n; i++)
				listeners[i].received(connection, object);
		}
	};

	/**
	 * Creates a Server with a buffer size of 2048.
	 */
	public Server () {
		this(2048);
	}

	/**
	 * @param bufferSize The maximum size an object may be after serialization.
	 */
	public Server (int bufferSize) {
		this.bufferSize = bufferSize;

		kryo = new Kryo();
		FieldSerializer fieldSerializer = new FieldSerializer(kryo);
		kryo.register(RegisterTCP.class, fieldSerializer);
		kryo.register(RegisterUDP.class, fieldSerializer);
		kryo.register(KeepAlive.class, fieldSerializer);
		kryo.register(DiscoverHost.class, fieldSerializer);
		kryo.register(Ping.class, fieldSerializer);

		try {
			selector = Selector.open();
		} catch (IOException ex) {
			throw new RuntimeException("Error opening selector.", ex);
		}
	}

	/**
	 * Gets the Kryo instance that will be used to serialize and deserialize objects.
	 */
	public Kryo getKryo () {
		return kryo;
	}

	/**
	 * Opens a TCP only server.
	 * @throws IOException if the server could not be opened.
	 */
	public void bind (int tcpPort) throws IOException {
		bind(tcpPort, -1);
	}

	/**
	 * Opens a TCP and UDP server.
	 * @throws IOException if the server could not be opened.
	 */
	public void bind (int tcpPort, int udpPort) throws IOException {
		close();
		synchronized (updateLock) {
			try {
				selector.wakeup();
				serverChannel = selector.provider().openServerSocketChannel();
				serverChannel.socket().bind(new InetSocketAddress(tcpPort));
				serverChannel.configureBlocking(false);
				serverChannel.register(selector, SelectionKey.OP_ACCEPT);
				if (DEBUG) debug("kryonet", "Accepting connections on port: " + tcpPort + "/TCP");

				if (udpPort != -1) {
					udp = new UdpConnection(kryo, bufferSize);
					udp.bind(selector, udpPort);
					if (DEBUG) debug("kryonet", "Accepting connections on port: " + udpPort + "/UDP");
				}
			} catch (IOException ex) {
				close();
				throw ex;
			}
		}
		if (INFO) info("kryonet", "Server started.");
	}

	/**
	 * Accepts any new connections and reads or writes any pending data for the current connections.
	 * @param timeout Wait for up to the specified milliseconds for a connection to be ready to process. May be zero to return
	 *           immediately if there are no connections to process.
	 */
	public void update (int timeout) throws IOException {
		updateThread = Thread.currentThread();
		synchronized (updateLock) { // Causes blocking while the selector is used to bind the server connection.
		}
		if (timeout > 0) {
			selector.select(timeout);
		} else {
			selector.selectNow();
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		outer: //
		for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
			SelectionKey selectionKey = iter.next();
			iter.remove();
			try {
				int ops = selectionKey.readyOps();
				Connection keyConnection = (Connection)selectionKey.attachment();

				if (keyConnection != null) {
					// Must be a TCP read or write operation.
					if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
						try {
							while (true) {
								Object object = keyConnection.tcp.readObject(keyConnection);
								if (object == null) break;
								if (DEBUG) {
									if (!(object instanceof FrameworkMessage)) {
										debug("kryonet", keyConnection + " received TCP: " + object);
									} else if (TRACE) {
										trace("kryonet", keyConnection + " received TCP: " + object);
									}
								}
								keyConnection.notifyReceived(object);
							}
						} catch (IOException ex) {
							if (TRACE) {
								trace("kryonet", "Unable to read TCP from: " + keyConnection, ex);
							} else if (DEBUG) {
								debug("kryonet", keyConnection + " update: " + ex.getMessage());
							}
							keyConnection.close();
						} catch (SerializationException ex) {
							if (ERROR) error("kryonet", "Error reading TCP from connection: " + keyConnection, ex);
							keyConnection.close();
						}
					}
					if ((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
						try {
							keyConnection.tcp.writeOperation();
						} catch (IOException ex) {
							if (TRACE) {
								trace("kryonet", "Unable to write TCP to connection: " + keyConnection, ex);
							} else if (DEBUG) {
								debug("kryonet", keyConnection + " update: " + ex.getMessage());
							}
							keyConnection.close();
						}
					}
					continue;
				}

				if ((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
					try {
						SocketChannel socketChannel = serverChannel.accept();
						if (socketChannel != null) acceptOperation(socketChannel);
					} catch (IOException ex) {
						if (DEBUG) debug("kryonet", "Unable to accept new connection.", ex);
					}
					continue;
				}

				// Must be a UDP read operation.
				if (udp == null) continue;
				InetSocketAddress fromAddress;
				try {
					fromAddress = udp.readFromAddress();
				} catch (IOException ex) {
					IOException ioEx = new IOException("Error reading UDP data.");
					ioEx.initCause(ex);
					throw ioEx;
				}
				if (fromAddress == null) continue;

				Connection[] connections = this.connections;
				Connection fromConnection = null;
				for (int i = 0, n = connections.length; i < n; i++) {
					Connection connection = connections[i];
					if (fromAddress.equals(connection.udpRemoteAddress)) {
						fromConnection = connection;
						break;
					}
				}

				Object object;
				try {
					object = udp.readObject(fromConnection);
				} catch (SerializationException ex) {
					if (WARN) {
						Connection errorConnection = null;
						for (int i = 0, n = connections.length; i < n; i++) {
							Connection connection = connections[i];
							if (fromAddress.equals(connection.udpRemoteAddress)) {
								errorConnection = connection;
								break;
							}
						}
						if (errorConnection != null) {
							if (ERROR) error("kryonet", "Error reading UDP from connection: " + errorConnection, ex);
						} else
							warn("kryonet", "Error reading UDP from unregistered address: " + fromAddress, ex);
					}
					continue;
				}

				if (object instanceof FrameworkMessage) {
					if (object instanceof RegisterUDP) {
						// Store the fromAddress on the connection and reply over TCP with a RegisterUDP to indicate success.
						short fromConnectionID = ((RegisterUDP)object).connectionID;
						Connection connection = pendingConnections.remove(fromConnectionID);
						if (connection != null) {
							if (connection.udpRemoteAddress != null) continue outer;
							connection.udpRemoteAddress = fromAddress;
							addConnection(connection);
							connection.sendTCP(new RegisterUDP());
							if (DEBUG)
								debug("kryonet", "Port " + udp.datagramChannel.socket().getLocalPort() + "/UDP connected to: "
									+ fromAddress);
							connection.notifyConnected();
							continue;
						}
						if (DEBUG) debug("kryonet", "Ignoring incoming RegisterUDP with invalid connection ID: " + fromConnectionID);
						continue;
					}
					if (object instanceof DiscoverHost) {
						udp.datagramChannel.send(udp.writeBuffer, fromAddress);
						if (DEBUG) debug("kryonet", "Responded to host discovery from: " + fromAddress);
						continue;
					}
				}

				if (fromConnection != null) {
					if (DEBUG) {
						if (object instanceof KeepAlive) {
							if (TRACE) trace("kryonet", fromConnection + " received UDP: " + object);
						} else
							debug("kryonet", fromConnection + " received UDP: " + object);
					}
					fromConnection.notifyReceived(object);
					continue;
				}
				if (DEBUG) debug("kryonet", "Ignoring UDP from unregistered address: " + fromAddress);
			} catch (CancelledKeyException ignored) {
				// Connection is closed.
			}
		}
	}

	public void run () {
		if (TRACE) trace("kryonet", "Server thread started.");
		shutdown = false;
		while (!shutdown) {
			try {
				update(500);
			} catch (IOException ex) {
				if (ERROR) error("kryonet", "Error updating server connections.", ex);
				close();
			}
		}
		if (TRACE) trace("kryonet", "Server thread stopped.");
	}

	public void start (boolean isDaemon) {
		Thread thread = new Thread(this, "Server");
		thread.setDaemon(isDaemon);
		thread.start();
	}

	public void stop () {
		if (shutdown) return;
		close();
		if (TRACE) trace("kryonet", "Server thread stopping.");
		shutdown = true;
		selector.wakeup();
	}

	private void acceptOperation (SocketChannel socketChannel) {
		Connection connection = newConnection(bufferSize);
		connection.initialize(kryo, bufferSize);
		connection.endPoint = this;
		if (udp != null) connection.udp = udp;
		try {
			SelectionKey selectionKey = connection.tcp.accept(selector, socketChannel);
			selectionKey.attach(connection);

			short id = nextConnectionID++;
			connection.id = id;
			connection.addListener(dispatchListener);

			if (udp == null)
				addConnection(connection);
			else
				pendingConnections.put(id, connection);

			RegisterTCP registerConnection = new RegisterTCP();
			registerConnection.connectionID = id;
			connection.sendTCP(registerConnection);

			if (udp == null) connection.notifyConnected();
		} catch (IOException ex) {
			connection.close();
			if (DEBUG) debug("kryonet", "Unable to accept TCP connection.", ex);
		}
	}

	/**
	 * Allows the connections used by the server to be subclassed. This can be useful for storage per connection without an
	 * additional lookup.
	 */
	protected Connection newConnection (int bufferSize) {
		return new Connection();
	}

	private void addConnection (Connection connection) {
		Connection[] newConnections = new Connection[connections.length + 1];
		newConnections[0] = connection;
		System.arraycopy(connections, 0, newConnections, 1, connections.length);
		connections = newConnections;
	}

	void removeConnection (Connection connection) {
		ArrayList<Connection> temp = new ArrayList(Arrays.asList(connections));
		temp.remove(connection);
		connections = temp.toArray(new Connection[temp.size()]);

		pendingConnections.remove(connection.id);
	}

	public void sendToAllTCP (Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			connection.sendTCP(object);
		}
	}

	public void sendToAllExceptTCP (short connectionID, Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			if (connection.id != connectionID) connection.sendTCP(object);
		}
	}

	public void sendToTCP (short connectionID, Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			if (connection.id == connectionID) {
				connection.sendTCP(object);
				break;
			}
		}
	}

	public void sendToAllUDP (Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			connection.sendUDP(object);
		}
	}

	public void sendToAllExceptUDP (short connectionID, Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			if (connection.id != connectionID) connection.sendUDP(object);
		}
	}

	public void sendToUDP (short connectionID, Object object) {
		Connection[] connections = this.connections;
		for (int i = 0, n = connections.length; i < n; i++) {
			Connection connection = connections[i];
			if (connection.id == connectionID) {
				connection.sendUDP(object);
				break;
			}
		}
	}

	public void addListener (Listener listener) {
		if (listener == null) throw new IllegalArgumentException("listener cannot be null.");
		synchronized (listenerLock) {
			Listener[] listeners = this.listeners;
			int n = listeners.length;
			for (int i = 0; i < n; i++)
				if (listener == listeners[i]) return;
			Listener[] newListeners = new Listener[n + 1];
			newListeners[0] = listener;
			System.arraycopy(listeners, 0, newListeners, 1, n);
			this.listeners = newListeners;
		}
		if (TRACE) trace("kryonet", "Server listener added: " + listener.getClass().getName());
	}

	public void removeListener (Listener listener) {
		if (listener == null) throw new IllegalArgumentException("listener cannot be null.");
		synchronized (listenerLock) {
			Listener[] listeners = this.listeners;
			int n = listeners.length;
			Listener[] newListeners = new Listener[n - 1];
			for (int i = 0, ii = 0; i < n; i++) {
				Listener copyListener = listeners[i];
				if (listener == copyListener) continue;
				if (ii == n - 1) return;
				newListeners[ii++] = copyListener;
			}
			this.listeners = newListeners;
		}
		if (TRACE) trace("kryonet", "Server listener removed: " + listener.getClass().getName());
	}

	/**
	 * Closes all open connections and the server ports.
	 */
	public void close () {
		Connection[] connections = this.connections;
		if (INFO && connections.length > 0) info("kryonet", "Closing server connections...");
		for (int i = 0, n = connections.length; i < n; i++)
			connections[i].close();
		connections = new Connection[0];
		if (serverChannel != null) {
			try {
				serverChannel.close();
				selector.selectNow();
				selector.wakeup();
				if (INFO) info("kryonet", "Server closed.");
			} catch (IOException ex) {
				if (DEBUG) debug("kryonet", "Unable to close server.", ex);
			}
			serverChannel = null;
		}
		if (udp != null) {
			udp.close();
			udp = null;
		}
	}

	public Thread getUpdateThread () {
		return updateThread;
	}
}
