![KryoNet](https://raw.github.com/wiki/EsotericSoftware/kryonet/images/logo.jpg)

Please use the [KryoNet discussion group](http://groups.google.com/group/kryonet-users) for support.

## Overview

KryoNet is a Java library that provides a clean and simple API for efficient TCP and UDP client/server network communication using NIO. KryoNet uses the [Kryo serialization library](http://code.google.com/p/kryo/) to automatically and efficiently transfer object graphs across the network.

KryoNet runs on both the desktop and on [Android](http://www.android.com/).

KryoNet is ideal for any client/server application. It is very efficient, so is especially good for games. KryoNet can also be useful for inter-process communication.

- [Running a server](#running-a-server)
- [Connecting a client](#connecting-a-client)
- [Registering classes](#registering-classes)
- [TCP and UDP](#tcp-and-udp)
- [Buffer sizes](#buffer-sizes)
- [Threading](#threading)
- [LAN server discovery](#lan-server-discovery)
- [Pluggable Serialization](#pluggable-serialization)
- [Logging](#logging)
- [Remote Method Invocation (RMI)](#remote-method-invocation)
- [KryoNet versus ?](#kryonet-versus-)
- [Further reading](#further-reading)

## Running a server

This code starts a server on TCP port 54555 and UDP port 54777:

```java
    Server server = new Server();
    server.start();
    server.bind(54555, 54777);
```

The `start` method starts a thread to handle incoming connections, reading/writing to the socket, and notifying listeners.

This code adds a listener to handle receiving objects:

```java
    server.addListener(new Listener() {
       public void received (Connection connection, Object object) {
          if (object instanceof SomeRequest) {
             SomeRequest request = (SomeRequest)object;
             System.out.println(request.text);
    
             SomeResponse response = new SomeResponse();
             response.text = "Thanks";
             connection.sendTCP(response);
          }
       }
    });
```
	 
Note the Listener class has other notification methods that can be overridden.

Typically a listener has a series of `instanceof` checks to decide what to do with the object received. In this example, it prints out a string and sends a response over TCP.

The SomeRequest and SomeResponse classes are defined like this:

```java
    public class SomeRequest {
       public String text;
    }
    public class SomeResponse {
       public String text;
    }
```

[Kryo](http://code.google.com/p/kryo/) automatically serializes the objects to and from bytes.


## Connecting a client

This code connects to a server running on TCP port 54555 and UDP port 54777:

```java
    Client client = new Client();
    client.start();
    client.connect(5000, "192.168.0.4", 54555, 54777);
    
    SomeRequest request = new SomeRequest();
    request.text = "Here is the request";
    client.sendTCP(request);
```

The `start` method starts a thread to handle the outgoing connection, reading/writing to the socket, and notifying listeners. Note that the thread must be started before `connect` is called, else the outgoing connection will fail.

In this example, the `connect` method blocks for a maximum of 5000 milliseconds. If it times out or connecting otherwise fails, an exception is thrown (handling not shown). After the connection is made, the example sends a "SomeRequest" object to the server over TCP.

This code adds a listener to print out the response:

```java
    client.addListener(new Listener() {
       public void received (Connection connection, Object object) {
          if (object instanceof SomeResponse) {
             SomeResponse response = (SomeResponse)object;
             System.out.println(response.text);
          }
       }
    });
```

## Registering classes

For the above examples to work, the classes that are going to be sent over the network must be registered with the following code:

```java
    Kryo kryo = server.getKryo();
    kryo.register(SomeRequest.class);
    kryo.register(SomeResponse.class);
    Kryo kryo = client.getKryo();
    kryo.register(SomeRequest.class);
    kryo.register(SomeResponse.class);
```

This must be done on both the client and server, before any network communication occurs. It is very important that the exact same classes are registered on both the client and server, and that they are registered in the exact same order. Because of this, typically the code that registers classes is placed in a method on a class available to both the client and server.

Please see the [Kryo serialization library](http://code.google.com/p/kryo/) for more information on how objects are serialized for network transfer. Kryo can serialize any object and supports data compression (eg, deflate compression).


## TCP and UDP

KryoNet always uses a TCP port. This allows the framework to easily perform reliable communication and have a stateful connection. KryoNet can optionally use a UDP port in addition to the TCP port. While both ports can be used simultaneously, it is not recommended to send an huge amount of data on both at the same time because the two protocols can [affect each other](http://www.isoc.org/INET97/proceedings/F3/F3_1.HTM).

TCP is reliable, meaning objects sent are sure to arrive at their destination eventually. UDP is faster but unreliable, meaning an object sent may never be delivered. Because it is faster, UDP is typically used when many updates are being sent and it doesn't matter if an update is missed.

Note that KryoNet does not currently implement any extra features for UDP, such as reliability or flow control. It is left to the application to make proper use of the UDP connection.


## Buffer sizes

KryoNet uses a few buffers for serialization and deserialization that must be sized appropriately for a specific application. See the `Client` and `Server` constructors for customizing the buffer sizes. There are two types of buffers, a write buffer and an object buffer.

To receive an object graph, the bytes are stored in the object buffer until all of the bytes for the object are received, then the object is deserialized. The object buffer should be sized at least as large as the largest object that will be received.

To send an object graph, it is serialized to the write buffer where it is queued until it can be written to the network socket. Typically it is written immediately, but when sending a lot of data or when the network is slow, it may remain queued in the write buffer for a short time. The write buffer should be sized at least as large as the largest object that will be sent, plus some head room to allow for some serialized objects to be queued. The amount of head room needed is dependent upon the size of objects being sent and how often they are sent.

To avoid very large buffer sizes, object graphs can be split into smaller pieces and sent separately. Collecting the pieces and reassembling the larger object graph, or writing them to disk, etc is left to the application code. If a large number of small object graphs are queued to be written at once, it may exceed the write buffer size. `TcpIdleSender` and `InputStreamSender` can be used to queue more data only when the connection is idle. Also see the `setIdleThreshold` method on the Connection class.


## Threading

KryoNet imposes no restrictions on how threading is handled. The Server and Client classes have an update method that accepts connections and reads or writes any pending data for the current connections. The update method should be called periodically to process network events.

Both the Client and Server classes implement Runnable and the `run` method continually calls update until the `stop` method is called. Handing a client or server to a java.lang.Thread is a convenient way to have a dedicated update thread, and this is what the `start` method does. If this doesn't fit your needs, call `update` manually from the thread of your choice.

Listeners are notified from the update thread, so should not block for long. Static wrapper classes are provided on the Listener class to change how a listener is notified, such as ThreadedListener.

The update thread should never be blocked to wait for an incoming network message, as this will cause a deadlock.


## LAN server discovery

KryoNet can broadcast a UDP message on the LAN to discover any servers running:

```java
    InetAddress address = client.discoverHost(54777, 5000);
    System.out.println(address);
```

This will print the address of the first server found running on UDP port 54777. The call will block for up to 5000 milliseconds, waiting for a response.


## Logging

KryoNet makes use of the low overhead, lightweight [MinLog logging library](http://code.google.com/p/minlog/). The logging level can be set in this way:

```java
    Log.set(LEVEL_TRACE);
```

KryoNet does minimal logging at INFO and above levels. DEBUG is good to use during development and indicates the total number of bytes for each object sent. TRACE is good to use when debugging a specific problem, but outputs too much information to leave on all the time.

MinLog supports a fixed logging level, which will remove logging statements below that level. For efficiency, KryoNet can be compiled with a fixed logging level MinLog JAR. See [MinLog](http://code.google.com/p/minlog/) for more information.


## Pluggable Serialization

Serialization can be customized by providing a Serialization instance to the Client and Server constructors. By default KryoNet uses [Kryo](http://code.google.com/p/kryo/) for serialization. Kryo uses a binary format and is very efficient, highly configurable, and does automatic serialization for most object graphs.

JsonSerialization is provided which uses [JsonBeans](http://code.google.com/p/jsonbeans/) to do serialization using JSON. JSON is human readable so is convenient for use during development to monitor the data being sent and received.


## Remote Method Invocation

KryoNet has an easy to use mechanism for invoking methods on remote objects (RMI). This has a small amount of overhead versus explicitly sending objects. RMI can hide that methods are being marshaled and executed remotely, but in practice the code using such methods will need to be aware of the network communication to handle errors and methods that block. KryoNet's RMI is not related to the java.rmi package.

RMI is done by first creating an ObjectSpace and registering objects with an ID:

```java
    ObjectSpace objectSpace = new ObjectSpace();
    objectSpace.register(42, someObject);
    // ...
    objectSpace.addConnection(connection);
```

Multiple ObjectSpaces can be created for both the client or server side. Once registered, objects can be used on the other side of the registered connections:

```java
    SomeObject someObject = ObjectSpace.getRemoteObject(connection, 42, SomeObject.class);
    SomeResult result = someObject.doSomething();
```

The `getRemoteObject` method returns a proxy object that represents the specified class. When a method on the class is called, a message is sent over the connection and on the remote side the method is invoked on the registered object. The method blocks until the return value is sent back over the connection.

Exactly how the remote method invocation is performed can be customized by casting the proxy object to a RemoteObject.

```java
    SomeObject someObject = ObjectSpace.getRemoteObject(connection, 42, SomeObject.class);
    ((RemoteObject)someObject).setNonBlocking(true, true);
    someObject.doSomething();
```

Note that the SomeObject class does not need to implement RemoteObject, this is handled automatically.

The first `true` passed to `setNonBlocking` causes remote method invocations to be non-blocking. When `doSomething` is invoked, it will not block and wait for the return value. Instead the method will just return null.

The second `true` passed to `setNonBlocking` indicates that the return value of remote method invocations are to be ignored. This means the server will not waste time or bandwidth sending the result of the remote method invocation.

If the second parameter for `setNonBlocking` is false, the server will send back the remote method invocation return value. There are two ways to access a return value for a non-blocking method invocation:

```java
    RemoteObject remoteObject = (RemoteObject)someObject;
    remoteObject.setNonBlocking(true, false);
    someObject.doSomething();
    // ...
    SomeResult result = remoteObject.waitForLastResponse();

    RemoteObject remoteObject = (RemoteObject)someObject;
    remoteObject.setNonBlocking(true, false);
    someObject.doSomething();
    byte responseID = remoteObject.getLastResponseID();
    // ...
    SomeResult result = remoteObject.waitForResponse(responseID);
```

## KryoNet versus ?

KryoNet makes the assumptions that it will only be used for client/server architectures and that KryoNet will be used on both sides of the network. Because KryoNet solves a specific problem, the KryoNet API can do so very elegantly.

The [Apache MINA](http://mina.apache.org/) project is similar to KryoNet. MINA's API is lower level and a great deal more complicated. Even the simplest client/server will require a lot more code to be written. MINA also is not integrated with a robust serialization framework and doesn't intrinsically support RMI.

The [Priobit](http://code.google.com/p/priobit/) project is a minimal layer over NIO. It provides TCP networking similar to KryoNet, but without the higher level features. Priobit requires all network communication to occur on a single thread.

The [Java Game Networking](http://code.google.com/p/jgn/) project is a higher level library similar to KryoNet. JGN does not have as simple of an API.


## Further reading

Beyond this documentation page, you may find the following links useful:

- [Kryo](http://code.google.com/p/kryo/) (used to do the serialization in KryoNet)
- [Example code](http://code.google.com/p/kryonet/source/browse/#svn/trunk/kryonet/examples/com/esotericsoftware/kryonet/examples)
- [Unit tests](http://code.google.com/p/kryonet/source/browse/#svn/trunk/kryonet/test/com/esotericsoftware/kryonet)
