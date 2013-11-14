
package com.esotericsoftware.kryonet;

public class KryoNetException extends RuntimeException {
	public KryoNetException () {
		super();
	}

	public KryoNetException (String message, Throwable cause) {
		super(message, cause);
	}

	public KryoNetException (String message) {
		super(message);
	}

	public KryoNetException (Throwable cause) {
		super(cause);
	}
}
