/*
 * Date: Dec 18, 2002
 * Time: 8:10:00 PM
 * (c) 2002-2007 Shawn Silverman
 */
package com.qindesign.util;

/**
 * This class implements a "pseudo" thread that can be safely stopped,
 * suspended, and resumed.
 *
 * @author Shawn Silverman
 * @version 1.1
 */
public abstract class PseudoThread implements Runnable {
    // Thread control

    private volatile boolean stopped;
    private volatile boolean suspended;  // Ensure prompt communication of the
                                         // suspend request

    // Sampler control

    private long interval;
    private Thread myThread;

    // Other state

    private Integer priority;
    //private Boolean daemon;

    /**
     * Creates a new pseudo-thread that performs work every so often, at the
     * given <code>interval</code>.
     *
     * @param interval time interval, in ms, between work
     * @throws IllegalArgumentException if <code>interval</code> is negative.
     */
    public PseudoThread(long interval) {
        setInterval(interval);
    }

    /**
     * Gets the work interval.
     */
    public synchronized final long getInterval() {
        return interval;
    }

    /**
     * Sets a new work interval, in milliseconds.
     *
     * @throws IllegalArgumentException if the interval is negative.
     */
    public final void setInterval(long interval) {
        if (interval < 0L) {
            throw new IllegalArgumentException("Interval must be >= 0: " + interval);
        }

        synchronized (this) {
            this.interval = interval;
        }
    }

    // NOTE No daemon threads in CLDC
    /*
     * Marks the thread as a daemon thread.
     *
     * @param flag indicates whether the thread is to be marked as a daemon
     *             thread
     * @throws IllegalThreadStateException if the thread is already active
     * @see Thread#setDaemon(boolean)
     *
    public final synchronized void setDaemon(boolean flag) {
        if (myThread != null) {
            throw new IllegalThreadStateException();
        }

        this.daemon = flag ? Boolean.TRUE : Boolean.FALSE;
    }*/

    /**
     * Sets the thread's priority.
     *
     * @param priority the new priority
     * @throws IllegalArgumentException if the priority is not in the range
     *         {@link Thread#MIN_PRIORITY MIN_PRIORITY}&ndash;{@link Thread#MAX_PRIORITY
     *         MAX_PRIORITY}.
     * @see Thread#setPriority(int)
     */
    public final synchronized void setPriority(int priority) {
        if (priority < Thread.MIN_PRIORITY || Thread.MAX_PRIORITY < priority) {
            throw new IllegalArgumentException("Priority out of range: " + priority);
        }

        // Remember the priority in case the thread has not started yet

        this.priority = new Integer(priority);

        if (myThread != null) {
            myThread.setPriority(priority);
        }
    }

    /**
     * Starts a thread.  This will guarantee there is only one instance.
     * <p>
     * The thread can be suspended before it is started by calling
     * {@link #suspend()} prior to a call to this method.</p>
     *
     * @throws IllegalThreadStateException if a thread has already been
     *         started.
     * @see #suspend()
     */
    public final synchronized void start() {
        if (myThread == null) {
            myThread = new Thread(this);

            // Set some other state

            if (priority != null) {
                myThread.setPriority(priority.intValue());
            }
            /*if (daemon != null) {
                myThread.setDaemon(daemon.booleanValue());
            }*/

            // Start the thread

            myThread.start();
        } else {
            throw new IllegalThreadStateException();
        }
    }

    /**
     * Stops the thread.  The thread may or not be stopped after this method
     * exits.
     *
     * @see #isAlive()
     * @see #join()
     */
    public final synchronized void stop() {
        boolean oldStopped = stopped;
        stopped = true;

        if (!oldStopped && myThread != null) {
            myThread.interrupt();
        }
    }

    /**
     * Suspends the thread.  This does nothing if it is already suspended.
     * <p>
     * This method can also be used to suspend the thread before it has been
     * started.</p>
     */
    public final void suspend() {
        suspended = true;
    }

    /**
     * Resumes the thread.  This also has the effect of cutting short the
     * sleep between work intervals if the thread has been suspended.  For
     * example, say the interval is one second, work has just been done, and
     * the thread has been sleeping for half a second.  If the thread is
     * suspended at this point and then resumed shortly after, then this will
     * resume work immediately, without waiting a further half a second.
     */
    public final void resume() {
        if (suspended) {
            suspended = false;

            synchronized (myThread) {
                myThread.notify();
            }
        }
    }

    /**
     * Tests if this thread is still running.
     *
     * @see Thread#isAlive()
     */
    public final boolean isAlive() {
        return (myThread != null && myThread.isAlive());
    }

    /**
     * Waits for this thread to stop running.
     *
     * @throws InterruptedException if another thread has interrupted the
     *         current thread.  Note that the interrupted status of the
     *         current thread is cleared when this exception is thrown.
     * @see Thread#join()
     */
    public final void join() throws InterruptedException {
        if (myThread != null) {  // myThread will never become null if it is already not null
            myThread.join();
        }
    }

    /**
     * Does the work between intervals.  Implementors can use the interrupted
     * status of the current thread to see if this pseudo-thread has been
     * stopped.
     */
    public abstract void doWork();

    /**
     * Thread execution.  Should not be called externally.
     */
    public final void run() {
        // Protect against external calls

        if (Thread.currentThread() != myThread) return;

        // NOTE CLDC does not have Thread.isInterrupted() or Thread.interrupted(),
        //      so we can't check for those in the 'while' test
        while (!stopped) {
            try {
                if (suspended) {
                    synchronized (myThread) {
                        while (suspended && !stopped) {
                            myThread.wait();
                        }
                    }
                }

                doWork();

                // Wait for the sleep period
                // Waiting as opposed to sleeping allows immediate resumption
                // of a suspended thread

                long interval;
                synchronized (this) {
                    interval = this.interval;
                }
                synchronized (myThread) {
                    myThread.wait(interval);
                }
            } catch (InterruptedException ex) {
                stopped = true;  // Respond to an interrupt
            }
        }
    }

    /**
     * Returns a string representation of this pseudo-thread, including its
     * name and priority.
     *
     * @return a string representation of this pseudo-thread.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();

        buf.append("PseudoThread[PT-")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(',');

        // Priority

        if (priority != null) {
            buf.append(priority.intValue());
        } else if (myThread != null) {
            buf.append(myThread.getPriority());
        } else {
            buf.append("<unknown priority>");
        }

        buf.append(']');

        return buf.toString();
    }
}
