
package com.esotericsoftware.kryonet.rmi;

/**
 * Provides access to various settings on a remote object.
 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
 * @author Nathan Sweet <misc@n4te.com>
 */
public interface RemoteObject {
	/**
	 * Sets the milliseconds to wait for a method to return value. Default is 3000.
	 */
	public void setResponseTimeout (int timeoutMillis);

	/**
	 * Sets the blocking behavior when invoking a method that has a return value.
	 * @param nonBlocking If false, the invoking thread will wait for a return value or timeout (default). If true, the invoking
	 *           thread will not wait for a response. The response can later be retrieved with {@link #waitForLastResponse()} or
	 *           {@link #waitForResponse(byte)}. The method will return immediately and the return value should be ignored.
	 * @param ignoreResponses If false, then the response for non-blocking method invocations can be retrieved with
	 *           {@link #waitForLastResponse()} or {@link #waitForResponse(byte)}. The responses will be stored until retrieved, so
	 *           each method call should have a matching retrieve. If true, then the response to non-blocking method invocations
	 *           will never be sent by the remote side of the connection and the response can never be retrieved.
	 */
	public void setNonBlocking (boolean nonBlocking, boolean ignoreResponses);

	/**
	 * Waits for the response to the last method invocation to be received or the response timeout to be reached. Must not be
	 * called from the connection's update thread.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
	 */
	public Object waitForLastResponse ();

	/**
	 * Gets the ID of response for the last method invocation.
	 */
	public byte getLastResponseID ();

	/**
	 * Waits for the specified method invocation response to be received or the response timeout to be reached. Must not be called
	 * from the connection's update thread.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
	 */
	public Object waitForResponse (byte responseID);

	/**
	 * Causes this RemoteObject to stop listening to the connection for method invocation response messages.
	 */
	public void close ();
}
