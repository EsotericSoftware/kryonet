
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.esotericsoftware.kryo.Context;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryo.serialize.IntSerializer;

/**
 * @author Nathan Sweet <misc@n4te.com>
 */
class TcpConnection {
	static private final int IPTOS_LOWDELAY = 0x10;

	SocketChannel socketChannel;
	int keepAliveTime = 59000;
	final ByteBuffer readBuffer, writeBuffer, tempWriteBuffer;
	private final Kryo kryo;
	private SelectionKey selectionKey;
	private final Object writeLock = new Object();
	private int currentObjectLength;
	private long lastCommunicationTime;

	public TcpConnection (Kryo kryo, int writeBufferSize, int objectBufferSize) {
		this.kryo = kryo;
		writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
		tempWriteBuffer = ByteBuffer.allocateDirect(objectBufferSize);
		readBuffer = ByteBuffer.allocateDirect(objectBufferSize);
		readBuffer.flip();
	}

	public SelectionKey accept (Selector selector, SocketChannel socketChannel) throws IOException {
		try {
			this.socketChannel = socketChannel;
			socketChannel.configureBlocking(false);
			socketChannel.socket().setTcpNoDelay(true);

			selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);

			if (DEBUG) {
				debug("kryonet", "Port " + socketChannel.socket().getLocalPort() + "/TCP connected to: "
					+ socketChannel.socket().getRemoteSocketAddress());
			}

			if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();

			return selectionKey;
		} catch (IOException ex) {
			close();
			throw ex;
		}
	}

	public void connect (Selector selector, SocketAddress remoteAddress, int timeout) throws IOException {
		close();
		writeBuffer.clear();
		readBuffer.clear();
		readBuffer.flip();
		try {
			SocketChannel socketChannel = selector.provider().openSocketChannel();
			Socket socket = socketChannel.socket();
			socket.setTcpNoDelay(true);
			socket.setTrafficClass(IPTOS_LOWDELAY);
			socket.connect(remoteAddress, timeout); // Connect using blocking mode for simplicity.
			socketChannel.configureBlocking(false);
			this.socketChannel = socketChannel;

			selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
			selectionKey.attach(this);

			if (DEBUG) {
				debug("kryonet", "Port " + socketChannel.socket().getLocalPort() + "/TCP connected to: "
					+ socketChannel.socket().getRemoteSocketAddress());
			}

			if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();
		} catch (IOException ex) {
			close();
			IOException ioEx = new IOException("Unable to connect to: " + remoteAddress);
			ioEx.initCause(ex);
			throw ioEx;
		}
	}

	public Object readObject (Connection connection) throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");

		if (currentObjectLength == 0) {
			// Read the length of the next object from the socket.
			if (!IntSerializer.canRead(readBuffer, true)) {
				readBuffer.compact();
				int bytesRead = socketChannel.read(readBuffer);
				readBuffer.flip();
				if (bytesRead == -1) throw new SocketException("Connection is closed.");
				if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();

				if (!IntSerializer.canRead(readBuffer, true)) return null;
			}
			currentObjectLength = IntSerializer.get(readBuffer, true);

			if (currentObjectLength <= 0) throw new SerializationException("Invalid object length: " + currentObjectLength);
			if (currentObjectLength > readBuffer.capacity())
				throw new SerializationException("Unable to read object larger than read buffer: " + currentObjectLength);
		}

		int length = currentObjectLength;
		if (readBuffer.remaining() < length) {
			// Read the bytes for the next object from the socket.
			readBuffer.compact();
			int bytesRead = socketChannel.read(readBuffer);
			readBuffer.flip();
			if (bytesRead == -1) throw new SocketException("Connection is closed.");
			if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();

			if (readBuffer.remaining() < length) return null;
		}
		currentObjectLength = 0;

		int startPosition = readBuffer.position();
		int oldLimit = readBuffer.limit();
		readBuffer.limit(startPosition + length);

		Context context = Kryo.getContext();
		context.put("connection", connection);
		context.setRemoteEntityID(connection.id);
		Object object = kryo.readClassAndObject(readBuffer);

		readBuffer.limit(oldLimit);
		if (readBuffer.position() - startPosition != length)
			throw new SerializationException("Incorrect number of bytes (" + (startPosition + length - readBuffer.position())
				+ " remaining) used to deserialize object: " + object);

		return object;
	}

	public void writeOperation () throws IOException {
		synchronized (writeLock) {
			writeBuffer.flip();
			if (writeToSocket(writeBuffer)) {
				// Write successful, clear OP_WRITE.
				selectionKey.interestOps(SelectionKey.OP_READ);
			}
			writeBuffer.compact();
		}
	}

	private boolean writeToSocket (ByteBuffer buffer) throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");
		while (buffer.hasRemaining())
			if (socketChannel.write(buffer) == 0) break;
		if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();
		return !buffer.hasRemaining();
	}

	/**
	 * This method is thread safe.
	 */
	public int send (Connection connection, Object object) throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");
		synchronized (writeLock) {
			tempWriteBuffer.clear();
			tempWriteBuffer.position(5); // Allow room for the data length.

			// Write data.
			Context context = Kryo.getContext();
			context.put("connection", connection);
			context.setRemoteEntityID(connection.id);
			try {
				kryo.writeClassAndObject(tempWriteBuffer, object);
			} catch (SerializationException ex) {
				throw new SerializationException("Unable to serialize object of type: " + object.getClass().getName(), ex);
			}
			tempWriteBuffer.flip();

			// Write data length.
			int dataLength = tempWriteBuffer.limit() - 5;
			int lengthLength = IntSerializer.length(dataLength, true);
			int start = 5 - lengthLength;
			tempWriteBuffer.position(start);
			IntSerializer.put(tempWriteBuffer, dataLength, true);
			tempWriteBuffer.position(start);

			try {
				if (writeBuffer.position() > 0) {
					// Other data is already queued, append this data to be written later.
					writeBuffer.put(tempWriteBuffer);
				} else if (!writeToSocket(tempWriteBuffer)) {
					// A partial write occurred, queue the remaining data to be written later.
					writeBuffer.put(tempWriteBuffer);
					// Set OP_WRITE to be notified when more writing can occur.
					selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				}
			} catch (BufferOverflowException ex) {
				throw new SerializationException(
					"Write buffer limit exceeded writing object of type: " + object.getClass().getName(), ex);
			}

			if (DEBUG || TRACE) {
				float percentage = writeBuffer.position() / (float)writeBuffer.capacity();
				if (DEBUG && percentage > 0.75f)
					debug("kryonet", connection + " TCP write buffer is approaching capacity: " + percentage + "%");
				else if (TRACE && percentage > 0.25f)
					trace("kryonet", connection + " TCP write buffer utilization: " + percentage + "%");
			}

			return tempWriteBuffer.limit();
		}
	}

	public void close () {
		try {
			if (socketChannel != null) {
				socketChannel.close();
				socketChannel = null;
				if (selectionKey != null) selectionKey.selector().wakeup();
			}
		} catch (IOException ex) {
			if (DEBUG) debug("kryonet", "Unable to close TCP connection.", ex);
		}
	}

	public boolean needsKeepAlive (long time) {
		return socketChannel != null && keepAliveTime > 0 && time - lastCommunicationTime > keepAliveTime;
	}
}
