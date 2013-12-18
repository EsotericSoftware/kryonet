
package com.esotericsoftware.kryonet.rmi;

import com.esotericsoftware.kryonet.Connection;

/** Provides access to various settings on a remote object.
 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
 * @author Nathan Sweet <misc@n4te.com> */
public interface RemoteObject {
	/** Sets the milliseconds to wait for a method to return value. Default is 3000. */
	public void setResponseTimeout (int timeoutMillis);

	/** Waits for the response to the last method invocation to be received or the response timeout to be reached. Must not be
	 * called from the connection's update thread.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...) */
	public Object waitForLastResponse ();

	/** Gets the ID of response for the last method invocation. */
	public int getLastResponseID ();

	/** Waits for the specified method invocation response to be received or the response timeout to be reached. Must not be called
	 * from the connection's update thread. Response IDs use a six bit identifier, with one identifier reserved for "no response".
	 * This means that this method should be called to get the result for a non-blocking call before an additional 63 non-blocking
	 * calls are made, or risk undefined behavior due to identical IDs.
	 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...) */
	public Object waitForResponse (int responseID);

	/** Causes this RemoteObject to stop listening to the connection for method invocation response messages. */
	public void close ();

	/** Returns the local connection for this remote object. */
	public Connection getConnection ();
}
