
package com.esotericsoftware.kryonet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import com.esotericsoftware.kryonet.FrameworkMessage.DiscoverHost;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;
import com.esotericsoftware.kryonet.FrameworkMessage.Ping;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterTCP;
import com.esotericsoftware.kryonet.FrameworkMessage.RegisterUDP;

class KryoNet {
	static public Kryo newKryo () {
		Kryo kryo = new Kryo();
		FieldSerializer fieldSerializer = new FieldSerializer(kryo);
		kryo.register(RegisterTCP.class, fieldSerializer);
		kryo.register(RegisterUDP.class, fieldSerializer);
		kryo.register(KeepAlive.class, fieldSerializer);
		kryo.register(DiscoverHost.class, fieldSerializer);
		kryo.register(Ping.class, fieldSerializer);
		return kryo;
	}
}
