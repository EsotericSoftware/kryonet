
package com.esotericsoftware.kryonet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterTCP;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterUDP;

import java.nio.ByteBuffer;

public class KryoSerialization implements Serialization {
	private final Kryo kryo;
	private final ByteBufferInput input;
	private final ByteBufferOutput output;

	public KryoSerialization () {
		this(new Kryo());
		kryo.setReferences(false);
		kryo.setRegistrationRequired(true);
	}

	public KryoSerialization (Kryo kryo) {
		this.kryo = kryo;

		kryo.register(RegisterTCP.class);
		kryo.register(RegisterUDP.class);
		kryo.register(KeepAlive.class);
		kryo.register(DiscoverHost.class);
		kryo.register(Ping.class);

		input = new ByteBufferInput();
		output = new ByteBufferOutput();
	}

	public Kryo getKryo () {
		return kryo;
	}

	public synchronized void write (Connection connection, ByteBuffer buffer, Object object) {
		output.setBuffer(buffer);
		kryo.getContext().put("connection", connection);
		kryo.writeClassAndObject(output, object);
		output.flush();
	}

	public synchronized Object read (Connection connection, ByteBuffer buffer) {
		input.setBuffer(buffer);
		kryo.getContext().put("connection", connection);
		return kryo.readClassAndObject(input);
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
