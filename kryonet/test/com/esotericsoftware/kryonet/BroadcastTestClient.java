
package com.esotericsoftware.kryonet;

import static com.esotericsoftware.minlog.Log.LEVEL_DEBUG;

import java.io.IOException;
import java.net.InetAddress;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.minlog.Log;

public class BroadcastTestClient {
	public static void main (String[] args) throws IOException {
		Log.set(LEVEL_DEBUG);

		Client client = new Client();

		InetAddress host = client.discoverHost(54777, 2000);
		if (host == null) {
			System.out.println("No servers found.");
			return;
		}

		new Thread(client).start();
		client.connect(2000, host, 54555, 54777);
	}
}
