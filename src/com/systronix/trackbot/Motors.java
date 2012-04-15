/*
 * Date: Oct 21, 2007
 * Time: 12:58:39 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot;

/**
 * This class controls the motors on the TrackBot.
 *
 * @author Shawn Silverman
 * @version 0.1
 */
public class Motors {
    /** The port-side motor. */
    public static final int MOTOR_PORT = 'P';

    /** The starboard-side motor. */
    public static final int MOTOR_STARBOARD = 'S';

    /** All motors. */
    public static final int MOTOR_ALL = 'A';

    /** A convenience value indicating coasting. */
    public static final int SPEED_COAST  = 0;

    /** A convenience value indicating the slowest speed. */
    public static final int SPEED_SLOWEST = 1;

    /** A convenience value indicating slow speed. */
    public static final int SPEED_SLOW   = 3;

    /** A convenience value indicating medium speed. */
    public static final int SPEED_MEDIUM = 6;

    /** A convenience value indicating fast speed. */
    public static final int SPEED_FAST   = 9;

    // Direction values

    /** Represents the forward direction. */
    public static final int DIR_FORWARD = '+';

    /** Represents the reverse direction. */
    public static final int DIR_REVERSE = '-';

    /** Represents 'brake'. */
    public static final int DIR_BRAKE   = '*';

    private RobotIO robotIO;
    private byte[] msgBuf;

    // Keep track of the last command sent to the motors

    private int lastPortSpeed = -1;
    private int lastStarboardSpeed = -1;
    private int lastPortDirection;
    private int lastStarboardDirection;

    /**
     * Creates a new <code>Motors</code> object using the given TrackBot
     * communications object.
     *
     * @param robotIO the TrackBot communications object
     */
    public Motors(RobotIO robotIO) {
        this.robotIO = robotIO;
        msgBuf = new byte[8];

        // Pre set some message values

        msgBuf[0] = '!';
        msgBuf[1] = 'M';
        msgBuf[7] = '\r';
    }

    /**
     * Checks the given motor value.
     *
     * @param motor the motor value to check
     * @throws IllegalArgumentException if the motor value is invalid.
     */
    private static void checkMotor(int motor) {
        switch (motor) {
            case MOTOR_PORT:
            case MOTOR_STARBOARD:
            case MOTOR_ALL:
                return;
        }
        throw new IllegalArgumentException("Invalid motor: " + motor);
    }

    /**
     * Sets the specified motor(s) to go forward at the specified speed.  The
     * speed can range from <code>0</code> to <code>10</code>.
     *
     * @param motor the motor(s) to go forward
     * @param speed the speed, a value in the range 0&ndash;10
     * @throws IllegalArgumentException if the speed is out of range or the
     *         motor value is invalid.
     * @see #MOTOR_PORT
     * @see #MOTOR_STARBOARD
     * @see #MOTOR_ALL
     */
    public void goForward(int motor, int speed) {
        move(motor, speed, (byte)DIR_FORWARD, false);
    }

    /**
     * Sets the specified motor(s) to go forward at the specified speed.  The
     * <code>abrupt</code> parameter indicates whether any speed change should
     * be abrupt, or should be changed linearly with a smooth trapezoidal
     * ramp.
     *
     * @param motor the motor(s) to go forward
     * @param speed the speed, a value in the range 0&ndash;10
     * @param abrupt whether to change speed abruptly
     * @throws IllegalArgumentException if the speed is out of range or the
     *         motor value is invalid.
     * @see #goForward(int, int)
     * @see #MOTOR_PORT
     * @see #MOTOR_STARBOARD
     * @see #MOTOR_ALL
     * @since FW 00.05
     */
    public void goForward(int motor, int speed, boolean abrupt) {
        move(motor, speed, (byte)DIR_FORWARD, abrupt);
    }

    /**
     * Sets the specified motor(s) to go backward at the specified speed.  The
     * speed can range from <code>0</code> to <code>10</code>.
     *
     * @param motor the motor(s) to go backward
     * @param speed the speed, a value in the range 0&ndash;10
     * @throws IllegalArgumentException if the speed is out of range or the
     *         motor value is invalid.
     * @see #MOTOR_PORT
     * @see #MOTOR_STARBOARD
     * @see #MOTOR_ALL
     */
    public void goReverse(int motor, int speed) {
        move(motor, speed, (byte)DIR_REVERSE, false);
    }

