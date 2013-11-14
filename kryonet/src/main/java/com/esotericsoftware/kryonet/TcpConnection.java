
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static com.esotericsoftware.minlog.Log.*;

/** @author Nathan Sweet <misc@n4te.com> */
class TcpConnection {
	static private final int IPTOS_LOWDELAY = 0x10;

	SocketChannel socketChannel;
	int keepAliveMillis = 8000;
	final ByteBuffer readBuffer, writeBuffer;
	boolean bufferPositionFix;
	int timeoutMillis = 12000;
	float idleThreshold = 0.1f;

	final Serialization serialization;
	private SelectionKey selectionKey;
	private long lastWriteTime, lastReadTime;
	private int currentObjectLength;
	private final Object writeLock = new Object();

	public TcpConnection (Serialization serialization, int writeBufferSize, int objectBufferSize) {
		this.serialization = serialization;
		writeBuffer = ByteBuffer.allocate(writeBufferSize);
		readBuffer = ByteBuffer.allocate(objectBufferSize);
		readBuffer.flip();
	}

	public SelectionKey accept (Selector selector, SocketChannel socketChannel) throws IOException {
		writeBuffer.clear();
		readBuffer.clear();
		readBuffer.flip();
		currentObjectLength = 0;
		try {
			this.socketChannel = socketChannel;
			socketChannel.configureBlocking(false);
			Socket socket = socketChannel.socket();
			socket.setTcpNoDelay(true);

			selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);

			if (DEBUG) {
				debug("kryonet", "Port " + socketChannel.socket().getLocalPort() + "/TCP connected to: "
					+ socketChannel.socket().getRemoteSocketAddress());
			}

			lastReadTime = lastWriteTime = System.currentTimeMillis();

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
		currentObjectLength = 0;
		try {
			SocketChannel socketChannel = selector.provider().openSocketChannel();
			Socket socket = socketChannel.socket();
			socket.setTcpNoDelay(true);
			// socket.setTrafficClass(IPTOS_LOWDELAY);
			socket.connect(remoteAddress, timeout); // Connect using blocking mode for simplicity.
			socketChannel.configureBlocking(false);
			this.socketChannel = socketChannel;

			selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
			selectionKey.attach(this);

			if (DEBUG) {
				debug("kryonet", "Port " + socketChannel.socket().getLocalPort() + "/TCP connected to: "
					+ socketChannel.socket().getRemoteSocketAddress());
			}

			lastReadTime = lastWriteTime = System.currentTimeMillis();
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
			int lengthLength = serialization.getLengthLength();
			if (readBuffer.remaining() < lengthLength) {
				readBuffer.compact();
				int bytesRead = socketChannel.read(readBuffer);
				readBuffer.flip();
				if (bytesRead == -1) throw new SocketException("Connection is closed.");
				lastReadTime = System.currentTimeMillis();

				if (readBuffer.remaining() < lengthLength) return null;
			}
			currentObjectLength = serialization.readLength(readBuffer);

			if (currentObjectLength <= 0) throw new KryoNetException("Invalid object length: " + currentObjectLength);
			if (currentObjectLength > readBuffer.capacity())
				throw new KryoNetException("Unable to read object larger than read buffer: " + currentObjectLength);
		}

		int length = currentObjectLength;
		if (readBuffer.remaining() < length) {
			// Fill the tcpInputStream.
			readBuffer.compact();
			int bytesRead = socketChannel.read(readBuffer);
			readBuffer.flip();
			if (bytesRead == -1) throw new SocketException("Connection is closed.");
			lastReadTime = System.currentTimeMillis();

			if (readBuffer.remaining() < length) return null;
		}
		currentObjectLength = 0;

		int startPosition = readBuffer.position();
		int oldLimit = readBuffer.limit();
		readBuffer.limit(startPosition + length);
		Object object;
		try {
			object = serialization.read(connection, readBuffer);
		} catch (Exception ex) {
			throw new KryoNetException("Error during deserialization.", ex);
		}

		readBuffer.limit(oldLimit);
		if (readBuffer.position() - startPosition != length)
			throw new KryoNetException("Incorrect number of bytes (" + (startPosition + length - readBuffer.position())
				+ " remaining) used to deserialize object: " + object);

		return object;
	}

	public void writeOperation () throws IOException {
		synchronized (writeLock) {
			if (writeToSocket()) {
				// Write successful, clear OP_WRITE.
				selectionKey.interestOps(SelectionKey.OP_READ);
			}
			lastWriteTime = System.currentTimeMillis();
		}
	}

	private boolean writeToSocket () throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");

		ByteBuffer buffer = writeBuffer;
		buffer.flip();
		while (buffer.hasRemaining()) {
			if (bufferPositionFix) {
				buffer.compact();
				buffer.flip();
			}
			if (socketChannel.write(buffer) == 0) break;
		}
		buffer.compact();

		return buffer.position() == 0;
	}

	/** This method is thread safe. */
	public int send (Connection connection, Object object) throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");
		synchronized (writeLock) {
			// Leave room for length.
			int start = writeBuffer.position();
			int lengthLength = serialization.getLengthLength();
			writeBuffer.position(writeBuffer.position() + lengthLength);

			// Write data.
			try {
				serialization.write(connection, writeBuffer, object);
			} catch (KryoNetException ex) {
				throw new KryoNetException("Error serializing object of type: " + object.getClass().getName(), ex);
			}
			int end = writeBuffer.position();

			// Write data length.
			writeBuffer.position(start);
			serialization.writeLength(writeBuffer, end - lengthLength - start);
			writeBuffer.position(end);

			// Write to socket if no data was queued.
			if (start == 0 && !writeToSocket()) {
				// A partial write, set OP_WRITE to be notified when more writing can occur.
				selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			} else {
				// Full write, wake up selector so idle event will be fired.
				selectionKey.selector().wakeup();
			}

			if (DEBUG || TRACE) {
				float percentage = writeBuffer.position() / (float)writeBuffer.capacity();
				if (DEBUG && percentage > 0.75f)
					debug("kryonet", connection + " TCP write buffer is approaching capacity: " + percentage + "%");
				else if (TRACE && percentage > 0.25f)
					trace("kryonet", connection + " TCP write buffer utilization: " + percentage + "%");
			}

			lastWriteTime = System.currentTimeMillis();
			return end - start;
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
		return socketChannel != null && keepAliveMillis > 0 && time - lastWriteTime > keepAliveMillis;
	}

	public boolean isTimedOut (long time) {
		return socketChannel != null && timeoutMillis > 0 && time - lastReadTime > timeoutMillis;
	}
}
