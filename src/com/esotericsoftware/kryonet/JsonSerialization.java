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

import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;

import com.esotericsoftware.jsonbeans.Json;
import com.esotericsoftware.jsonbeans.JsonException;
import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterTCP;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterUDP;

import static com.esotericsoftware.minlog.Log.*;

public class JsonSerialization implements Serialization {
	private final Json json = new Json();
	private final ByteBufferInputStream byteBufferInputStream = new ByteBufferInputStream();
	private final ByteBufferOutputStream byteBufferOutputStream = new ByteBufferOutputStream();
	private final OutputStreamWriter writer = new OutputStreamWriter(byteBufferOutputStream);
	private boolean logging = true, prettyPrint = true;
	private byte[] logBuffer = {};

	public JsonSerialization () {
		json.addClassTag("RegisterTCP", RegisterTCP.class);
		json.addClassTag("RegisterUDP", RegisterUDP.class);
		json.addClassTag("KeepAlive", KeepAlive.class);
		json.addClassTag("DiscoverHost", DiscoverHost.class);
		json.addClassTag("Ping", Ping.class);

		json.setWriter(writer);
	}

	public void setLogging (boolean logging, boolean prettyPrint) {
		this.logging = logging;
		this.prettyPrint = prettyPrint;
	}

	public void write (Connection connection, ByteBuffer buffer, Object object) {
		byteBufferOutputStream.setByteBuffer(buffer);
		int start = buffer.position();
		try {
			json.writeValue(object, Object.class, null);
			writer.flush();
		} catch (Exception ex) {
			throw new JsonException("Error writing object: " + object, ex);
		}
		if (INFO && logging) {
			int end = buffer.position();
			buffer.position(start);
			buffer.limit(end);
			int length = end - start;
			if (logBuffer.length < length) logBuffer = new byte[length];
			buffer.get(logBuffer, 0, length);
			buffer.position(end);
			buffer.limit(buffer.capacity());
			String message = new String(logBuffer, 0, length);
			if (prettyPrint) message = json.prettyPrint(message);
			info("Wrote: " + message);
		}
	}

	public Object read (Connection connection, ByteBuffer buffer) {
		byteBufferInputStream.setByteBuffer(buffer);
		return json.fromJson(Object.class, byteBufferInputStream);
	}

	public void writeLength (ByteBuffer buffer, int length) {
		buffer.putInt(length);
	}

	public int readLength (ByteBuffer buffer) {
		return buffer.getInt();
	}

	public int getLengthLength () {
		return 4;
	}
}
