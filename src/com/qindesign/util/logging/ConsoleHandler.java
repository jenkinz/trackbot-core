/*
 * Date: Nov 15, 2005
 * Time: 4:16:22 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

/**
 * A stream handler that writes to <code>System.err</code>.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class ConsoleHandler extends StreamHandler {
    /**
     * Creates a new handler using a {@link SimpleFormatter}.
     */
    public ConsoleHandler() {
        this(new SimpleFormatter());
    }

    /**
     * Creates a new stream handler that writes to <code>System.err</code> and
     * uses the given formatter.
     *
     * @param f the formatter
     */
    public ConsoleHandler(Formatter f) {
        super(System.err, f);
    }

    /**
     * This flushes the stream after publishing the message.
     *
     * @param record the log record
     */
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    /**
     * This flushes the stream, but does not close it.
     */
    public void close() {
        flush();
    }
}
