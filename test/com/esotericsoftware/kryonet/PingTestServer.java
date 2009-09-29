
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

public class PingTestServer {
	public static void main (String[] args) throws Exception {
		Log.set(LEVEL_DEBUG);

		final Server server = new Server();
		server.start(false);
		server.bind(54555);
	}
}
