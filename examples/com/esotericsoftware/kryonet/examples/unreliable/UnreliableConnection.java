package com.esotericsoftware.kryonet.examples.unreliable;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

public class UnreliableConnection extends Client {
	static final int serverTcpPort = 54555;
	static final int serverUdpPort = 54777;
	static final String localhost = "127.0.0.1";

	static final int lagMillisMin = 100;
	static final int lagMillisMax = 250;
	static final float lossPercentage = 0.1f;
	static final float duplicationPercentage = 0.03f;

	private final String name;

	UnreliableConnection(final String name) {
		this.name = name;

	}

	@Override
	public void addListener(final Listener listener) {
		super.addListener(new Listener.UnreliableListener(
			UnreliableConnection.lagMillisMin, UnreliableConnection.lagMillisMax, 
			UnreliableConnection.lossPercentage, UnreliableConnection.duplicationPercentage,
			new Listener() {
				@Override
				public void received(Connection connection, Object object) {
					System.out.println("["+name+"]: recving "+object);
					if (listener != null) {
						listener.received(connection, object);
					}
				}
			}));
	}


	@Override
	public int sendUDP(Object object) {
		System.out.println("["+name+"]: sending "+object);
		return super.sendUDP(object);
	}



}
