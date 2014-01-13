package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.sun.xml.internal.ws.api.message.Packet;

import static com.esotericsoftware.minlog.Log.*;

public class DiscoverHostTest extends KryoNetTestCase {

	public void testBroadcast() throws IOException {
		// This server exists solely to reply to Client#discoverHost.
		// It wouldn't be needed if the real server was using UDP.
		final Server broadcastServer = new Server();
		startEndPoint(broadcastServer);
		broadcastServer.bind(0, udpPort);

		final Server server = new Server();
		startEndPoint(server);
		server.bind(54555);
		server.addListener(new Listener() {
			public void disconnected(Connection connection) {
				broadcastServer.stop();
				server.stop();
			}
		});

		// ----

		Client client = new Client();
		InetAddress host = client.discoverHost(udpPort, 2000);
		if (host == null) {
			stopEndPoints();
			fail("No servers found.");
			return;
		}

		startEndPoint(client);
		client.connect(2000, host, tcpPort);
		client.stop();

		waitForThreads();
	}

	public void testCustomBroadcast() throws IOException {

		ServerDiscoveryHandler serverDiscoveryHandler = new ServerDiscoveryHandler() {

			@Override
			public boolean onDiscoverHost(UdpConnection udp,
					InetSocketAddress fromAddress, Serialization serialization)
					throws IOException {

				DiscoveryResponsePacket packet = new DiscoveryResponsePacket();
				packet.id = 42;
				packet.gameName = "gameName";
				packet.playerName = "playerName";

				ByteBuffer buffer = ByteBuffer.allocate(256);
				serialization.write(null, buffer, packet);
				buffer.flip();

				udp.datagramChannel.send(buffer, fromAddress);

				return true;
			}

		};

		ClientDiscoveryHandler clientDiscoveryHandler = new ClientDiscoveryHandler() {

			private Input input = null;

			@Override
			public DatagramPacket onRequestNewDatagramPacket() {
				byte[] buffer = new byte[1024];
				input = new Input(buffer);
				return new DatagramPacket(buffer, buffer.length);
			}

			@Override
			public void onDiscoveredHost(DatagramPacket datagramPacket, Kryo kryo) {
				if (input != null) {
					DiscoveryResponsePacket packet;
					packet = (DiscoveryResponsePacket) kryo.readClassAndObject(input);
					info("test", "packet.id = " + packet.id);
					info("test", "packet.gameName = " + packet.gameName);
					info("test", "packet.playerName = " + packet.playerName);
					info("test", "datagramPacket.getAddress() = " + datagramPacket.getAddress());
					info("test", "datagramPacket.getPort() = " + datagramPacket.getPort());
					assertEquals(42, packet.id);
					assertEquals("gameName", packet.gameName);
					assertEquals("playerName", packet.playerName);
					assertEquals(udpPort, datagramPacket.getPort());
				}
			}

			@Override
			public void onFinally() {
				if (input != null) {
					input.close();
				}
			}

		};

		// This server exists solely to reply to Client#discoverHost.
		// It wouldn't be needed if the real server was using UDP.
		final Server broadcastServer = new Server();

		broadcastServer.getKryo().register(DiscoveryResponsePacket.class);
		broadcastServer.setDiscoveryHandler(serverDiscoveryHandler);

		startEndPoint(broadcastServer);
		broadcastServer.bind(0, udpPort);

		final Server server = new Server();
		startEndPoint(server);
		server.bind(54555);
		server.addListener(new Listener() {
			public void disconnected(Connection connection) {
				broadcastServer.stop();
				server.stop();
			}
		});

		// ----

		Client client = new Client();

		client.getKryo().register(DiscoveryResponsePacket.class);
		client.setDiscoveryHandler(clientDiscoveryHandler);

		InetAddress host = client.discoverHost(udpPort, 2000);
		if (host == null) {
			stopEndPoints();
			fail("No servers found.");
			return;
		}

		startEndPoint(client);
		client.connect(2000, host, tcpPort);
		client.stop();

		waitForThreads();
	}

	public static class DiscoveryResponsePacket {

		public DiscoveryResponsePacket() {
			//
		}

		public int id;
		public String gameName;
		public String playerName;
	}

}
