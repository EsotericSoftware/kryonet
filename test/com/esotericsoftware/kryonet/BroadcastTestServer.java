
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import java.io.IOException;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.minlog.Log;

public class BroadcastTestServer {
	public static void main (String[] args) throws IOException {
		Log.set(LEVEL_DEBUG);

		Server server = new Server();
		new Thread(server).start();
		server.bind(54555, 54777);
	}
}
