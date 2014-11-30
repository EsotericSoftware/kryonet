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

package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import static com.esotericsoftware.minlog.Log.*;

/** @author Nathan Sweet <misc@n4te.com> */
class UdpConnection {
	InetSocketAddress connectedAddress;
	DatagramChannel datagramChannel;
	int keepAliveMillis = 19000;
	final ByteBuffer readBuffer, writeBuffer;
	private final Serialization serialization;
	private SelectionKey selectionKey;
	private final Object writeLock = new Object();
	private long lastCommunicationTime;

	public UdpConnection (Serialization serialization, int bufferSize) {
		this.serialization = serialization;
		readBuffer = ByteBuffer.allocate(bufferSize);
		writeBuffer = ByteBuffer.allocateDirect(bufferSize);
	}

	public void bind (Selector selector, InetSocketAddress localPort) throws IOException {
		close();
		readBuffer.clear();
		writeBuffer.clear();
		try {
			datagramChannel = selector.provider().openDatagramChannel();
			datagramChannel.socket().bind(localPort);
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
		readBuffer.clear();
		writeBuffer.clear();
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
			try {
				Object object = serialization.read(connection, readBuffer);
				if (readBuffer.hasRemaining())
					throw new KryoNetException("Incorrect number of bytes (" + readBuffer.remaining()
						+ " remaining) used to deserialize object: " + object);
				return object;
			} catch (Exception ex) {
				throw new KryoNetException("Error during deserialization.", ex);
			}
		} finally {
			readBuffer.clear();
		}
	}

	/** This method is thread safe. */
	public int send (Connection connection, Object object, SocketAddress address) throws IOException {
		DatagramChannel datagramChannel = this.datagramChannel;
		if (datagramChannel == null) throw new SocketException("Connection is closed.");
		synchronized (writeLock) {
			try {
				try {
					serialization.write(connection, writeBuffer, object);
				} catch (Exception ex) {
					throw new KryoNetException("Error serializing object of type: " + object.getClass().getName(), ex);
				}
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
