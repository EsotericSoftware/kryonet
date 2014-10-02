package com.esotericsoftware.kryonet.util;

import java.util.concurrent.ConcurrentLinkedDeque;

import static com.esotericsoftware.minlog.Log.*;

public class ConnectionMetrics {

    private final static int HISTORY_MAXIMUM = 120;
    private long timestampLastUpdate;

    public class MetricsInformation {

        public long timestamp;
        public long countObjectsSent;
        public long countObjectsReceived;
        public long countBytesSent;
        public long countBytesReceived;

    }

    private ConcurrentLinkedDeque<MetricsInformation> history;
    private MetricsInformation current;

    public ConnectionMetrics() {
        history = new ConcurrentLinkedDeque<MetricsInformation>();
        for (int i = 0; i < HISTORY_MAXIMUM; i++) {
            history.push(new MetricsInformation());
        }

        current = new MetricsInformation();
        current.timestamp = System.currentTimeMillis();

        timestampLastUpdate = current.timestamp;
    }


    public ConcurrentLinkedDeque<MetricsInformation> getHistory() {
        return history;
    }


    public MetricsInformation getLastInformation() {
        if (history.size() > 0) {
            return history.getLast();
        } else {
            return current;
        }
    }

    public void clear() {

        history.clear();
        for (int i = 0; i < HISTORY_MAXIMUM; i++) {
            history.push(new MetricsInformation());
        }
        current.timestamp = System.currentTimeMillis();
        current.countBytesReceived = 0;
        current.countBytesSent = 0;
        current.countObjectsReceived = 0;
        current.countObjectsSent = 0;
        timestampLastUpdate = current.timestamp;

    }


    public void update() {
        long timeNow = System.currentTimeMillis();

        if (timeNow - timestampLastUpdate >= 1000) {

            history.removeFirst();

            MetricsInformation information = new MetricsInformation();
            information.timestamp = current.timestamp;
            information.countBytesSent = current.countBytesSent;
            information.countBytesReceived = current.countBytesReceived;
            information.countObjectsReceived = current.countObjectsReceived;
            information.countObjectsSent = current.countObjectsSent;

            history.addLast(information);

            if (DEBUG) {
                System.out.println("PACKETS(sent:" + current.countObjectsSent + "/recv:" + current.countObjectsReceived + ") " +
                        "BYTES(sent:" + current.countBytesSent + "/recv:" + current.countBytesReceived + ")");
            }

            current.timestamp = timeNow;
            current.countBytesReceived = 0;
            current.countBytesSent = 0;
            current.countObjectsReceived = 0;
            current.countObjectsSent = 0;
            timestampLastUpdate = timeNow;

        }
    }


    public void incrementObjectsSent(long count) {
        current.countObjectsSent += count;
    }

    public void incrementObjectsReceived(long count) {
        current.countObjectsReceived += count;
    }

    public void incrementBytesSent(long count) {
        current.countBytesSent += count;
    }

    public void incrementBytesReceived(long count) {
        current.countBytesReceived += count;
    }

}