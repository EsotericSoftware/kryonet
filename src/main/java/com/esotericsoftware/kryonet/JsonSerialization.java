
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
