
package com.esotericsoftware.kryonet;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public interface Serialization {
	public Object read (Connection connection, ByteBuffer buffer);

	/** @param connection May be null. */
	public void write (Connection connection, ByteBuffer buffer, Object object);

	public int getLengthLength ();

	public void writeLength (ByteBuffer buffer, int length);

	public int readLength (ByteBuffer buffer);
}
