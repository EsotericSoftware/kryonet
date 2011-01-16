
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import com.esotericsoftware.kryo.Context;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;

/**
 * @author Nathan Sweet <misc@n4te.com>
 */
class UdpConnection {
	InetSocketAddress connectedAddress;
	DatagramChannel datagramChannel;
	int keepAliveMillis = 19000;
	final ByteBuffer readBuffer, writeBuffer;
	private final Kryo kryo;
	private SelectionKey selectionKey;
	private final Object writeLock = new Object();
	private long lastCommunicationTime;

	public UdpConnection (Kryo kryo, int bufferSize) {
		this.kryo = kryo;
		readBuffer = ByteBuffer.allocateDirect(bufferSize);
		writeBuffer = ByteBuffer.allocateDirect(bufferSize);
	}

	public void bind (Selector selector, int localPort) throws IOException {
		close();
		try {
			datagramChannel = selector.provider().openDatagramChannel();
			datagramChannel.socket().bind(new InetSocketAddress(localPort));
			datagramChannel.configureBlocking(false);
			selectionKey = datagramChannel.register(selector, SelectionKey.OP_READ);

			lastCommunicationTime = System.currentTimeMillis();
		} catch (IOException ex) {
			close();
			throw ex;
		}
	}

	public void connect (Selector selector, InetSocketAddress remoteAddress) throws IOException {
		close();
		try {
			datagramChannel = selector.provider().openDatagramChannel();
			datagramChannel.socket().bind(null);
			datagramChannel.socket().connect(remoteAddress);
			datagramChannel.configureBlocking(false);

			selectionKey = datagramChannel.register(selector, SelectionKey.OP_READ);

			lastCommunicationTime = System.currentTimeMillis();

			connectedAddress = remoteAddress;
		} catch (IOException ex) {
			close();
			IOException ioEx = new IOException("Unable to connect to: " + remoteAddress);
			ioEx.initCause(ex);
			throw ioEx;
		}
	}

	public InetSocketAddress readFromAddress () throws IOException {
		DatagramChannel datagramChannel = this.datagramChannel;
		if (datagramChannel == null) throw new SocketException("Connection is closed.");
		lastCommunicationTime = System.currentTimeMillis();
		return (InetSocketAddress)datagramChannel.receive(readBuffer);
	}

	public Object readObject (Connection connection) {
		readBuffer.flip();
		try {
			Context context = Kryo.getContext();
			context.put("connection", connection);
			if (connection != null) context.setRemoteEntityID(connection.id);
			Object object = kryo.readClassAndObject(readBuffer);
			if (readBuffer.hasRemaining())
				throw new SerializationException("Incorrect number of bytes (" + readBuffer.remaining()
					+ " remaining) used to deserialize object: " + object);
			return object;
		} finally {
			readBuffer.clear();
		}
	}

	/**
	 * This method is thread safe.
	 */
	public int send (Connection connection, Object object, SocketAddress address) throws IOException {
		DatagramChannel datagramChannel = this.datagramChannel;
		if (datagramChannel == null) throw new SocketException("Connection is closed.");
		synchronized (writeLock) {
			try {
				Context context = Kryo.getContext();
				context.put("connection", connection);
				context.setRemoteEntityID(connection.id);
				kryo.writeClassAndObject(writeBuffer, object);
				writeBuffer.flip();
				int length = writeBuffer.limit();
				datagramChannel.send(writeBuffer, address);

				lastCommunicationTime = System.currentTimeMillis();

				boolean wasFullWrite = !writeBuffer.hasRemaining();
				return wasFullWrite ? length : -1;
			} finally {
				writeBuffer.clear();
			}
		}
	}

	public void close () {
		connectedAddress = null;
		try {
			if (datagramChannel != null) {
				datagramChannel.close();
				datagramChannel = null;
				if (selectionKey != null) selectionKey.selector().wakeup();
			}
		} catch (IOException ex) {
			if (DEBUG) debug("kryonet", "Unable to close UDP connection.", ex);
		}
	}

	public boolean needsKeepAlive (long time) {
		return connectedAddress != null && keepAliveMillis > 0 && time - lastCommunicationTime > keepAliveMillis;
	}
}
