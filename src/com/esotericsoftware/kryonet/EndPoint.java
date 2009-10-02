
package com.esotericsoftware.kryonet;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;

/**
 * Represents the local end point of a connection.
 * @author Nathan Sweet <misc@n4te.com>
 */
public interface EndPoint extends Runnable {
	/**
	 * Gets the Kryo instance that will be used to serialize and deserialize objects.
	 */
	public Kryo getKryo ();

	/**
	 * If the listener already exists, it is not added again.
	 */
	public void addListener (Listener listener);

	public void removeListener (Listener listener);

	/**
	 * Continually updates this end point until {@link #stop()} is called.
	 */
	public void run ();

	/**
	 * Starts a new thread that calls {@link #run()}.
	 */
	public void start ();

	/**
	 * Closes this end point and causes {@link #run()} to return.
	 */
	public void stop ();

	/**
	 * @see Client
	 * @see Server
	 */
	public void close ();

	/**
	 * @see Client#update(int)
	 * @see Server#update(int)
	 */
	public void update (int timeout) throws IOException;

	/**
	 * Returns the last thread that called {@link #update(int)} for this end point. This can be useful to detect when long running
	 * code will be run on the update thread.
	 */
	public Thread getUpdateThread ();
}
