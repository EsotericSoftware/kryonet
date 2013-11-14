
package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.Arrays;

public class UnregisteredClassTest extends KryoNetTestCase {
	public void testPingPong () throws IOException {
		final Data dataTCP = new Data();
		populateData(dataTCP, true);
		final Data dataUDP = new Data();
		populateData(dataUDP, false);

		final Server server = new Server(16384, 8192);
		server.getKryo().setRegistrationRequired(false);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				connection.sendTCP(dataTCP);
				connection.sendUDP(dataUDP); // Note UDP ping pong stops if a UDP packet is lost.
			}

			public void received (Connection connection, Object object) {
				if (object instanceof Data) {
					Data data = (Data)object;
					if (data.isTCP) {
						if (!data.equals(dataTCP)) throw new RuntimeException("Fail!");
						connection.sendTCP(data);
					} else {
						if (!data.equals(dataUDP)) throw new RuntimeException("Fail!");
						connection.sendUDP(data);
					}
				}
			}
		});

		// ----

		final Client client = new Client(16384, 8192);
		client.getKryo().setRegistrationRequired(false);
		startEndPoint(client);
		client.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				if (object instanceof Data) {
					Data data = (Data)object;
					if (data.isTCP) {
						if (!data.equals(dataTCP)) throw new RuntimeException("Fail!");
						connection.sendTCP(data);
					} else {
						if (!data.equals(dataUDP)) throw new RuntimeException("Fail!");
						connection.sendUDP(data);
					}
				}
			}
		});

		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads(5000);
	}

	private void populateData (Data data, boolean isTCP) {
		data.isTCP = isTCP;

		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < 3000; i++)
			buffer.append('a');
		data.string = buffer.toString();

		data.strings = new String[] {"abcdefghijklmnopqrstuvwxyz0123456789", "", null, "!@#$", "�����"};
		data.ints = new int[] {-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
		data.shorts = new short[] {-12345, 12345, -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE};
		data.floats = new float[] {0, -0, 1, -1, 123456, -123456, 0.1f, 0.2f, -0.3f, (float)Math.PI, Float.MAX_VALUE,
			Float.MIN_VALUE};
		data.doubles = new double[] {0, -0, 1, -1, 123456, -123456, 0.1d, 0.2d, -0.3d, Math.PI, Double.MAX_VALUE, Double.MIN_VALUE};
		data.longs = new long[] {0, -0, 1, -1, 123456, -123456, 99999999999l, -99999999999l, Long.MAX_VALUE, Long.MIN_VALUE};
		data.bytes = new byte[] {-123, 123, -1, 0, 1, Byte.MAX_VALUE, Byte.MIN_VALUE};
		data.chars = new char[] {32345, 12345, 0, 1, 63, Character.MAX_VALUE, Character.MIN_VALUE};
		data.booleans = new boolean[] {true, false};
		data.Ints = new Integer[] {-1234567, 1234567, -1, 0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
		data.Shorts = new Short[] {-12345, 12345, -1, 0, 1, Short.MAX_VALUE, Short.MIN_VALUE};
		data.Floats = new Float[] {0f, -0f, 1f, -1f, 123456f, -123456f, 0.1f, 0.2f, -0.3f, (float)Math.PI, Float.MAX_VALUE,
			Float.MIN_VALUE};
		data.Doubles = new Double[] {0d, -0d, 1d, -1d, 123456d, -123456d, 0.1d, 0.2d, -0.3d, Math.PI, Double.MAX_VALUE,
			Double.MIN_VALUE};
		data.Longs = new Long[] {0l, -0l, 1l, -1l, 123456l, -123456l, 99999999999l, -99999999999l, Long.MAX_VALUE, Long.MIN_VALUE};
		data.Bytes = new Byte[] {-123, 123, -1, 0, 1, Byte.MAX_VALUE, Byte.MIN_VALUE};
		data.Chars = new Character[] {32345, 12345, 0, 1, 63, Character.MAX_VALUE, Character.MIN_VALUE};
		data.Booleans = new Boolean[] {true, false};
	}

	static public class Data {
		public String string;
		public String[] strings;
		public int[] ints;
		public short[] shorts;
		public float[] floats;
		public double[] doubles;
		public long[] longs;
		public byte[] bytes;
		public char[] chars;
		public boolean[] booleans;
		public Integer[] Ints;
		public Short[] Shorts;
		public Float[] Floats;
		public Double[] Doubles;
		public Long[] Longs;
		public Byte[] Bytes;
		public Character[] Chars;
		public Boolean[] Booleans;
		public boolean isTCP;

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
			if (!Arrays.equals(strings, other.strings)) return false;
			return true;
		}

		public String toString () {
			return "Data";
		}
	}
}
