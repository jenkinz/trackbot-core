/*
 * Date: Feb 25, 2008
 * Time: 11:46:10 AM
 *
 * (c) 2008 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

import java.io.IOException;

/**
 * Sends telemetry data.  It is up to subclasses to decide how the data is
 * sent, and what form it takes.
 *
 * @author Shawn Silverman
 */
public abstract class Telemetry implements Events.Listener {
    /**
     * Creates a new telemetry object.
     */
    protected Telemetry() {
        super();
    }

    /**
     * Sends the telemetry data.
     *
     * @throws IOException if there was an I/O error while sending the data.
     */
    protected abstract void send() throws IOException;
}
