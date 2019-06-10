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

import com.esotericsoftware.minlog.Log;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class CheckRegisteredClassesTest extends KryoNetTestCase {

	public void testRegisteredClassesCheck () throws IOException, InterruptedException {
		test(null, null, true);
		test(null, BigInteger.class, false);
		test(BigInteger.class, BigInteger.class, true);
		test(BigInteger.class, BigDecimal.class, false);
	}

	private void test(Class serverClass, Class clientClass, boolean isEqual) throws IOException, InterruptedException {
		Log.info("test. serverClass=" + serverClass + " clientClass=" + clientClass);
		final Server server = new Server();
		if (serverClass != null)
			server.getKryo().register(serverClass);
		startEndPoint(server);
		server.bind(tcpPort);

		final Client client = new Client();
		if (clientClass != null)
			client.getKryo().register(clientClass);
		startEndPoint(client);
		client.addListener(new Listener() {
			public void connected (Connection connection) {
			}

			public void received (Connection connection, Object object) {
			}
		});
		client.connect(5000, host, tcpPort);
		Thread.sleep(5000);
		assertEquals(isEqual, client.isConnected);
		waitForThreads(5000);
	}
}
