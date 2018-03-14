package com.esotericsoftware.kryonet.rmi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoNetTestCase;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.io.IOException;

/**
 * Tests concurrent invocations of one remoteObject with complex method signatures.
 *
 * Even with repeat, this test is likely to be false positive.
 *
 * Confirmed tho problems:
 * with concurrent usage of СlassКesolver (ClassCastException on client side and BufferUnderflow on server side)
 * and with concurrent blocking invocations (TimeoutException while waiting for response) due to incorrect locking.
 */
public class RmiCachedMethodsTest extends KryoNetTestCase {
    static private int SERVER_TEST_OBJECT_ID = 13;
    static private int PARALLELISM = 10;
    static private int REPEAT_TIMES = 10;

    static private volatile boolean testFailed = false;


    public void testMultithreadedMethodInvocation() throws IOException {
        for (int i = 0; i < REPEAT_TIMES; i++) {
            doTestMultithreadedMethodInvocation();
        }
    }

    private void doTestMultithreadedMethodInvocation() throws IOException {
        Server server = new Server();
        register(server.getKryo());
        startEndPoint(server);
        server.bind(tcpPort, udpPort);

        final ObjectSpace serverObjectSpace = new ObjectSpace();
        final TestObject serverTestObject = new TestObjectImpl();
        serverObjectSpace.register(SERVER_TEST_OBJECT_ID, serverTestObject);

        server.addListener(new Listener() {
            public void connected (final Connection connection) {
                serverObjectSpace.addConnection(connection);
            }
        });

        // -----------

        Client client = new Client();
        register(client.getKryo());
        startEndPoint(client);

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                runMultithreadedTestOnObject(connection);
            }
        });

        client.connect(5000, host, tcpPort, udpPort);
        waitForThreads();
        if (testFailed) fail();
    }

    private void runMultithreadedTestOnObject(Connection connection) {
        TestObject testObject = ObjectSpace.getRemoteObject(connection, SERVER_TEST_OBJECT_ID, TestObject.class);
        Runnable test = new TestRunnable(testObject);

        Thread[] threads = new Thread[PARALLELISM];
        for (int i = 0; i < PARALLELISM; i++) threads[i] = new Thread(test);
        for (Thread thread : threads) thread.start();

        try {
            Thread.sleep(200);
            boolean finished = true;
            while(!finished) {
                finished = true;
                for (Thread thread : threads)
                    if (thread.isAlive())
                        finished = false;
            }
            stopEndPoints();
        } catch (InterruptedException ex) {}
    }

    static private class TestRunnable implements Runnable {
        final TestObject testObject;

        public TestRunnable(TestObject testObject) {
            this.testObject = testObject;
        }

        public void run() {
            try {
                int pi = 3;
                int ans = testObject.complexFunction(
                        1L, 1d, false, pi, 'a',
                        null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null);
                assertEquals(ans, pi);
            } catch (RuntimeException ex) {
                testFailed = true;
                throw ex;
            }
        }
    }

    static private void register(Kryo kryo) {
        kryo.register(long.class);
        kryo.register(double.class);
        kryo.register(boolean.class);
        kryo.register(int.class);
        kryo.register(char.class);
        kryo.register(Long.class);
        kryo.register(Double.class);
        kryo.register(Boolean.class);
        kryo.register(Integer.class);
        kryo.register(Character.class);
        kryo.register(String.class);
        kryo.register(Long[].class);
        kryo.register(Double[].class);
        kryo.register(Boolean[].class);
        kryo.register(Integer[].class);
        kryo.register(Character[].class);
        kryo.register(String[].class);
        kryo.register(long[].class);
        kryo.register(double[].class);
        kryo.register(boolean[].class);
        kryo.register(int[].class);
        kryo.register(char[].class);
        kryo.register(TestObject.class);
        ObjectSpace.registerClasses(kryo);
    }

    static private interface TestObject {
        public int complexFunction(long pl, double pd, boolean pb, int pi, char pc,
                                   Long l, Double d, Boolean b, Integer i, Character c, String s1,
                                   Long[] la, Double[] da, Boolean[] ba, Integer[] ia, Character[] ca, String[] sa,
                                   long[] pla, double[] pda, boolean[] pba, int[] pia, char[] pca);
    }

    static private class TestObjectImpl implements TestObject {

        public TestObjectImpl() {}

        public int complexFunction(long pl, double pd, boolean pb, int pi, char pc,
                                   Long l, Double d, Boolean b, Integer i, Character c, String s1,
                                   Long[] la, Double[] da, Boolean[] ba, Integer[] ia, Character[] ca, String[] sa,
                                   long[] pla, double[] pda, boolean[] pba, int[] pia, char[] pca) {
            return pi;
        }
    }

}
