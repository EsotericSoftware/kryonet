
package com.esotericsoftware.kryonet.examples.chatrmi;

// This class represents a player on the server.
public interface IPlayer {
	public void registerName (String name);

	public void sendMessage (String message);
}
