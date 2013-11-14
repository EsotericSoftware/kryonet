
package com.esotericsoftware.kryonet.rmi;

/** Thrown when a method with a return value is invoked on a remote object and the response is not received with the
 * {@link RemoteObject#setResponseTimeout(int) response timeout}.
 * @see ObjectSpace#getRemoteObject(com.esotericsoftware.kryonet.Connection, int, Class...)
 * @author Nathan Sweet <misc@n4te.com> */
public class TimeoutException extends RuntimeException {
	public TimeoutException () {
		super();
	}

	public TimeoutException (String message, Throwable cause) {
		super(message, cause);
	}

	public TimeoutException (String message) {
		super(message);
	}

	public TimeoutException (Throwable cause) {
		super(cause);
	}
}
