package com.esotericsoftware.kryonet.util;

import java.util.ArrayDeque;

public class BandwidthMonitor {

    private final static int HISTORY_MAXIMUM = 120;
    private long timestampLastUpdate;
    private boolean debug = false;

    public class BandwidthInformation {

        public long timestamp;
        public long countPacketsSent;
        public long countPacketsReceived;
        public long countBytesSent;
        public long countBytesReceived;

    }

    private ArrayDeque<BandwidthInformation> history;
    private BandwidthInformation current;

    public BandwidthMonitor() {
        history = new ArrayDeque<BandwidthInformation>();
        for (int i = 0; i < HISTORY_MAXIMUM; i++) {
            history.push(new BandwidthInformation());
        }

        current = new BandwidthInformation();
        current.timestamp = System.currentTimeMillis();

        timestampLastUpdate = current.timestamp;
    }


    public ArrayDeque<BandwidthInformation> getHistory() {
        return history;
    }


    public BandwidthInformation getLastInformation() {
        if (history.size() > 0) {
            return history.getLast();
        } else {
            return current;
        }
    }

    public void clear() {

        history.clear();
        for (int i = 0; i < HISTORY_MAXIMUM; i++) {
            history.push(new BandwidthInformation());
        }
        current.timestamp = System.currentTimeMillis();
        current.countBytesReceived = 0;
        current.countBytesSent = 0;
        current.countPacketsReceived = 0;
        current.countPacketsSent = 0;
        timestampLastUpdate = current.timestamp;

    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void update() {
        long timeNow = System.currentTimeMillis();

        if (timeNow - timestampLastUpdate >= 1000) {

            history.removeFirst();

            BandwidthInformation information = new BandwidthInformation();
            information.timestamp = current.timestamp;
            information.countBytesSent = current.countBytesSent;
            information.countBytesReceived = current.countBytesReceived;
            information.countPacketsReceived = current.countPacketsReceived;
            information.countPacketsSent = current.countPacketsSent;

            history.addLast(information);

            if (debug == true) {
                System.out.println("PACKETS(sent:" + current.countPacketsSent + "/recv:" + current.countPacketsReceived + ") " +
                        "BYTES(sent:" + current.countBytesSent + "/recv:" + current.countBytesReceived + ")");
            }

            current.timestamp = timeNow;
            current.countBytesReceived = 0;
            current.countBytesSent = 0;
            current.countPacketsReceived = 0;
            current.countPacketsSent = 0;
            timestampLastUpdate = timeNow;

        }
    }


    public void incrementPacketsSent(long count) {
        current.countPacketsSent += count;
    }

    public void incrementPacketsReceived(long count) {
        current.countPacketsReceived += count;
    }

    public void incrementBytesSent(long count) {
        current.countBytesSent += count;
    }

    public void incrementBytesReceived(long count) {
        current.countBytesReceived += count;
    }

}