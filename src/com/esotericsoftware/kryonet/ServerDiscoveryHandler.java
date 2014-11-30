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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;

public interface ServerDiscoveryHandler {

	/**
	 * This implementation of {@link ServerDiscoveryHandler} is responsible for
	 * providing the {@link Server} with it's default behavior.
	 */
	public static final ServerDiscoveryHandler DEFAULT = new ServerDiscoveryHandler() {
		private ByteBuffer emptyBuffer = ByteBuffer.allocate(0);

		@Override
		public boolean onDiscoverHost(UdpConnection udp,
				InetSocketAddress fromAddress, Serialization serialization)
				throws IOException {
			udp.datagramChannel.send(emptyBuffer, fromAddress);
			return true;
		}
	};

	/**
	 * Called when the {@link Server} receives a {@link DiscoverHost} packet.
	 * 
	 * @param udp
	 *            the {@link UdpConnection}
	 * @param fromAddress
	 *            {@link InetSocketAddress} the {@link DiscoverHost} came from
	 * @param serialization
	 *            the {@link Server}'s {@link Serialization} instance
	 * @return true if a response was sent to {@code fromAddress}, false
	 *         otherwise
	 * @throws IOException
	 *             from the use of
	 *             {@link DatagramChannel#send(ByteBuffer, java.net.SocketAddress)}
	 */
	public boolean onDiscoverHost(UdpConnection udp,
			InetSocketAddress fromAddress, Serialization serialization)
			throws IOException;

}
