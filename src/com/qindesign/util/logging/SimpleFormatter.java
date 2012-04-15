/*
 * Date: Nov 15, 2005
 * Time: 4:24:54 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

import java.util.Date;

/**
 * A simple formatter that outputs in a human-readable form.  The typical log
 * entry is one or two lines.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class SimpleFormatter extends Formatter {
    /** System line separator. */
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /** Shared <code>Date</code> object. */
    private Date date = new Date();

    /**
     * Formats the log message into a simple human-readable form.
     *
     * @param record the log record
     */
    public String format(LogRecord record) {
        StringBuffer buf = new StringBuffer();

        // Date and time

        synchronized (this) {
            date.setTime(System.currentTimeMillis());
            buf.append(date);
        }

        // Class and method names

        String className = record.getSourceClassName();
        String methodName = record.getSourceMethodName();

        buf.append(' ');
        if (className != null) {
            buf.append(className);
        } else {
            buf.append(record.getLoggerName());
        }

        if (methodName != null) {
            buf.append(className == null ? ' ' : '.');
            buf.append(methodName);
        }

        buf.append(LINE_SEPARATOR);

        // Level name

        buf.append(Level.toString(record.getLevel()));
        buf.append(": ");
        buf.append(record.getMessage());

        buf.append(LINE_SEPARATOR);

        // Possible stack trace
        // TODO Make the stack trace compatible with CLDC

        if (record.getThrown() != null) {
            /*ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            record.getThrown().printStackTrace(ps);
            ps.close();  // This also flushes

            buf.append(baos.toString());*/
            buf.append(record.getThrown());
            buf.append(LINE_SEPARATOR);
        }

        return buf.toString();
    }
}