    /**
     * Sets the specified motor(s) to go backward at the specified speed.  The
     * <code>abrupt</code> parameter indicates whether any speed change should
     * be abrupt, or should be changed linearly with a smooth trapezoidal
     * ramp.
     *
     * @param motor the motor(s) to go forward
     * @param speed the speed, a value in the range 0&ndash;10
     * @param abrupt whether to change speed abruptly
     * @throws IllegalArgumentException if the speed is out of range or the
     *         motor value is invalid.
     * @see #goReverse(int, int)
     * @see #MOTOR_PORT
     * @see #MOTOR_STARBOARD
     * @see #MOTOR_ALL
     * @since FW 00.05
     */
    public void goReverse(int motor, int speed, boolean abrupt) {
        move(motor, speed, (byte)DIR_REVERSE, abrupt);
    }

    /**
     * Returns the last speed value sent to the specified motor.  This returns
     * <code>-1</code> if the the value is unknown.
     *
     * @param motor the motor for which to get the last speed sent
     * @return the last speed sent to the specified motor, or <code>-1</code>
     *         if the value is unknown.
     */
    public int getLastSpeed(int motor) {
        switch (motor) {
            case MOTOR_PORT:
                return lastPortSpeed;
            case MOTOR_STARBOARD:
                return lastStarboardSpeed;
            case MOTOR_ALL:
                if (lastPortSpeed == lastStarboardSpeed) {
                    return lastPortSpeed;
                }
                break;
        }
        return -1;
    }

    /**
     * Returns the last direction value sent to the specified motor.  This
     * returns <code>-1</code> if the the value is unknown.
     *
     * @param motor the motor for which to get the last speed sent
     * @return the last direction sent to the specified motor, or
     *         <code>-1</code> if the value is unknown.
     * @see #DIR_FORWARD
     * @see #DIR_REVERSE
     * @see #DIR_BRAKE
     */
    public int getLastDirection(int motor) {
        switch (motor) {
            case MOTOR_PORT:
                return lastPortDirection;
            case MOTOR_STARBOARD:
                return lastStarboardDirection;
            case MOTOR_ALL:
                if (lastPortDirection == lastStarboardDirection) {
                    return lastPortDirection;
                }
                break;
        }
        return -1;
    }

    /**
     * Moves the specified motor(s).
     *
     * @param motor the motor(s) to move
     * @param speed the speed
     * @param direction the direction
     * @param jumpBell indicates whether to abruptly change the speed without
     *                 a trapezoidal ramp-up
     */
    private void move(int motor, int speed, byte direction, boolean jumpBell) {
        if (speed < 0 || 10 < speed) {
            throw new IllegalArgumentException("Invalid speed: " + speed);
        }
        checkMotor(motor);

        synchronized (this) {
            msgBuf[2] = (byte)motor;
            msgBuf[3] = direction;
            msgBuf[4] = (byte)((speed == 10) ? '1' : '0');
            msgBuf[5] = (byte)((speed % 10) + '0');

            msgBuf[6] = jumpBell ? (byte)'J' : (byte)'\r';

            robotIO.queueMessage(msgBuf, 0, jumpBell ? 8 : 7);

            // Keep track of the last values sent

            if (MOTOR_PORT == motor || MOTOR_ALL == motor) {
                lastPortSpeed = speed;
                lastPortDirection = direction;
            }
            if (MOTOR_STARBOARD == motor || MOTOR_ALL == motor) {
                lastStarboardSpeed = speed;
                lastStarboardDirection = direction;
            }
        }
    }

    /**
     * Brakes the specified motor(s).
     *
     * @param motor the motor(s) to brake
     * @throws IllegalArgumentException if the motor value is invalid.
     * @see #MOTOR_PORT
     * @see #MOTOR_STARBOARD
     * @see #MOTOR_ALL
     */
    public void brake(int motor) {
        checkMotor(motor);

        synchronized (this) {
            msgBuf[2] = (byte)motor;
            msgBuf[3] = (byte)DIR_BRAKE;
            msgBuf[4] = '0';
            msgBuf[5] = '0';
            msgBuf[6] = '\r';

            robotIO.queueMessage(msgBuf, 0, 7);

            // Keep track of the last values sent

            if (MOTOR_PORT == motor || MOTOR_ALL == motor) {
                lastPortSpeed = 0;
                lastPortDirection = DIR_BRAKE;
            }
            if (MOTOR_STARBOARD == motor || MOTOR_ALL == motor) {
                lastStarboardSpeed = 0;
                lastStarboardDirection = DIR_BRAKE;
            }
        }
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    public String toString() {
        return "Robot motors";
    }
}
