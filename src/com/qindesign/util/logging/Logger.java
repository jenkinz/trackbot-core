/*
 * Date: Nov 15, 2005
 * Time: 2:12:21 PM
 * (c) 2005 Shawn Silverman
 */
package com.qindesign.util.logging;

import java.util.Hashtable;
import java.util.Vector;

/**
 * Implements a simplified logging framework.  This is based on the logging
 * facilities in JDK 1.4.
 *
 * @author Shawn Silverman
 * @version 1.0
 */
public class Logger {
    // TODO Logging in the background
    // TODO Avoid synchronizing when logging one entry
    // TODO Avoid creating a Hashtable when there's only one logger object

    /** Maintains a set of named loggers. */
    private static Hashtable loggers;

    private String name;
    private int level;

    /** Maintains a list of handlers. */
    private Vector handlers;

    /** Interal reusable log record structure. */
    private LogRecord logRecord;

    /**
     * Creates a new logger with the given name.  The name may be
     * <code>null</code> for an anonymous logger.
     * <p>
     * The default level is {@link Level#INFO}.</p>
     *
     * @param name the logger name
     */
    protected Logger(String name) {
        this.name = name;
        this.level = Level.INFO;

        // Create a reusable log record object

        logRecord = new LogRecord();
        logRecord.setLoggerName(name);
    }

    /**
     * Gets the logger with the specified name.  This creates a new logger if
     * one did not already exist.
     *
     * @param name the name of the logger to get
     * @return the logger with the given name.
     * @see #Logger(String)
     */
    public static synchronized Logger getLogger(String name) {
        Logger logger;

        if (loggers == null) {
            loggers = new Hashtable();
            logger = null;
        } else {
            logger = (Logger)loggers.get(name);
        }

        // Create a new logger if one doesn't already exist

        if (logger == null) {
            logger = new Logger(name);
            loggers.put(name, logger);
        }

        return logger;
    }

    /*
     * Gets the logger for the given class.  This creates a new logger if
     * one did not already exist.
     * <p>
     * This is equivalent to calling >getLogger(c.getName())</code>.</p>
     *
     * @param c the class to log
     * @return the logger for the given class.
     * @see #Logger(String)
     *
    public static synchronized Logger getLogger(Class c) {
        return getLogger(c.getName());
    }*/

    /**
     * Returns a newly created logger with no name.
     *
     * @return a new logger.
     */
    public static /*synchronized*/ Logger getAnonymousLogger()
    {
        return new Logger(null);
    }

    /**
     * Gets the name of this logger.  This will return <code>null</code> for
     * an anonymous logger.
     *
     * @return the name of this logger, or <code>null</code> for an anonymous
     *         logger.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the log level.  Messages with a lower level than this will not be
     * logged.  The value {@link Level#OFF} can be used to turn off logging.
     *
     * @param level the new log level
     * @see #getLevel()
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the log level.
     *
     * @return the log level.
     * @see #setLevel(int)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Adds a handler to receive log messages.  Note that handlers can be
     * added more than once.
     *
     * @param handler the handler to add to this logger
     */
    public void addHandler(Handler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }

