/*
 * Date: Mar 23, 2008
 * Time: 12:53:18 AM
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implements a channel that pretends to operate over radio channels.
 * Simulators can use this to provide inter-robot communication.
 *
 * @author Shawn Silverman
 * @version 0.0
 */
public class VirtualRadioChannel extends Channel {
    /*public static void main(String[] args) throws Exception {
        Channel c = subscribe(-1, 10);
        for (int i = 0; i < 5; i++) {
            final int ii = i;
            new Thread() {
                public void run() {
                    Channel channel = subscribe(ii, 10);
                    try {
                        Data data = (Data)channel.receive();
                        System.out.println(ii + ": " + data.getAddress() + ": " + new String(data.getData()));
                    } catch (IOException ex) {
                    }
                }
            }.start();
        }

        Thread.sleep(1000L);
        c.send("Hello!".getBytes(), 0, 6);
    }*/

    /**
     * This class represents received data.  It contains the actual data plus
     * the sender's address.
     *
     * @author Shawn Silverman
     */
    public static final class Data extends Channel.Data {
        private int address;

        /**
         * Creates a new data object from the given radiogram.
         *
         * @param address the address of the sender
         * @param data the data, this is copied internally
         * @param off the offset into the data array
         * @param len the data length
         */
        Data(int address, byte[] data, int off, int len) {
            // Copy the data

            this.data = new byte[len];
            System.arraycopy(data, off, this.data, 0 ,len);

            this.address = address;
        }

        /**
         * Gets the sender's address.
         *
         * @return the sender's address.
         */
        public int getAddress() {
            return address;
        }
    }

    private static Map subscribers = new HashMap();

    /** The data queue. */
    private LinkedList queue;

    private volatile boolean closed;

    // Channel and subscriber information

    private int address;
    private Integer port;

    /**
     * Subscribes to the specified radio channel.  When a user subscribes,
     * they can send, receive, and wait for data.  It is up to the subscriber
     * to choose a unique address.
     * <p>
     * Note that a new channel will be created every time this method is
     * called.</p>
     *
     * @param address the subscriber's address
     * @param port subscribe to this radio port
     * @return a new channel object.
     */
    public static VirtualRadioChannel subscribe(int address, int port) {
        VirtualRadioChannel channel = new VirtualRadioChannel(address, port);

        // Add to the subscribers list

        List subs;
        synchronized (subscribers) {
            subs = (List)subscribers.get(new Integer(port));
            if (subs == null) {
                // Create a new list

                subs = new ArrayList();
                subscribers.put(new Integer(port), subs);
            }
        }

        synchronized (subs) {
            subs.add(channel);
        }

        return channel;
    }

    /**
     * Create a new radiogram channel and use the specified radio port.
     *
     * @param address the address of the subscriber
     * @param port subscribe to this radio port
     */
    private VirtualRadioChannel(int address, int port) {
        this.address = address;
        this.port = new Integer(port);

        // Create the queue

        queue = new LinkedList();
    }

    /**
     * Closes the connection to this channel.
     */
    public void close() {
        synchronized (queue) {
            if (closed) return;

            closed = true;

            // Clear the queue

            queue.clear();
            queue.notifyAll();
        }

        // Remove ourselves from the subscriber list

        List subs;
        synchronized (subscribers) {
            subs = (List)subscribers.get(port);
        }
        if (subs != null) {  // Check for null, for robustness
            synchronized (subs) {
                subs.remove(this);
            }
        }
    }

    /**
     * Sends data to the channel.
     *
     * @param b the data
     * @param off offset into the data
     * @param len data length
     */
    public void send(byte[] b, int off, int len) {
        if (closed) return;

        // Notify all the subscribers

        List subs;
        synchronized (subscribers) {
            subs = (List)subscribers.get(port);
        }
        if (subs == null) return;

        synchronized (subs) {
            for (Iterator iter = subs.iterator(); iter.hasNext() && !closed; ) {
                VirtualRadioChannel c = (VirtualRadioChannel)iter.next();
                if (c != this) {
                    c.queueData(address, b, off, len);
                }
            }
        }
    }

    /**
     * Adds data to the queue.
     *
     * @param address the sender's address
     */
    private void queueData(int address, byte[] b, int off, int len) {
        Data data = new Data(address, b, off, len);

        synchronized (queue) {
            if (closed) return;

            queue.addLast(data);
            queue.notify();  // Notify only one thread
        }
    }

    /**
     * Waits for data to be received by the channel.  This returns
     * <code>null</code> if the channel has been {@linkplain #close() closed}.
     * <p>
     * The {@link #isDataAvailable()} method can be used to check if there is
     * any data waiting.</p>
     * <p>
     * This returns objects of type {@link Data}.</p>
     *
     * @return the data received.
     * @see #isDataAvailable()
     * @see #close()
     * @see Data
     */
    public Channel.Data receive() {
        synchronized (queue) {
            if (closed) return null;

            while (queue.isEmpty()) {
                try {
                    queue.wait();
                } catch (InterruptedException ex) {
                    return null;
                }
            }

            // Pop the queue

            if (queue.isEmpty()) return null;  // Closing the channel clears the queue, and the channel may have been closed while waiting
            return (Data)queue.removeFirst();
        }
    }

    /**
     * Checks if there is any data waiting in the queue.
     *
     * @return whether data is available in the queue.
     */
    public boolean isDataAvailable() {
        synchronized (queue) {
            if (closed) return false;

            return !queue.isEmpty();
        }
    }
}
