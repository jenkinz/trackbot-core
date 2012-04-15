/*
 * Date: Feb 25, 2008
 * Time: 12:43:12 PM
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.io;

import java.io.IOException;

/**
 * Represents the abstract concept of a channel.  Users can subscribe to
 * channels, send, receive, and wait for data.
 * <p>
 * It is up to subclasses to provide some sort of subscribe mechanism.</p>
 *
 * @author Shawn Silverman
 * @version 0.0
 */
public abstract class Channel {
    /**
     * Represents data received on the channel.
     */
    public abstract static class Data {
        /** The data is stored here. */
        protected byte[] data;

        /**
         * Creates a new data object.  It is up to subclasses to fill in the
         * {@link #data} array.
         */
        protected Data() {
        }

        /**
         * Gets the data.
         *
         * @return the data.
         */
        public byte[] getData() {
            return data;
        }
    }

    /**
     * Creates a new channel.
     */
    protected Channel() {
        super();
    }

    /**
     * Sends data to the channel.
     *
     * @param data the data to send, a byte array
     * @param off offset into the array
     * @param len the data length
     * @throws IOException if there was an I/O error while sending the data.
     */
    public abstract void send(byte[] data, int off, int len)
        throws IOException;

    /**
     * Receives data from the channel.  This waits until data is available.
     * If the channel has been {@linkplain #close() closed}, then this will
     * return <code>null</code>.
     * <p>
     * It is up to subclasses to determine what kind of object to return.</p>
     *
     * @return data from the channel.
     * @throws IOException if there was an I/O error while receiving data.
     * @see #close()
     * @see #isDataAvailable()
     * @see Data
     */
    public abstract Data receive() throws IOException;

    /**
     * Checks if data is available.  If data is available, then {@link #receive()}
     * will not block.
     *
     * @return whether data is available.
     */
    public abstract boolean isDataAvailable();

    /**
     * Closes the channel and cleans up any resources.  Closing a channel that
     * has already been closed has no effect.
     */
    public abstract void close();
}
