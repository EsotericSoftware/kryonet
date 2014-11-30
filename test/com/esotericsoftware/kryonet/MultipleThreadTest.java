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

import com.esotericsoftware.kryo.Kryo;

public class MultipleThreadTest extends KryoNetTestCase {
	int receivedServer, receivedClient1, receivedClient2;

	public void testMultipleThreads () throws IOException {
		receivedServer = 0;

		final int messageCount = 10;
		final int threads = 5;
		final int sleepMillis = 50;
		final int clients = 3;

		final Server server = new Server(16384, 8192);
		server.getKryo().register(String[].class);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void received (Connection connection, Object object) {
				receivedServer++;
				if (receivedServer == messageCount * clients) stopEndPoints();
			}
		});

		// ----

		for (int i = 0; i < clients; i++) {
			Client client = new Client(16384, 8192);
			client.getKryo().register(String[].class);
			startEndPoint(client);
			client.addListener(new Listener() {
				int received;

				public void received (Connection connection, Object object) {
					if (object instanceof String) {
						received++;
						if (received == messageCount * threads) {
							for (int i = 0; i < messageCount; i++) {
								connection.sendTCP("message" + i);
								try {
									Thread.sleep(50);
								} catch (InterruptedException ignored) {
								}
							}
						}
					}
				}
			});
			client.connect(5000, host, tcpPort, udpPort);
		}

		for (int i = 0; i < threads; i++) {
			new Thread() {
				public void run () {
					Connection[] connections = server.getConnections();
					for (int i = 0; i < messageCount; i++) {
						for (int ii = 0, n = connections.length; ii < n; ii++)
							connections[ii].sendTCP("message" + i);
						try {
							Thread.sleep(sleepMillis);
						} catch (InterruptedException ignored) {
						}
					}
				}
			}.start();
		}

		waitForThreads(5000);

		assertEquals(messageCount * clients, receivedServer);
	}
}
