
package com.esotericsoftware.kryonet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import junit.framework.TestCase;

import com.esotericsoftware.minlog.Log;

abstract public class KryoNetTestCase extends TestCase {
	static public String host = "localhost";
	static public int tcpPort = 54555, udpPort = 54777;

	private ArrayList<Thread> threads = new ArrayList();
	ArrayList<EndPoint> endPoints = new ArrayList();
	private Timer timer;
	boolean fail;

	public KryoNetTestCase () {
		// Log.TRACE();
	//	Log.DEBUG();
	}

	protected void setUp () throws Exception {
		System.out.println("---- " + getClass().getSimpleName());
		timer = new Timer();
	}

	protected void tearDown () throws Exception {
		timer.cancel();
	}

	public void startEndPoint (EndPoint endPoint) {
		endPoints.add(endPoint);
		Thread thread = new Thread(endPoint, endPoint.getClass().getSimpleName());
		threads.add(thread);
		thread.start();
	}

	public void stopEndPoints () {
		stopEndPoints(0);
	}

	public void stopEndPoints (int stopAfterMillis) {
		timer.schedule(new TimerTask() {
			public void run () {
				for (EndPoint endPoint : endPoints)
					endPoint.stop();
				endPoints.clear();
			}
		}, stopAfterMillis);
	}

	public void waitForThreads (int stopAfterMillis) {
		if (stopAfterMillis > 10000) throw new IllegalArgumentException("stopAfterMillis must be < 10000");
		stopEndPoints(stopAfterMillis);
		waitForThreads();
	}

	public void waitForThreads () {
		fail = false;
		TimerTask failTask = new TimerTask() {
			public void run () {
				stopEndPoints();
				fail = true;
			}
		};
		timer.schedule(failTask, 11000);
		while (true) {
			for (Iterator iter = threads.iterator(); iter.hasNext();) {
				Thread thread = (Thread)iter.next();
				if (!thread.isAlive()) iter.remove();
			}
			if (threads.isEmpty()) break;
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
		}
		failTask.cancel();
		if (fail) fail("Test did not complete in a timely manner.");
		// Give sockets a chance to close before starting the next test.
		try {
			Thread.sleep(1000);
		} catch (InterruptedException ignored) {
		}
	}
}