        synchronized (this) {
            if (handlers == null) {
                handlers = new Vector();
            }
            handlers.addElement(handler);
        }
    }

    /**
     * Removes the given handler from this loggers list of handlers.
     *
     * @param handler the handler to remove from the list
     */
    public void removeHandler(Handler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }

        synchronized (this) {
            if (handlers == null) {
                return;
            }

            handlers.removeElement(handler);
        }
    }

    /**
     * Checks if a message with the given level would be logged.
     *
     * @param level the message logging level to check
     * @return whether a message with the given level would be logged.
     */
    public boolean isLoggable(int level) {
        return (level >= this.level) && (this.level != Level.OFF);
    }

    /**
     * Logs a message.
     * <p>
     * All logging requests end up calling this method.  Subclasses can
     * therefore override this method to capture all log activity.</p>
     *
     * @param record the log event
     */
    public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) {
            return;
        }

        // Post the log message to all the handlers

        synchronized (this) {
            if (handlers == null) return;

            for (int i = handlers.size(); --i >= 0; ) {
                ((Handler)handlers.elementAt(i)).publish(record);
            }
        }
    }

    /*
     * This method fills in the logger name, and then passes the record on to
     * {@link #log(LogRecord)}.  All the convenience methods in this class
     * this method instead of {@link #log(LogRecord)} because another class
     * may call {@link #log(LogRecord)} with a different logger name.
     *
    private void doLog() {
        logRecord.setLoggerName(name);
        log(logRecord);
    }*/

    /*
     * Direct log methods.
     */

    /**
     * Logs a message.  If the logger is enabled for the given message level,
     * then the message is forwarded to all the registered handlers.
     *
     * @param level the message level
     * @param msg the message
     */
    public final void log(int level, String msg) {
        log(level, null, null, msg, null);
    }

    /**
     * Logs a message with an associated exception.  If the logger is enabled
     * for the given message level, then the message is forwarded to all the
     * registered handlers.
     *
     * @param level the message level
     * @param msg the message
     * @param t the associated exception
     */
    public final void log(int level, String msg, Throwable t) {
        log(level, null, null, msg, t);
    }

    /**
     * Logs a message, specifying the source class and method.  If the logger
     * is enabled for the given message level, then the message is forwarded
     * to all the registered handlers.
     *
     * @param level the message level
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     * @param msg the message
     */
    public final void log(int level,
                          String sourceClass, String sourceMethod,
                          String msg)
    {
        log(level, sourceClass, sourceMethod, msg, null);
    }

    /**
     * Logs a message, specifying the source class and method, and associated
     * exception.  If the logger is enabled for the given message level, then
     * the message is forwarded to all the registered handlers.
     *
     * @param level the message level
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     * @param msg the message
     * @param t the associated exception
     */
    public final synchronized void log(int level,
                                       String sourceClass, String sourceMethod,
                                       String msg, Throwable t)
    {
        logRecord.setMillis(System.currentTimeMillis());

        logRecord.setLevel(level);
        logRecord.setMessage(msg);
        logRecord.setSourceClassName(sourceClass);
        logRecord.setSourceMethodName(sourceMethod);
        logRecord.setThrown(t);

        log(logRecord);
    }

    /*
     * Convenience methods for logging method entries and returns.
     */

    /**
     * Logs a method entry.  This is a convenience method that logs the
     * message "ENTRY" with a log level of FINER.
     *
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     */
    public void entering(String sourceClass, String sourceMethod) {
        log(Level.FINER, sourceClass, sourceMethod, "ENTRY", null);
    }

    /**
     * Logs a method return.  This is a convenience method that logs the
     * message "RETURN" with a log level of FINER.
     *
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     */
    public void exiting(String sourceClass, String sourceMethod) {
        log(Level.FINER, sourceClass, sourceMethod, "RETURN", null);
    }

    /**
     * Logs throwing an exception.
     *
     * @param sourceClass the class that claims to have issued the logging
     *                    request
     * @param sourceMethod the method that claims to have issued the logging
     *                     request
     * @param t the thrown exception
     */
    public void throwing(String sourceClass, String sourceMethod, Throwable t)
    {
        log(Level.FINER, sourceClass, sourceMethod, "THROW", t);
    }

    /*
     * Convenience methods using level names.
     */

    /**
     * Logs a SEVERE message.  If the logger is enabled for the SEVERE message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#SEVERE
     */
    public void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    /**
     * Logs a WARNING message.  If the logger is enabled for the WARNING
     * message level, then the message is forwarded to all the registered
     * handlers.
     *
     * @param msg the message
     * @see Level#WARNING
     */
    public void warning(String msg) {
        log(Level.WARNING, msg);
    }

    /**
     * Logs an INFO message.  If the logger is enabled for the INFO message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#INFO
     */
    public void info(String msg) {
        log(Level.INFO, msg);
    }

    /**
     * Logs a CONFIG message.  If the logger is enabled for the CONFIG message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#CONFIG
     */
    public void config(String msg) {
        log(Level.CONFIG, msg);
    }

    /**
     * Logs a FINE message.  If the logger is enabled for the FINE message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#FINE
     */
    public void fine(String msg) {
        log(Level.FINE, msg);
    }

    /**
     * Logs a FINER message.  If the logger is enabled for the FINER message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#FINER
     */
    public void finer(String msg) {
        log(Level.FINER, msg);
    }

    /**
     * Logs a FINEST message.  If the logger is enabled for the FINEST message
     * level, then the message is forwarded to all the registered handlers.
     *
     * @param msg the message
     * @see Level#FINEST
     */
    public void finest(String msg) {
        log(Level.FINEST, msg);
    }
}
