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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class UnregisteredClassTest extends KryoNetTestCase {
	public void testUnregisteredClasses () throws IOException {
		final Data dataTCP = new Data();
		populateData(dataTCP, true);
		final Data dataUDP = new Data();
		populateData(dataUDP, false);

		final AtomicInteger receivedTCP = new AtomicInteger();
		final AtomicInteger receivedUDP = new AtomicInteger();

		final Server server = new Server(1024 * 32, 1024 * 16);
		server.getKryo().setRegistrationRequired(false);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			@Override
			public void connected (Connection connection) {
				connection.sendTCP(dataTCP);
				connection.sendUDP(dataUDP);
			}

			@Override
			public void received (Connection connection, Object object) {
				if (object instanceof Data) {
					Data data = (Data)object;
					if (data.isTCP) {
						if (!data.equals(dataTCP)) fail();
						receivedTCP.incrementAndGet();
					} else {
						if (!data.equals(dataUDP)) fail();
						receivedUDP.incrementAndGet();
					}
				}
			}
		});

		// ----

		final Client client = new Client(1024 * 32, 1024 * 16);
		client.getKryo().setRegistrationRequired(false);
		startEndPoint(client);
		client.addListener(new Listener() {
			@Override
			public void received (Connection connection, Object object) {
				if (object instanceof Data) {
					Data data = (Data)object;
					if (data.isTCP) {
						if (!data.equals(dataTCP)) fail();
						receivedTCP.incrementAndGet();
						connection.sendTCP(data);
					} else {
						if (!data.equals(dataUDP)) fail();
						receivedUDP.incrementAndGet();
						connection.sendUDP(data);
					}
				}
			}
		});

		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads(5000);

		assertEquals(2, receivedTCP.intValue());
		assertEquals(2, receivedUDP.intValue());
	}

	private void populateData (Data data, boolean isTCP) {
		data.isTCP = isTCP;

		data.string = "a".repeat(3000);

		data.strings = new String[] {"abcdefghijklmnopqrstuvwxyz0123456789", "", null, "!@#$", "�����"};
		data.ints = new int[] {-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
		data.shorts = new short[] {-12345, 12345, -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE};
		data.floats = new float[] {0, -0, 1, -1, 123456, -123456, 0.1f, 0.2f, -0.3f, (float)Math.PI, Float.MAX_VALUE,
			Float.MIN_VALUE};
		data.doubles = new double[] {0, -0, 1, -1, 123456, -123456, 0.1d, 0.2d, -0.3d, Math.PI, Double.MAX_VALUE, Double.MIN_VALUE};
		data.longs = new long[] {0, -0, 1, -1, 123456, -123456, 99999999999L, -99999999999L, Long.MAX_VALUE, Long.MIN_VALUE};
		data.bytes = new byte[] {-123, 123, -1, 0, 1, Byte.MAX_VALUE, Byte.MIN_VALUE};
		data.chars = new char[] {32345, 12345, 0, 1, 63, Character.MAX_VALUE, Character.MIN_VALUE};
		data.booleans = new boolean[] {true, false};
		data.Ints = new Integer[] {-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
		data.Shorts = new Short[] {-12345, 12345, -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE};
		data.Floats = new Float[] {0f, -0f, 1f, -1f, 123456f, -123456f, 0.1f, 0.2f, -0.3f, (float)Math.PI, Float.MAX_VALUE,
			Float.MIN_VALUE};
		data.Doubles = new Double[] {0d, -0d, 1d, -1d, 123456d, -123456d, 0.1d, 0.2d, -0.3d, Math.PI, Double.MAX_VALUE,
			Double.MIN_VALUE};
		data.Longs = new Long[] {0L, -0L, 1L, -1L, 123456L, -123456L, 99999999999L, -99999999999L, Long.MAX_VALUE, Long.MIN_VALUE};
		data.Bytes = new Byte[] {-123, 123, -1, 0, 1, Byte.MAX_VALUE, Byte.MIN_VALUE};
		data.Chars = new Character[] {32345, 12345, 0, 1, 63, Character.MAX_VALUE, Character.MIN_VALUE};
		data.Booleans = new Boolean[] {true, false};
	}

	static public class Data {
		public String string;
		String[] strings;
		int[] ints;
		short[] shorts;
		float[] floats;
		double[] doubles;
		long[] longs;
		byte[] bytes;
		char[] chars;
		boolean[] booleans;
		Integer[] Ints;
		Short[] Shorts;
		Float[] Floats;
		Double[] Doubles;
		Long[] Longs;
		Byte[] Bytes;
		Character[] Chars;
		Boolean[] Booleans;
		boolean isTCP;

		@Override
		public int hashCode () {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(Booleans);
			result = prime * result + Arrays.hashCode(Bytes);
			result = prime * result + Arrays.hashCode(Chars);
			result = prime * result + Arrays.hashCode(Doubles);
			result = prime * result + Arrays.hashCode(Floats);
			result = prime * result + Arrays.hashCode(Ints);
			result = prime * result + Arrays.hashCode(Longs);
			result = prime * result + Arrays.hashCode(Shorts);
			result = prime * result + Arrays.hashCode(booleans);
			result = prime * result + Arrays.hashCode(bytes);
			result = prime * result + Arrays.hashCode(chars);
			result = prime * result + Arrays.hashCode(doubles);
			result = prime * result + Arrays.hashCode(floats);
			result = prime * result + Arrays.hashCode(ints);
			result = prime * result + (isTCP ? 1231 : 1237);
			result = prime * result + Arrays.hashCode(longs);
			result = prime * result + Arrays.hashCode(shorts);
			result = prime * result + ((string == null) ? 0 : string.hashCode());
			result = prime * result + Arrays.hashCode(strings);
			return result;
		}

		@Override
		public boolean equals (Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			Data other = (Data)obj;
			if (!Arrays.equals(Booleans, other.Booleans)) return false;
			if (!Arrays.equals(Bytes, other.Bytes)) return false;
			if (!Arrays.equals(Chars, other.Chars)) return false;
			if (!Arrays.equals(Doubles, other.Doubles)) return false;
			if (!Arrays.equals(Floats, other.Floats)) return false;
			if (!Arrays.equals(Ints, other.Ints)) return false;
			if (!Arrays.equals(Longs, other.Longs)) return false;
			if (!Arrays.equals(Shorts, other.Shorts)) return false;
			if (!Arrays.equals(booleans, other.booleans)) return false;
			if (!Arrays.equals(bytes, other.bytes)) return false;
			if (!Arrays.equals(chars, other.chars)) return false;
			if (!Arrays.equals(doubles, other.doubles)) return false;
			if (!Arrays.equals(floats, other.floats)) return false;
			if (!Arrays.equals(ints, other.ints)) return false;
			if (isTCP != other.isTCP) return false;
			if (!Arrays.equals(longs, other.longs)) return false;
			if (!Arrays.equals(shorts, other.shorts)) return false;
			if (string == null) {
				if (other.string != null) return false;
			} else if (!string.equals(other.string)) return false;
			return Arrays.equals(strings, other.strings);
		}

		@Override
		public String toString () {
			return "Data";
		}
	}
}
