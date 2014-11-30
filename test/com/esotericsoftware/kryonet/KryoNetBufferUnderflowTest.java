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
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicBoolean;

public class KryoNetBufferUnderflowTest {
	public static void main (String[] args) throws IOException, InterruptedException {
		final int port = 7000;
		final int writeBufferSize = 16384;
		final int objectBufferSize = 2048;
		final AtomicBoolean received = new AtomicBoolean();

		// Creating server
		final Server server = new Server(writeBufferSize, objectBufferSize);
		server.bind(port);
		server.start();
		System.out.println("Server listening on port " + port);

		// Creating client
		final Client client = new Client(writeBufferSize, objectBufferSize);
		client.start();
		client.addListener(new Listener() {
			@Override
			public void received (Connection connection, Object object) {
				if (object instanceof String) {
					System.out.println("Received: " + object);
					received.set(true);
				} else
					System.err.println("Received unexpected object");
			}
		});
		client.connect(5000, "localhost", port);
		System.out.println("Client connected");

		// Catching exception
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException (Thread t, Throwable e) {
				e.printStackTrace();
				received.set(true);
				// Stopping it all
				System.out.println("Stopping client and server");
				client.stop();
				server.stop();
			}
		});

		// Sending small messages
		for (int i = 0; i < 5; i++) {
			String smallMessage = "RandomStringUtils.randomAlphanumeric(256)";
			System.out.println("Sending: " + smallMessage);
			received.set(false);
			server.sendToAllTCP(smallMessage);
			while (!received.get()) {
				Thread.sleep(100);
			}
		}

		// Sending large message
		String bigMessage = "RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)RandomStringUtils.randomAlphanumeric(532)";
		bigMessage = bigMessage + bigMessage + bigMessage + bigMessage + bigMessage + bigMessage + bigMessage;
		System.out.println("Sending: " + bigMessage);
		received.set(false);
		server.sendToAllTCP(bigMessage);
		while (!received.get()) {
			Thread.sleep(100);
		}
	}
}
