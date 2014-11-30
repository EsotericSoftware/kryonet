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

package com.esotericsoftware.kryonet.rmi;

import com.esotericsoftware.kryonet.Connection;

/** Provides access to various settings on a remote object.
 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
 * @author Nathan Sweet <misc@n4te.com> */
public interface RemoteObject {
	/** Sets the milliseconds to wait for a method to return value. Default is 3000. */
	public void setResponseTimeout (int timeoutMillis);

	/** Sets the blocking behavior when invoking a remote method. Default is false.
	 * @param nonBlocking If false, the invoking thread will wait for the remote method to return or timeout (default). If true,
	 *           the invoking thread will not wait for a response. The method will return immediately and the return value should
	 *           be ignored. If they are being transmitted, the return value or any thrown exception can later be retrieved with
	 *           {@link #waitForLastResponse()} or {@link #waitForResponse(byte)}. The responses will be stored until retrieved, so
	 *           each method call should have a matching retrieve. */
	public void setNonBlocking (boolean nonBlocking);

	/** Sets whether return values are sent back when invoking a remote method. Default is true.
	 * @param transmit If true, then the return value for non-blocking method invocations can be retrieved with
	 *           {@link #waitForLastResponse()} or {@link #waitForResponse(byte)}. If false, then non-primitive return values for
	 *           remote method invocations are not sent by the remote side of the connection and the response can never be
	 *           retrieved. This can also be used to save bandwidth if you will not check the return value of a blocking remote
	 *           invocation. Note that an exception could still be returned by {@link #waitForLastResponse()} or
	 *           {@link #waitForResponse(byte)} if {@link #setTransmitExceptions(boolean)} is true. */
	public void setTransmitReturnValue (boolean transmit);

	/** Sets whether exceptions are sent back when invoking a remote method. Default is true.
	 * @param transmit If false, exceptions will be unhandled and rethrown as RuntimeExceptions inside the invoking thread. This is
	 *           the legacy behavior. If true, behavior is dependent on whether {@link #setNonBlocking(boolean)}. If non-blocking
	 *           is true, the exception will be serialized and sent back to the call site of the remotely invoked method, where it
	 *           will be re-thrown. If non-blocking is false, an exception will not be thrown in the calling thread but instead can
	 *           be retrieved with {@link #waitForLastResponse()} or {@link #waitForResponse(byte)}, similar to a return value. */
	public void setTransmitExceptions (boolean transmit);

	/** If true, UDP will be used to send the remote method invocation. UDP remote method invocations will never return a response
	 * and the invoking thread will not wait for a response. */
	public void setUDP (boolean udp);

	/** If false, calls to {@link Object#toString()} will return "<proxy>" instead of being invoking the remote method. Default is
	 * false. */
	public void setRemoteToString (boolean remoteToString);

	/** Waits for the response to the last method invocation to be received or the response timeout to be reached. Must not be
	 * called from the connection's update thread.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...) */
	public Object waitForLastResponse ();

	/** Gets the ID of response for the last method invocation. */
	public byte getLastResponseID ();

	/** Waits for the specified method invocation response to be received or the response timeout to be reached. Must not be called
	 * from the connection's update thread. Response IDs use a six bit identifier, with one identifier reserved for "no response".
	 * This means that this method should be called to get the result for a non-blocking call before an additional 63 non-blocking
	 * calls are made, or risk undefined behavior due to identical IDs.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...) */
	public Object waitForResponse (byte responseID);

	/** Causes this RemoteObject to stop listening to the connection for method invocation response messages. */
	public void close ();

	/** Returns the local connection for this remote object. */
	public Connection getConnection ();
}
