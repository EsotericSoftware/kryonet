
package com.esotericsoftware.kryonet.examples.chatrmi;

// This class represents the chat window on the client.
public interface IChatFrame {
	public void addMessage (String message);

	public void setNames (String[] names);
}
