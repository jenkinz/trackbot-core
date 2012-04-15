/*
 * Date: Nov 15, 2005
 * Time: 3:15:15 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

/**
 * This class provides support for formatting log messages.
 * <p>
 * Typically, each handler will have a formatter associated with it.</p>
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public abstract class Formatter {
    /**
     * Creates a new formatter.
     */
    protected Formatter() {
    }

    /*
     * Formats a log message.  This is a convenience method that calls
     * {@link #format(int, String, String, String, Throwable, String)} with a
     * value of <code>null</code> for all parameters but the message
     * parameter.
     *
     * @param level the message level
     * @param msg the log message
     * @param loggerName the logger name
     *
    public final String format(int level, String msg, String loggerName) {
        return format(level, null, null, msg, null, loggerName);
    }*/

    /*
     * Formats a log message.  This is a convenience method that calls
     * {@link #format(int, String, String, String, Throwable, String)} with a
     * value of <code>null</code> for the class name and method name
     * parameters.
     *
     * @param level the message level
     * @param msg the log message
     * @param t the associated exception
     * @param loggerName the logger name
     *
    public final String format(int level,
                               String msg, Throwable t,
                               String loggerName) {
        return format(level, null, null, msg, t, loggerName);
    }*/

    /*
     * Formats a log message.  This is a convenience method that calls
     * {@link #format(int, String, String, String, Throwable, String)} with a
     * value of <code>null</code> for the exception parameter.
     *
     * @param level the message level
     * @param className the class that claims to have issued the logging
     *                  request
     * @param methodName the method that claims to have issued the logging
     *                   request
     * @param msg the log message
     * @param loggerName the logger name
     *
    public final String format(int level,
                               String className, String methodName,
                               String msg,
                               String loggerName) {
        return format(level, className, methodName, msg, null, loggerName);
    }*/

    /**
     * Formats a log message.
     *
     * @param record the log record to be formatted
     * @return the formatted log record
     */
    public abstract String format(LogRecord record);
}
