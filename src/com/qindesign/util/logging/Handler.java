/*
 * Date: Nov 15, 2005
 * Time: 2:54:33 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

/**
 * This class takes log messages and exports them.  A handler may or may not
 * require an associated formatter.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public abstract class Handler {
    private Formatter formatter;
    private int level;

    /**
     * Creates a new handler.  No formatter is set.
     */
    protected Handler() {
    }

    /*
     * Publishes a log message.  This is a convenience method that calls
     * {@link #publish(int, String, String, String, Throwable, String)} with a
     * value of <code>null</code> for all parameters but the message
     * parameter.
     *
     * @param level the message level
     * @param msg the log message
     * @param loggerName the logger name
     *
    public final void publish(int level, String msg, String loggerName) {
        publish(level, null, null, msg, null, loggerName);
    }*/

    /*
     * Publishes a log message.  This is a convenience method that calls
     * {@link #publish(int, String, String, String, Throwable, String)} with a
     * value of <code>null</code> for the class name and method name
     * parameters.
     *
     * @param level the message level
     * @param msg the log message
     * @param t the associated exception
     * @param loggerName the logger name
     *
    public final void publish(int level,
                              String msg, Throwable t,
                              String loggerName) {
        publish(level, null, null, msg, t, loggerName);
    }*/

    /*
     * Publishes a log message.  This is a convenience method that calls
     * {@link #publish(int, String, String, String, Throwable, String)} with a
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
    public final void publish(int level,
                              String className, String methodName,
                              String msg,
                              String loggerName) {
        publish(level, className, methodName, msg, null, loggerName);
    }*/

    /**
     * Publishes a log message, possible exception, and issuing class and
     * method.
     *
     * @param record the log record
     */
    public abstract void publish(LogRecord record);

    /**
     * Flushes any buffered output.
     */
    public abstract void flush();

    /**
     * Closes the handler and frees any resources.  This method will first
     * perform a flush, and then close the handler.
     */
    public abstract void close();

    /**
     * Sets the formatter that will be used to format messages for this
     * handler.
     *
     * @param f the new formatter to use
     */
    public void setFormatter(Formatter f) {
        if (f == null) {
            throw new NullPointerException();
        }
        this.formatter = f;
    }

    /**
     * Gets the formatter associated with this handler.
     *
     * @return the formatter associated with this handler.
     */
    public Formatter getFormatter() {
        return formatter;
    }

    /**
     * Sets the log level for this handler.  Messages with a lower level than
     * this will not be logged.
     * <p>
     * The intention of this method is to allow developers to limit messages
     * sent to specific handlers.</p>
     *
     * @param level the new log level
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the log level for this handler.  Messages with a lower level will
     * not be logged.
     *
     * @return the level of messages being logged for this handler.
     */

    public int getLevel() {
        return level;
    }

    /**
     * Checks if this handler would log the given record.  This method checks
     * if the record has a sufficient level for publishing using this handler,
     * and also performs any other handler-specific checks that might prevent
     * the record from being published.
     *
     * @param record the record whose level to check
     * @return whether the given record would be logged.
     */
    public boolean isLoggable(LogRecord record) {
        return (record.getLevel() >= this.level) && (this.level != Level.OFF);
    }
}
