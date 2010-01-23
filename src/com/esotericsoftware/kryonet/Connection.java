
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import com.esotericsoftware.kryo.Context;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.TcpConnection.WriteBufferOverflowException;

/**
 * Represents a TCP and optionally a UDP connection between a {@link Client} and a {@link Server}. If either underlying connection
 * is closed or errors, both connections are closed.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Connection {
	int id = -1;
	private String name;
	EndPoint endPoint;
	TcpConnection tcp;
	UdpConnection udp;
	InetSocketAddress udpRemoteAddress;
	private Listener[] listeners = {};
	private Object listenerLock = new Object();
	private long lastPingTime;
	private int returnTripTime;

	protected Connection () {
	}

	void initialize (Kryo kryo, int writeBufferSize, int readBufferSize) {
		tcp = new TcpConnection(kryo, writeBufferSize, readBufferSize);
	}

	/**
	 * Returns the server assigned ID. Will be -1 if this connection is not connected.
	 */
	public int getID () {
		return id;
	}

	/**
	 * Sends the object over the network using TCP.
	 * @return The number of bytes sent.
	 * @see Kryo#register(Class, com.esotericsoftware.kryo.Serializer)
	 */
	public int sendTCP (Object object) {
		if (object == null) throw new IllegalArgumentException("object cannot be null.");
		try {
			int length = tcp.send(this, object);
			if (length == 0) {
				if (TRACE) trace("kryonet", this + " TCP had nothing to send.");
			} else if (DEBUG) {
				String objectString = object == null ? "null" : object.getClass().getSimpleName();
				if (!(object instanceof FrameworkMessage)) {
					debug("kryonet", this + " sent TCP: " + objectString + " (" + length + ")");
				} else if (TRACE) {
					trace("kryonet", this + " sent TCP: " + objectString + " (" + length + ")");
				}
			}
			return length;
		} catch (IOException ex) {
			if (DEBUG) {
				if (id != -1)
					debug("kryonet", "Unable to send TCP with connection: " + this, ex);
				else
					debug("kryonet", "Unable to send TCP.", ex);
			}
			return 0;
		} catch (WriteBufferOverflowException ex) {
			if (WARN) warn(ex.getMessage(), ex.getCause());
			close();
			return 0;
		} catch (SerializationException ex) {
			close();
			throw ex;
		}
	}

	/**
	 * Sends the object over the network using UDP.
	 * @return The number of bytes sent.
	 * @see Kryo#register(Class, com.esotericsoftware.kryo.Serializer)
	 * @throws IllegalStateException if this connection was not opened with both TCP and UDP.
	 */
	public int sendUDP (Object object) {
		if (object == null) throw new IllegalArgumentException("object cannot be null.");
		SocketAddress address = udpRemoteAddress;
		if (address == null && udp != null) address = udp.connectedAddress;
		if (address == null && id != -1) throw new IllegalStateException("Connection is not connected via UDP.");

		Context context = Kryo.getContext();
		context.put("connection", this);
		context.put("connectionID", id);
		try {
			if (address == null) throw new SocketException("Connection is closed.");

			int length = udp.send(this, object, address);
			if (length == 0) {
				if (TRACE) trace("kryonet", this + " UDP had nothing to send.");
			} else if (DEBUG) {
				if (length != -1) {
					String objectString = object == null ? "null" : object.getClass().getSimpleName();
					if (!(object instanceof FrameworkMessage)) {
						debug("kryonet", this + " sent UDP: " + objectString + " (" + length + ")");
					} else if (TRACE) {
						trace("kryonet", this + " sent UDP: " + objectString + " (" + length + ")");
					}
				} else
					debug("kryonet", this + " was unable to send, UDP socket buffer full.");
			}
			return length;
		} catch (IOException ex) {
			if (DEBUG) {
				if (id != -1)
					debug("kryonet", "Unable to send UDP with connection: " + this, ex);
				else
					debug("kryonet", "Unable to send UDP.", ex);
			}
			return 0;
		} catch (SerializationException ex) {
			if (ERROR) error("kryonet", "Error sending UDP with connection: " + this, ex);
			close();
			throw ex;
		}
	}

	public void close () {
		tcp.close();
		if (udp != null && udp.connectedAddress != null) udp.close();
		if (id != -1) {
			notifyDisconnected();
			if (INFO) info("kryonet", this + " disconnected.");
		}
		setID(-1);
	}

	/**
	 * Requests the connection to communicate with the remote computer to determine a new value for the
	 * {@link #getReturnTripTime() return trip time}. When the connection receives a {@link FrameworkMessage.Ping} object with
	 * {@link Ping#isReply isReply} set to true, the new return trip time is available.
	 */
	public void updateReturnTripTime () {
		Ping ping = new Ping();
		lastPingTime = ping.time = System.currentTimeMillis();
		sendTCP(ping);
	}

	/**
	 * Returns the last calculated return trip time, or -1 if {@link #updateReturnTripTime()} has never been called or the
	 * {@link FrameworkMessage.Ping} response has not yet been received.
	 */
	public int getReturnTripTime () {
		return returnTripTime;
	}

	/**
	 * An empty object will be sent if the TCP connection is inactive more than the specified milliseconds. Some network hardware
	 * will close TCP connections if they cease to transmit. Set to zero to disable. Defaults to 59000.
	 */
	public void setKeepAliveTCP (int keepAliveMillis) {
		tcp.keepAliveTime = keepAliveMillis;
	}

	/**
	 * An empty object will be sent if the UDP connection is inactive more than the specified milliseconds. Most network hardware
	 * will close UDP connections if they cease to transmit. Set to zero to disable. Defaults to 19000.
	 */
	public void setKeepAliveUDP (int keepAliveMillis) {
		if (udp == null) throw new IllegalStateException("Not connected via UDP.");
		udp.keepAliveTime = keepAliveMillis;
	}

	/**
	 * If the listener already exists, it is not added again.
	 */
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
		if (TRACE) trace("kryonet", "Connection listener added: " + listener.getClass().getName());
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
		if (TRACE) trace("kryonet", "Connection listener removed: " + listener.getClass().getName());
	}

	void notifyConnected () {
		if (INFO) {
			SocketChannel socketChannel = tcp.socketChannel;
			if (socketChannel != null) {
				Socket socket = tcp.socketChannel.socket();
				if (socket != null) {
					InetSocketAddress remoteSocketAddress = (InetSocketAddress)socket.getRemoteSocketAddress();
					if (remoteSocketAddress != null) info("kryonet", this + " connected: " + remoteSocketAddress.getAddress());
				}
			}
		}
		Listener[] listeners = this.listeners;
		for (int i = 0, n = listeners.length; i < n; i++)
			listeners[i].connected(this);
	}

	void notifyDisconnected () {
		Listener[] listeners = this.listeners;
		for (int i = 0, n = listeners.length; i < n; i++)
			listeners[i].disconnected(this);
	}

	void notifyReceived (Object object) {
		if (object instanceof Ping) {
			Ping ping = (Ping)object;
			if (ping.isReply) {
				if (ping.time == lastPingTime) {
					returnTripTime = (int)(System.currentTimeMillis() - ping.time);
					if (TRACE) trace("kryonet", this + " return trip time: " + returnTripTime);
				}
			} else {
				ping.isReply = true;
				sendTCP(ping);
			}
		}
		Listener[] listeners = this.listeners;
		for (int i = 0, n = listeners.length; i < n; i++)
			listeners[i].received(this, object);
	}

	/**
	 * Returns the local {@link Client} or {@link Server} to which this connection belongs.
	 */
	public EndPoint getEndPoint () {
		return endPoint;
	}

	/**
	 * Returns the IP address and port of the remote end of the TCP connection, or null if this connection is not connected.
	 */
	public InetSocketAddress getRemoteAddressTCP () {
		SocketChannel socketChannel = tcp.socketChannel;
		if (socketChannel != null) {
			Socket socket = tcp.socketChannel.socket();
			if (socket != null) {
				return (InetSocketAddress)socket.getRemoteSocketAddress();
			}
		}
		return null;
	}

	/**
	 * Returns the IP address and port of the remote end of the UDP connection, or null if this connection is not connected.
	 */
	public InetSocketAddress getRemoteAddressUDP () {
		InetSocketAddress connectedAddress = udp.connectedAddress;
		if (connectedAddress != null) return connectedAddress;
		return udpRemoteAddress;
	}

	/**
	 * Sets the friendly name of this connection. This is returned by {@link #toString()} and is useful for providing application
	 * specific identifying information in the logging. May be null for the default name of "Connection X", where X is the
	 * connection ID.
	 */
	public void setName (String name) {
		this.name = name;
	}

	public String toString () {
		if (name != null) return name;
		return "Connection " + id;
	}

	void setID (int id) {
		this.id = id;
		if (id != -1 && name == null) name = "Connection " + id;
	}
}
