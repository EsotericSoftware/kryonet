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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryonet.util.InputStreamSender;

public class InputStreamSenderTest extends KryoNetTestCase {
	boolean success;

	public void testStream () throws IOException {
		final int largeDataSize = 12345;

		final Server server = new Server(16384, 8192);
		server.getKryo().setRegistrationRequired(false);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			public void connected (Connection connection) {
				ByteArrayOutputStream output = new ByteArrayOutputStream(largeDataSize);
				for (int i = 0; i < largeDataSize; i++)
					output.write(i);
				ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
				// Send data in 512 byte chunks.
				connection.addListener(new InputStreamSender(input, 512) {
					protected void start () {
						// Normally would send an object so the receiving side knows how to handle the chunks we are about to send.
						System.out.println("starting");
					}

					protected Object next (byte[] bytes) {
						System.out.println("sending " + bytes.length);
						return bytes; // Normally would wrap the byte[] with an object so the receiving side knows how to handle it.
					}
				});
			}
		});

		// ----

		final Client client = new Client(16384, 8192);
		client.getKryo().setRegistrationRequired(false);
		startEndPoint(client);
		client.addListener(new Listener() {
			int total;

			public void received (Connection connection, Object object) {
				if (object instanceof byte[]) {
					int length = ((byte[])object).length;
					System.out.println("received " + length);
					total += length;
					if (total == largeDataSize) {
						success = true;
						stopEndPoints();
					}
				}
			}
		});

		client.connect(5000, host, tcpPort, udpPort);

		waitForThreads(5000);
		if (!success) fail();
	}
}
