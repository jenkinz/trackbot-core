/*
 * Date: Nov 15, 2005
 * Time: 3:33:06 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Logging handler that sends the log entries to an output stream.
 * <p>
 * The default logging level is {@link Level#INFO}.</p>
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class StreamHandler extends Handler {
    private PrintStream out;

    /**
     * Creates a new handler with the given output stream and formatter.
     *
     * @param out the target output stream
     * @param f the formatter
     */
    public StreamHandler(OutputStream out, Formatter f) {
        setOutputStream(out);
        setFormatter(f);

        setLevel(Level.INFO);
    }

    /**
     * Sets a new output stream for this handler.  If there is already a
     * current output stream, then it is flushed and closed.
     *
     * @param out the new output stream
     */
    protected synchronized void setOutputStream(OutputStream out) {
        if (out == null) {
            throw new NullPointerException();
        }

        // Check for a previous stream

        if (this.out != null) {
            this.out.flush();
            this.out.close();
        }

        // Set the internal stream

        if (out instanceof PrintStream) {
            this.out = (PrintStream)out;
        } else {
            this.out = new PrintStream(out);
        }
    }

    /**
     * Formats the log message with the current formatter, and then sends the
     * result to the current output stream.
     *
     * @param record the log record
     */
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        if (out == null) {
            return;
        }

        Formatter f = getFormatter();
        if (f != null) {
            out.print(f.format(record));
        }
    }

    /**
     * Flushes any buffered data.
     */
    public synchronized void flush() {
        if (out != null) {
            out.flush();
        }
    }

    /**
     * Flushes and closes the current output stream.
     */
    public synchronized void close() {
        if (out != null) {
            out.close();  // This also flushes

            out = null;
        }
    }
}
