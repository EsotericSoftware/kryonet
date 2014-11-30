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

/** Marker interface to denote that a message is used by the Ninja framework and is generally invisible to the developer. Eg, these
 * messages are only logged at the {@link Log#LEVEL_TRACE} level.
 * @author Nathan Sweet <misc@n4te.com> */
public interface FrameworkMessage {
	static final FrameworkMessage.KeepAlive keepAlive = new KeepAlive();

	/** Internal message to give the client the server assigned connection ID. */
	static public class RegisterTCP implements FrameworkMessage {
		public int connectionID;
	}

	/** Internal message to give the server the client's UDP port. */
	static public class RegisterUDP implements FrameworkMessage {
		public int connectionID;
	}

	/** Internal message to keep connections alive. */
	static public class KeepAlive implements FrameworkMessage {
	}

	/** Internal message to discover running servers. */
	static public class DiscoverHost implements FrameworkMessage {
	}

	/** Internal message to determine round trip time. */
	static public class Ping implements FrameworkMessage {
		public int id;
		public boolean isReply;
	}
}
