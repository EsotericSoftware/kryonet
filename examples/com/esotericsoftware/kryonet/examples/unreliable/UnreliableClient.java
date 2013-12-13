package com.esotericsoftware.kryonet.examples.unreliable;

public class UnreliableClient {	
	private final UnreliableConnection client = new UnreliableConnection("Client");

	private UnreliableClient() throws Exception {
		client.start();
		client.connect(5000, UnreliableConnection.localhost, UnreliableConnection.serverTcpPort, UnreliableConnection.serverUdpPort);
		
		client.addListener(null);
		for (int i=0; i<100; i++) {
			String object = String.format("#%02d", i);
			client.sendUDP(object);
			Thread.sleep(10);
		}
	}
	public static void main(String[] args) {
		try {
			new UnreliableClient();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
