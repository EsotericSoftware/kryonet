
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryo.serialize.ShortSerializer;
import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterTCP;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterUDP;

/**
 * Represents a TCP and optionally a UDP connection to a {@link Server}.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Client extends Connection implements EndPoint {
	private final Kryo kryo;
	private Selector selector;
	private boolean udpRegistered;
	private Object udpRegistrationLock = new Object();
	private volatile boolean shutdown;
	private final Object updateLock = new Object();
	private Thread updateThread;

	/**
	 * Creates a Client with a buffer size of 2048.
	 */
	public Client () {
		this(2048);
	}

	/**
	 * @param bufferSize The maximum size an object may be after serialization.
	 */
	public Client (int bufferSize) {
		super();
		endPoint = this;

		kryo = new Kryo();
		FieldSerializer fieldSerializer = new FieldSerializer(kryo);
		kryo.register(RegisterTCP.class, fieldSerializer);
		kryo.register(RegisterUDP.class, fieldSerializer);
		kryo.register(KeepAlive.class, fieldSerializer);
		kryo.register(DiscoverHost.class, fieldSerializer);
		kryo.register(Ping.class, fieldSerializer);

		initialize(kryo, bufferSize);

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
	 * Opens a TCP only client.
	 * @see #connect(int, InetAddress, int, int)
	 */
	public void connect (int timeout, String host, int tcpPort) throws IOException {
		connect(timeout, InetAddress.getByName(host), tcpPort, -1);
	}

	/**
	 * Opens a TCP and UDP client.
	 * @see #connect(int, InetAddress, int, int)
	 */
	public void connect (int timeout, String host, int tcpPort, int udpPort) throws IOException {
		connect(timeout, InetAddress.getByName(host), tcpPort, udpPort);
	}

	/**
	 * Opens a TCP only client.
	 * @see #connect(int, InetAddress, int, int)
	 */
	public void connect (int timeout, InetAddress host, int tcpPort) throws IOException {
		connect(timeout, host, tcpPort, -1);
	}

	/**
	 * Opens a TCP and UDP client. Blocks until the connection is complete or the timeout is reached.
	 * <p>
	 * Because the framework must perform some minimal communication before the connection is considered successful,
	 * {@link #update(int)} must be called on a separate thread during the connection process.
	 * @throws IOException if the client could not be opened or connecting times out.
	 */
	public void connect (int timeout, InetAddress host, int tcpPort, int udpPort) throws IOException {
		if (host == null) throw new IllegalArgumentException("host cannot be null.");
		close();
		try {
			if (udpPort != -1) udp = new UdpConnection(kryo, tcp.readBuffer.capacity());

			long endTime;
			synchronized (updateLock) {
				selector.wakeup();
				endTime = System.currentTimeMillis() + timeout;
				tcp.connect(selector, new InetSocketAddress(host, tcpPort), 5000);
			}

			// Wait for RegisterTCP.
			while (System.currentTimeMillis() < endTime && id == -1) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {
				}
			}
			if (id == -1) throw new SocketTimeoutException("Connected, but timed out during TCP registration.");

			if (udpPort != -1) {
				InetSocketAddress udpAddress = new InetSocketAddress(host, udpPort);
				synchronized (updateLock) {
					selector.wakeup();
					udp.connect(selector, udpAddress);
				}

				// Wait for RegisterUDP reply.
				synchronized (udpRegistrationLock) {
					while (System.currentTimeMillis() < endTime && !udpRegistered) {
						RegisterUDP registerUDP = new RegisterUDP();
						registerUDP.connectionID = id;
						udp.send(this, registerUDP, udpAddress);
						try {
							udpRegistrationLock.wait(200);
						} catch (InterruptedException ignored) {
						}
					}
					if (!udpRegistered) throw new SocketTimeoutException("Connected, but timed out during UDP registration.");
				}
			}
		} catch (IOException ex) {
			close();
			throw ex;
		}
	}

	/**
	 * Reads or writes any pending data for this client.
	 * @param timeout Wait for up to the specified milliseconds for data to be ready to process. May be zero to return immediately
	 *           if there is no data to process.
	 */
	public void update (int timeout) throws IOException {
		updateThread = Thread.currentThread();
		synchronized (updateLock) { // Causes blocking while the selector is used to establish a new connection.
		}
		if (timeout > 0) {
			selector.select(timeout);
		} else {
			selector.selectNow();
		}
		Set<SelectionKey> keys = selector.selectedKeys();
		for (Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();) {
			SelectionKey selectionKey = iter.next();
			iter.remove();
			try {
				int ops = selectionKey.readyOps();
				if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
					if (selectionKey.attachment() == tcp) {
						while (true) {
							Object object = tcp.readObject(this);
							if (object == null) break;
							if (id == -1 || (udp != null && !udpRegistered)) {
								if (object instanceof RegisterTCP) id = ((RegisterTCP)object).connectionID;
								if (object instanceof RegisterUDP) {
									synchronized (udpRegistrationLock) {
										udpRegistered = true;
										udpRegistrationLock.notifyAll();
									}
									if (DEBUG)
										debug("Port " + udp.datagramChannel.socket().getLocalPort() + "/UDP connected to: "
											+ udp.connectedAddress);
								}
								if (id != -1 && (udp == null || udpRegistered)) notifyConnected();
								continue;
							}
							if (DEBUG) {
								if (!(object instanceof FrameworkMessage)) {
									debug(this + " received TCP: " + object);
								} else if (TRACE) {
									trace(this + " received TCP: " + object);
								}
							}
							notifyReceived(object);
						}
					} else {
						if (udp.readFromAddress() == null) continue;
						Object object = udp.readObject(this);
						if (object == null) continue;
						if (DEBUG) debug(this + " received UDP: " + object);
						notifyReceived(object);
					}
				}
				if ((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) tcp.writeOperation();
			} catch (CancelledKeyException ignored) {
				// Connection is closed.
			}
		}
		if (id != -1 && tcp.needsKeepAlive()) sendTCP(FrameworkMessage.keepAlive);
		if (udp != null && udpRegistered && udp.needsKeepAlive()) sendUDP(FrameworkMessage.keepAlive);
	}

	public void run () {
		if (TRACE) trace("Client thread started.");
		shutdown = false;
		while (!shutdown) {
			try {
				update(500);
			} catch (IOException ex) {
				if (TRACE) {
					if (id != -1)
						trace("Unable to update connection: " + this, ex);
					else
						trace("Unable to update connection.", ex);
				} else if (DEBUG) {
					if (id != -1)
						debug(this + " update: " + ex.getMessage());
					else
						debug("Unable to update connection: " + ex.getMessage());
				}
				close();
			} catch (SerializationException ex) {
				if (ERROR) {
					if (id != -1)
						error("Error updating connection: " + this, ex);
					else
						error("Error updating connection.", ex);
				}
				close();
				throw ex;
			}
		}
		if (TRACE) trace("Client thread stopped.");

		// If the connection was closed and the update thread shutdown, select one last time to complete closing the socket.
		try {
			selector.selectNow();
		} catch (IOException ignored) {
		}
	}

	public void start (boolean isDaemon) {
		Thread thread = new Thread(this, "Client");
		thread.setDaemon(isDaemon);
		thread.start();
	}

	public void stop () {
		if (shutdown) return;
		close();
		if (TRACE) trace("Client thread stopping.");
		shutdown = true;
		selector.wakeup();
	}

	public void close () {
		super.close();
		udpRegistered = false;
	}

	public void addListener (Listener listener) {
		super.addListener(listener);
		if (TRACE) trace("Client listener added.");
	}

	public void removeListener (Listener listener) {
		super.removeListener(listener);
		if (TRACE) trace("Client listener removed.");
	}

	/**
	 * Returns true if connected to a {@link Server}. Note this client could become disconnected at any time.
	 */
	public boolean isConnected () {
		return id != -1;
	}

	public Thread getUpdateThread () {
		return updateThread;
	}

	/**
	 * Broadcasts a UDP message on the LAN to discover any running servers.
	 * @param udpPort The UDP port of the server.
	 * @param timeoutMillis The number of milliseconds to wait for a response.
	 * @return the first server found, or null if no server responded.
	 */
	public InetAddress discoverHost (int udpPort, int timeoutMillis) {
		// TODO - Change to discover multiple hosts.
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			try {
				short classID = kryo.getRegisteredClass(DiscoverHost.class).id;
				ByteBuffer dataBuffer = ByteBuffer.allocate(4);
				ShortSerializer.put(dataBuffer, classID, true);
				dataBuffer.flip();
				byte[] data = new byte[dataBuffer.limit()];
				dataBuffer.get(data);
				for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
					for (InetAddress address : Collections.list(iface.getInetAddresses())) {
						if (!address.isSiteLocalAddress()) continue;
						// Java 1.5 doesn't support getting the subnet mask, so try the two most common.
						byte[] ip = address.getAddress();
						ip[3] = -1; // 255.255.255.0
						socket.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(ip), udpPort));
						ip[2] = -1; // 255.255.0.0
						socket.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(ip), udpPort));
					}
				}
				if (DEBUG) debug("Broadcasted host discovery on port: " + udpPort);

				socket.setSoTimeout(timeoutMillis);
				DatagramPacket packet = new DatagramPacket(new byte[0], 0);
				try {
					socket.receive(packet);
				} catch (SocketTimeoutException ex) {
					if (INFO) debug("Host discovery timed out.");
					return null;
				}
				if (INFO) debug("Discovered server: " + packet.getAddress());
				return packet.getAddress();
			} finally {
				socket.close();
			}
		} catch (IOException ex) {
			if (ERROR) error("Host discovery failed.", ex);
			return null;
		}
	}
}
