package com.esotericsoftware.kryonet;



/**
 * Marker interface to denote that a message is used by the Ninja framework and is generally invisible to the developer. Eg, these
 * messages are only logged at the {@link Log#TRACE} level.
 */
public interface FrameworkMessage {
	static final FrameworkMessage.KeepAlive keepAlive = new KeepAlive();

	/**
	 * Internal message to give the client the server assigned connection ID.
	 */
	static public class RegisterTCP implements FrameworkMessage {
		public short connectionID;

		public String toString () {
			return "Ninja.RegisterTCP";
		}
	}

	/**
	 * Internal message to give the server the client's UDP port.
	 */
	static public class RegisterUDP implements FrameworkMessage {
		public short connectionID;

		public String toString () {
			return "Ninja.RegisterUDP";
		}
	}

	/**
	 * Internal message to keep connections alive.
	 */
	static public class KeepAlive implements FrameworkMessage {
		public String toString () {
			return "Ninja.KeepAlive";
		}
	}

	/**
	 * Internal message to discover running servers.
	 */
	static public class DiscoverHost implements FrameworkMessage {
		public String toString () {
			return "Ninja.DiscoverHost";
		}
	}

	/**
	 * Internal message to determine round trip time.
	 */
	static public class Ping implements FrameworkMessage {
		long time;
		public boolean isReply;

		public String toString () {
			return "Ninja.Ping";
		}
	}
}
