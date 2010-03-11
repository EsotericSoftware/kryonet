
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
	final ByteBuffer readBuffer, writeBuffer;
	private final Kryo kryo;
	private final ByteBuffer writeLengthBuffer = ByteBuffer.allocateDirect(4);
	private SelectionKey selectionKey;
	private final Object writeLock = new Object();
	private int currentObjectLength;
	private long lastCommunicationTime;

	public TcpConnection (Kryo kryo, int writeBufferSize, int readBufferSize) {
		this.kryo = kryo;
		writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
		readBuffer = ByteBuffer.allocateDirect(readBufferSize);
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
			// If it was not a partial write, clear the OP_WRITE flag. Otherwise wait to be notified when more writing can occur.
			if (writeToSocket()) selectionKey.interestOps(SelectionKey.OP_READ);
		}
	}

	private boolean writeToSocket () throws IOException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");
		writeBuffer.flip();
		while (writeBuffer.hasRemaining())
			if (socketChannel.write(writeBuffer) == 0) break;
		boolean wasFullWrite = !writeBuffer.hasRemaining();
		writeBuffer.compact();

		if (keepAliveTime > 0) lastCommunicationTime = System.currentTimeMillis();

		return wasFullWrite;
	}

	/**
	 * This method is thread safe.
	 */
	public int send (Connection connection, Object object) throws IOException, WriteBufferOverflowException {
		SocketChannel socketChannel = this.socketChannel;
		if (socketChannel == null) throw new SocketException("Connection is closed.");
		synchronized (writeLock) {
			int start = writeBuffer.position();
			writeBuffer.position(start + 1); // Allow 1 byte for the data length (the ideal case).

			Context context = Kryo.getContext();
			context.put("connection", connection);
			context.setRemoteEntityID(connection.id);
			try {
				kryo.writeClassAndObject(writeBuffer, object);
			} catch (BufferOverflowException ex) {
				close();
				throw new WriteBufferOverflowException("Write buffer overflow, position/limit/capacity: " + writeBuffer.position()
					+ "/" + writeBuffer.limit() + "/" + writeBuffer.capacity(), ex);
			} catch (SerializationException ex) {
				writeBuffer.position(start);
				throw new SerializationException("Unable to serialize object of type: " + object.getClass().getName(), ex);
			}

			// Write data length to socket.
			int dataLength = writeBuffer.position() - start - 1;
			int lengthLength;
			if (dataLength <= 127) {
				// Ideally it fits in one byte.
				lengthLength = 1;
				writeBuffer.put(start, (byte)dataLength);
			} else {
				// Write it to a temporary buffer.
				writeLengthBuffer.clear();
				lengthLength = IntSerializer.put(writeLengthBuffer, dataLength, true);
				writeLengthBuffer.flip();
				// Put last byte in writeBuffer.
				int lastIndex = writeLengthBuffer.limit() - 1;
				writeBuffer.put(start, writeLengthBuffer.get(lastIndex));
				writeLengthBuffer.limit(lastIndex);
				// Write the rest to the socket.
				while (writeLengthBuffer.hasRemaining())
					if (socketChannel.write(writeLengthBuffer) == 0) break;
				if (writeLengthBuffer.hasRemaining()) {
					// If writing the length failed (rare), shift the data over.
					byte[] temp = context.getByteArray(dataLength + 1);
					writeBuffer.position(start);
					writeBuffer.get(temp);
					writeBuffer.position(start);
					writeBuffer.put(writeLengthBuffer);
					writeBuffer.put(temp);
				}
			}

			if (DEBUG || TRACE) {
				float percentage = writeBuffer.position() / (float)writeBuffer.capacity();
				if (TRACE) trace("kryonet", connection + " TCP write buffer utilization: " + percentage + "%");
				if (DEBUG && percentage > 0.75f)
					debug("kryonet", connection + " TCP write buffer is approaching capacity: " + percentage + "%");
			}

			// If it was a partial write, set the OP_WRITE flag to be notified when more writing can occur.
			if (!writeToSocket()) selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

			return lengthLength + dataLength;
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

	static class WriteBufferOverflowException extends Exception {
		public WriteBufferOverflowException (String message, Throwable cause) {
			super(message, cause);
		}
	}
}
