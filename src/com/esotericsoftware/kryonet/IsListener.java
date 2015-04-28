package com.esotericsoftware.kryonet;

/**
 * Created by Chris on 2015-04-28.
 */
public interface IsListener {
	void connected(Connection connection);

	void disconnected(Connection connection);

	void received(Connection connection, Object object);

	void idle(Connection connection);
}
