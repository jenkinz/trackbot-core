/*
 * Date: Mar 9, 2008
 * Time: 1:22:14 PM
 * (c) 2008 Shawn Silverman
 */
package com.qindesign.util;

/**
 * A latch that can be used for thread signalling.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class Latch {
    private boolean state;

    /**
     * Creates a new latch.  The latch will not be set.
     */
    public Latch() {
        super();
    }

    /**
     * Sets the latch.  If the latch is not already set, then any threads
     * waiting for the latch to be set will be notified.  Once the latch is
     * set, it stays set until {@link #resetLatch()} is called.
     */
    public synchronized void setLatch() {
        if (!state) {
            state = true;
            notifyAll();
        }
    }

    /**
     * Waits for the latch to be set.  This returns immediately if the latch
     * is already set.
     *
     * @throws InterruptedException if the thread was interrupted while
     *         waiting for the latch to be set.
     */
    public synchronized void waitLatch() throws InterruptedException {
        while (!state) {
            wait();
        }
    }

    /**
     * Resets the latch.  The state is set to <code>false</code>.  No threads
     * are notified.
     */
    public synchronized void resetLatch() {
        state = false;
    }
}
