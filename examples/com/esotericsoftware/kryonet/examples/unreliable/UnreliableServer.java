package com.esotericsoftware.kryonet.examples.unreliable;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class UnreliableServer {
	private final Server server = new Server() {
		@Override
		protected Connection newConnection() {
			return new UnreliableConnection("Server");
		}
	};

	private UnreliableServer() throws Exception {
		server.addListener(new Listener() {
			@Override
			public void received(Connection connection, Object object) {
				connection.sendUDP(object);
			}
		});

		server.start();	    
		server.bind(UnreliableConnection.serverTcpPort, UnreliableConnection.serverUdpPort);
	}

	public static void main(String[] args) {
		try {
			new UnreliableServer();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
