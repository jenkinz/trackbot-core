/*
 * Date: Oct 21, 2007
 * Time: 1:25:00 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.demo;

import com.systronix.trackbot.Motors;
import com.systronix.trackbot.Robot;
import com.systronix.trackbot.RobotIO;
import com.systronix.trackbot.VersionInfo;
import com.systronix.io.Debug;
import com.qindesign.util.Random;
import com.qindesign.util.logging.Level;

/**
 * Program for the robot that avoids obstacles.
 * <p>
 * Note that the state machine (the "behavior") runs on a separate thread from
 * the event notification thread.  The purpose of this is so that the code
 * won't miss events if they stream in faster than the behavior can process
 * them.  One use case is when keeping track of sensor history without
 * processing it.</p>
 *
 * @author Shawn Silverman
 * @version 0.7
 */
public class Avoider extends Robot {
    /** The STOPPED state. */
    protected static final int STATE_STOPPED = 0;

    /** The RUNAWAY state. */
    protected static final int STATE_RUNAWAY = 1;

    /**
     * The maximum state value in this class.  Additional states in subclasses
     * or other places should start at <code>MAX_STATE&nbsp;+&nbsp;1</code>.
     */
    protected static final int MAX_STATE = STATE_RUNAWAY;

    /**
     * The main entry point.
     *
     * @param args the program arguments
     * @throws Exception if there was an error while attempting to connect to
     *         the robot.
     * @throws Error if the no connection could be made to the robot.
     */
    public static void main(String[] args) throws Exception {
        Debug.setLoggingLevel(Level.FINE);
        Debug.info("Avoider application starting.");

        // Start the Avoider

        Avoider avoider = new Avoider();
        //avoider.stateMachine();
    }

    /** This holds the current state. */
    protected int state = STATE_STOPPED;

    /** Indicates that the state machine is stopped. */
    protected volatile boolean stopped;

    /** The thread on which the state machine is running. */
    private Thread smThread;

    // Current data, modules, and state

    /** The current power node state. */
    protected int powerNodeState = -1;

    /** The current sensor node state. */
    protected int sensorNodeState = -1;

    /**
     * Holds the last direction sent to the motors.  Please see the
     * <code>DIR_<em>XXX</em></code> constants.
     */
    protected int lastDirection;

    // Activity locks

    /**
     * A lock used for communicating that there is a new IR sensor value
     * available.
     */
    protected Object irSensorLock = new Object();

    protected Random random = new Random();  // Source of randomness

    /**
     * Creates the Avoider.
     *
     * @throws Exception if there was an error while trying to connect to the
     *         robot.
     */
    public Avoider() throws Exception {
        super();
    }

    /**
     * Creates the Avoider using the given I/O connetion.
     *
     * @param robotIO the robot I/O connection
     */
    public Avoider(RobotIO robotIO) {
        super(robotIO);
    }

    /**
     * Stops the state machine.
     *
     * @see #stateMachine()
     */
    protected final void stopStateMachine() {
        if (stopped) return;

        stopped = true;

        synchronized (this) {
            if (smThread != null) {
                Thread t = smThread;
                smThread = null;
                t.interrupt();
            }
        }
    }

    /**
     * Stops the state machine and also cleans up any resources used by this
     * object.
     */
    public void destroy() {
        stopStateMachine();

        super.destroy();
    }

    /**
     * Runs the state machine.  This chooses the next state, and then executes
     * the appropriate action.
     */
    private void stateMachine() {
        chooseNextState();
        runState();
    }

    /**
     * Perfoms an action based on the current state.  Subclasses that override
     * this method should call <code>super.runState()</code> for any states
     * they don't understand.
     */
    protected void runState() {
        switch (state) {
            case STATE_STOPPED:
                doStopped();
                break;
            case STATE_RUNAWAY:
                doRunaway();
                break;
            default:
                state = STATE_STOPPED;
        }
    }

    /**
     * Chooses the next state based on the current sensor data.  This is called
     * after {@link #runState()} and after the main loop waits for the next
     * event.  Subclasses that override this method should call
     * <code>super.chooseNextState()</code> for any states they don't
     * understand.
     */
    protected void chooseNextState() {
        switch (state) {
            case STATE_STOPPED:
                // Listen to the corner, side, or cliff IR sensors

                if ((powerNodeState & 0xf000) != 0  // Something on the four corner sensors
                      || (sensorNodeState & 0x0f00) != 0  // Something on the sides
                      || (sensorNodeState & 0xf000) != 0xf000) {  // Something on the cliffs
                                                                  // All "on" means no cliff
                    this.state = STATE_RUNAWAY;
                }  // If this wasn't true, then there's nothing around, so stay "STOPPED"
                break;

            case STATE_RUNAWAY:
                // Listen to the corner, side, and cliff IR sensors

                if ((powerNodeState & 0xf000) == 0  // Nothing on the four corner sensors
                      && (sensorNodeState & 0x0f00) == 0  // Nothing on the sides
                      && (sensorNodeState & 0xf000) == 0xf000) {  // Nothing on the cliffs
                    // If I see nothing around, enter "STOPPED"

                    this.state = STATE_STOPPED;
                }
                break;

            default:  // No other states shold exist
                this.state = STATE_STOPPED;  // Make this switch 'robust'
        }
    }

    /**
     * Behavior for the STOPPED state.
     * <p>
     * This can transition to the RUNAWAY state.</p>
     */
    protected void doStopped() {
        motors.brake(Motors.MOTOR_ALL);

        Debug.finer("Avoider: STOPPED");
    }

    // Convenience constants for source code readability

    /** The "stop" direction. */
    protected static final int DIR_STOP                = 0;

    /** The "veer forward left" direction. */
    protected static final int DIR_VEER_FORWARD_LEFT   = 1;

    /** The "veer forward right" direction. */
    protected static final int DIR_VEER_FORWARD_RIGHT  = 2;

    /** The "veer backward left" direction. */
    protected static final int DIR_VEER_BACKWARD_LEFT  = 3;

    /** The "veer backward right" direction. */
    protected static final int DIR_VEER_BACKWARD_RIGHT = 4;

    /** The "forward" direction. */
    protected static final int DIR_FORWARD             = 5;

    /** The "backward" direction. */
    protected static final int DIR_BACKWARD            = 6;

    /** The "turn left" direction. */
    protected static final int DIR_TURN_LEFT           = 7;

    /** The "turn right" direction. */
    protected static final int DIR_TURN_RIGHT          = 8;

    /** The direction names, for debugging purposes. */
    protected static final String[] DIR_NAMES = {
            "Stop",
            "Veer forward left",
            "Veer forward right",
            "Veer backward left",
            "Veer backward right",
            "Forward",
            "Backward",
            "Turn left",
            "Turn right" };

    /**
     * Behavior for the RUNAWAY state.
     * <p>
     * This can transition to the STOPPED state.</p>
     */
    protected void doRunaway() {
        // Figure out what direction to go
        int dir = DIR_STOP;

        // Make a decision based on the corner sensors

        // Bit 15: Fore port
        // Bit 14: Fore starboard
        // Bit 13: Aft port
        // Bit 12: Aft starboard

        boolean noCorners = false;
        boolean useSideSensors = true;

        switch ((powerNodeState >> 12) & 0x000f) {
            case 0:  // No triggers
                noCorners = true;
                dir = DIR_STOP;
                useSideSensors = false;
                break;

            case 15:  // All sensors
                dir = DIR_STOP;
                break;

            case 1:  // Just aft starboard
                dir = DIR_VEER_FORWARD_RIGHT;
                break;

            case 9:  // Fore port and aft starboard
            case 11:  // Fore port and both back sensors
            case 10:  // Fore port and aft port
            case 13:  // Both front sensors and aft starboard
                if (DIR_TURN_LEFT == lastDirection) {
                    dir = DIR_TURN_LEFT;
                } else {
                    dir = DIR_TURN_RIGHT;
                }
                break;

            case 5:  // Fore starboard and aft starboard
            case 7:  // Fore starboard and both back sensors
            case 6:  // Fore starboard and aft port
            case 14:  // Both front sensors and aft port
                if (DIR_TURN_RIGHT == lastDirection) {
                    dir = DIR_TURN_RIGHT;
                } else {
                    dir = DIR_TURN_LEFT;
                }
                break;

            case 2:  // Just aft port
                dir = DIR_VEER_FORWARD_LEFT;
                break;

            case 3:  // Both rear sensors; move straight forward
                dir = DIR_FORWARD;
                break;
            case 4:  // Just fore starboard
                dir = DIR_VEER_BACKWARD_RIGHT;
                break;

            case 8:  // Just fore port
                dir = DIR_VEER_BACKWARD_LEFT;
                break;
            case 12:  // Both front sensors; go straight back
                dir = DIR_BACKWARD;
                break;
        }//switch powerNodeState

        // Make a decision based on the side sensors

        // Bit 11: Port fore
        // Bit 10: Starboard fore
        // Bit  9: Port aft
        // Bit  8: Starboard aft

        if (useSideSensors) {
            switch ((sensorNodeState >> 8) & 0x000f) {
                case 0:  // No triggers
                    if (DIR_STOP == dir) {
                        if (!noCorners) {
                            // If not already turning, turn a random direction

                            if (DIR_TURN_RIGHT == lastDirection) {
                                dir = DIR_TURN_RIGHT;
                            } else if (DIR_TURN_LEFT == lastDirection) {
                                dir = DIR_TURN_LEFT;
                            } else {
                                if (random.nextBoolean()) {
                                    dir = DIR_TURN_RIGHT;
                                } else {
                                    dir = DIR_TURN_LEFT;
                                }
                            }
                        }
                    }

                    break;

                case 1:  // Just starboard aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_FORWARD_RIGHT;
                            } else {
                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_VEER_BACKWARD_LEFT;
                            break;
                        //case DIR_TURN_LEFT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 2:  // Just port aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_FORWARD_LEFT;
                            } else {
                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                            dir = DIR_VEER_BACKWARD_RIGHT;
                            break;
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 3:  // Both aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_FORWARD;
                            } else {
                                // If not already turning, turn a random direction

                                if (DIR_TURN_RIGHT == lastDirection) {
                                    dir = DIR_TURN_RIGHT;
                                } else if (DIR_TURN_LEFT == lastDirection) {
                                    dir = DIR_TURN_LEFT;
                                } else {
                                    if (random.nextBoolean()) {
                                        dir = DIR_TURN_RIGHT;
                                    } else {
                                        dir = DIR_TURN_LEFT;
                                    }
                                }
                            }
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_BACKWARD;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 4:  // Just starboard fore
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_BACKWARD_RIGHT;
                            } else {
                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_VEER_FORWARD_LEFT;
                            break;
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 5:  // Both starboard
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                //// Move a random direction

                                dir = DIR_VEER_FORWARD_LEFT;
                            } else {
                                //// Turn a random direction

                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_VEER_FORWARD_LEFT;
                            break;
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_VEER_BACKWARD_LEFT;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 6:  // Starboard fore and port aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                //// Move a random direction

                                dir = DIR_FORWARD;
                            } else {
                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_VEER_FORWARD_LEFT;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                            dir = DIR_VEER_BACKWARD_RIGHT;
                            break;
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 7:  // Port aft and both starboard
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_FORWARD_LEFT;
                            } else {
                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_VEER_FORWARD_LEFT;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_BACKWARD;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 8:  // Just port fore
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_BACKWARD_LEFT;
                            } else {
                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                            dir = DIR_VEER_FORWARD_RIGHT;
                            break;
                        //case DIR_TURN_LEFT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 9:  // Port fore and starboard aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                //// Move a random direction

                                dir = DIR_FORWARD;
                            } else {
                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                            dir = DIR_VEER_FORWARD_RIGHT;
                            break;
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_VEER_BACKWARD_LEFT;
                            break;
                        //case DIR_TURN_LEFT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 10:  // Both port
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                //// Move a random direction

                                dir = DIR_VEER_FORWARD_RIGHT;
                            } else {
                                //// Turn a random direction

                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                            dir = DIR_VEER_FORWARD_RIGHT;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                            dir = DIR_VEER_BACKWARD_RIGHT;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 11:  // Port fore and both aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_FORWARD_RIGHT;
                            } else {
                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                            dir = DIR_VEER_FORWARD_RIGHT;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_BACKWARD;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 12:  // Both fore
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_BACKWARD;
                            } else {
                                // If not already turning, turn a random direction

                                if (DIR_TURN_RIGHT == lastDirection) {
                                    dir = DIR_TURN_RIGHT;
                                } else if (DIR_TURN_LEFT == lastDirection) {
                                    dir = DIR_TURN_LEFT;
                                } else {
                                    if (random.nextBoolean()) {
                                        dir = DIR_TURN_RIGHT;
                                    } else {
                                        dir = DIR_TURN_LEFT;
                                    }
                                }
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_FORWARD;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 13:  // Both fore and starboard aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_BACKWARD_LEFT;
                            } else {
                                dir = DIR_TURN_RIGHT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_FORWARD;
                            break;
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_VEER_BACKWARD_LEFT;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 14:  // Both fore and port aft
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                dir = DIR_VEER_BACKWARD_RIGHT;
                            } else {
                                dir = DIR_TURN_LEFT;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_FORWARD;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                            dir = DIR_VEER_BACKWARD_RIGHT;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
                case 15:  // All sensors
                    switch (dir) {
                        case DIR_STOP:
                            if (noCorners) {
                                //// Move a random direction

                                dir = DIR_FORWARD;
                            }
                            break;
                        case DIR_VEER_FORWARD_LEFT:  // Aft port
                        case DIR_VEER_FORWARD_RIGHT:  // Aft starboard
                            dir = DIR_FORWARD;
                            break;
                        case DIR_VEER_BACKWARD_LEFT:  // Fore port
                        case DIR_VEER_BACKWARD_RIGHT:  // Fore starboard
                            dir = DIR_BACKWARD;
                            break;
                        //case DIR_TURN_LEFT:
                        //case DIR_TURN_RIGHT:
                        //    dir = DIR_STOP;
                        //    break;
                    }
                    break;
            }
        }//useSideSensors == true

        // Examine the cliff sensors here because it can trump previous
        // decisions

        // Bit 15: Fore port cliff
        // Bit 14: Fore starboard cliff
        // Bit 13: Aft port cliff
        // Bit 12: Aft starboard cliff

        switch ((~sensorNodeState >> 12) & 0x000f) {  // Invert the bits (because 'on' means 'no cliff')
            case 1:  // Aft starboard cliff
            case 2:  // Aft port cliff
            case 3:  // Both aft cliff
                switch (dir) {
                    case DIR_STOP:
                        if (noCorners) {
                            dir = DIR_FORWARD;
                        }
                        break;
                    case DIR_BACKWARD:
                    case DIR_VEER_BACKWARD_LEFT:
                    case DIR_VEER_BACKWARD_RIGHT:
                    case DIR_TURN_LEFT:
                    case DIR_TURN_RIGHT:
                    default:  // The default case is here to be robust
                        dir = DIR_STOP;
                        break;
                }
                break;
            case 4:  // Fore starboard cliff
            case 8:  // Fore port cliff
            case 12:  // Both fore cliff
                switch (dir) {
                    case DIR_STOP:
                        if (noCorners) {
                            dir = DIR_BACKWARD;
                        }
                        break;
                    case DIR_FORWARD:
                    case DIR_VEER_FORWARD_LEFT:
                    case DIR_VEER_FORWARD_RIGHT:
                    case DIR_TURN_LEFT:
                    case DIR_TURN_RIGHT:
                    default:  // The default case is here to be robust
                        dir = DIR_STOP;
                        break;
                }
                break;
            case 0:  // No cliff sensors
                break;
            default:  // All other combinations
                dir = DIR_STOP;
        }

        Debug.finer("Avoider: RUNAWAY: " + DIR_NAMES[dir]);

        // Perform the motor command

        goDirection(dir);
    }

    /** The "MEDIUM" speed.*/
    protected static final int SPEED_MEDIUM = Motors.SPEED_MEDIUM;

    /** The "SLOW" speed. */
    protected static final int SPEED_SLOW = Motors.SPEED_SLOW;

    /** The "TURNING" speed. */
    protected static final int TURN_SPEED = Motors.SPEED_SLOW;

    /** The "FASTEST" speed. */
    protected static final int SPEED_FAST = Motors.SPEED_FAST;


    /**
     * Moves the motors in the given direction.  Please see the
     * <code>DIR_<em>XXX</em></code> constants.
     *
     * @param dir the direction
     */
     protected void goDirection(int dir) {
        switch (dir) {
            case DIR_STOP:
                motors.brake(Motors.MOTOR_ALL);
                break;
            case DIR_FORWARD:
                goForward(Motors.MOTOR_ALL, SPEED_MEDIUM);
                break;
            case DIR_BACKWARD:
                goReverse(Motors.MOTOR_ALL, SPEED_MEDIUM);
                break;
            case DIR_VEER_FORWARD_LEFT:
                goForward(Motors.MOTOR_PORT, SPEED_SLOW);
                goForward(Motors.MOTOR_STARBOARD, SPEED_MEDIUM);
                break;
            case DIR_VEER_FORWARD_RIGHT:
                goForward(Motors.MOTOR_PORT, SPEED_MEDIUM);
                goForward(Motors.MOTOR_STARBOARD, SPEED_SLOW);
                break;
            case DIR_VEER_BACKWARD_LEFT:
                goReverse(Motors.MOTOR_PORT, SPEED_SLOW);
                goReverse(Motors.MOTOR_STARBOARD, SPEED_MEDIUM);
                break;
            case DIR_VEER_BACKWARD_RIGHT:
                goReverse(Motors.MOTOR_PORT, SPEED_MEDIUM);
                goReverse(Motors.MOTOR_STARBOARD, SPEED_SLOW);
                break;
            case DIR_TURN_LEFT:
                goReverse(Motors.MOTOR_PORT, TURN_SPEED);
                goForward(Motors.MOTOR_STARBOARD, TURN_SPEED);
                break;
            case DIR_TURN_RIGHT:
                goForward(Motors.MOTOR_PORT, TURN_SPEED);
                goReverse(Motors.MOTOR_STARBOARD, TURN_SPEED);
                break;
        }

        lastDirection = dir;
     }



    /**
     * Moves the motors in the given direction and speed.  Please see the
     * <code>DIR_<em>XXX</em></code> constants.
     *
     * @param dir the direction
     */
     protected void goDirection(int dir, int speed) {
        switch (dir) {
            case DIR_STOP:
                motors.brake(Motors.MOTOR_ALL);
                break;
            case DIR_FORWARD:
                goForward(Motors.MOTOR_ALL, speed);
                break;
            case DIR_BACKWARD:
                goReverse(Motors.MOTOR_ALL, speed);
                break;
            case DIR_VEER_FORWARD_LEFT:
                goForward(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_FORWARD_RIGHT:
                goForward(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_BACKWARD_LEFT:
                goReverse(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_VEER_BACKWARD_RIGHT:
                goReverse(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_TURN_LEFT:
                goReverse(Motors.MOTOR_PORT, speed);
                goForward(Motors.MOTOR_STARBOARD, speed);
                break;
            case DIR_TURN_RIGHT:
                goForward(Motors.MOTOR_PORT, speed);
                goReverse(Motors.MOTOR_STARBOARD, speed);
                break;
        }

        lastDirection = dir;
     }
     

    /**
     * Moves the specified motor forward.  This first brakes the motor if we
     * are changing directions, or if we are slowing down in the same
     * direction.
     *
     * @param motor the motor to move forward
     * @param speed the motor speed
     */
    protected void goForward(int motor, int speed) {
        int lastDir = motors.getLastDirection(motor);
        int lastSpeed = motors.getLastSpeed(motor);

        // Brake if we are changing directions, or if we are slowing down
        // in the same direction

        if (Motors.DIR_REVERSE == lastDir
              || Motors.DIR_FORWARD == lastDir && speed < lastSpeed) {
            motors.brake(motor);
        }

        // Send a new command if we need to

        if (Motors.DIR_FORWARD != lastDir || speed != lastSpeed) {
            motors.goForward(motor, speed);
        }
    }

    /**
     * Moves the specified motor in reverse.  This first brakes the motor if
     * we are changind directions, or if we are slowing down in the same
     * direction.
     *
     * @param motor the motor to move in reverse
     * @param speed the motor speed
     */
    protected void goReverse(int motor, int speed) {
        int lastDir = motors.getLastDirection(motor);
        int lastSpeed = motors.getLastSpeed(motor);

        // Brake if we are changing directions, or if we are slowing down
        // in the same direction

        if (Motors.DIR_FORWARD == lastDir
              || Motors.DIR_REVERSE == lastDir && speed < lastSpeed) {
            motors.brake(motor);
        }

        // Send a new command if we need to

        if (Motors.DIR_REVERSE != lastDir || speed != lastSpeed) {
            motors.goReverse(motor, speed);
        }
    }

    /**
     * Listens to all the states.
     *
     * @param powerNodeState the power node state
     * @param sensorNodeState the sensor node state
     */
    public void allStates(int powerNodeState, int sensorNodeState, int beaconState[][], int currTrackBotID) {
//        if (powerNodeState != this.powerNodeState
//              || sensorNodeState != this.sensorNodeState) {

            this.powerNodeState = powerNodeState;
            this.sensorNodeState = sensorNodeState;

            stateMachine();
//        }
    }



    /**
     * Listens to the power node.
     *
     * @param state the power node state
     */
    public final void powerNodeState(int state) {
        if (state == powerNodeState) return;
        this.powerNodeState = state;

        stateMachine();
        /*// Notify the IR sensor waiters

        synchronized (irSensorLock) {
            irSensorLock.notifyAll();
        }*/
    }

    /**
     * Listens to the sensor node.
     *
     * @param state the sensor node state
     */
    public final void sensorNodeState(int state) {
        if (state == sensorNodeState) return;
        this.sensorNodeState = state;

        stateMachine();
        /*// Notify the IR sensor waiters

        synchronized (irSensorLock) {
            irSensorLock.notifyAll();
        }*/
    }

    public void testPointValue(int testPoint, boolean state) {
    }

    public void transducerStation(byte site, int left, int right, boolean pir) {
    }

    public void taggingMemory(byte[] data) {
    }

    public void robotSerialNumber(String serialNo) {
    }

    /**
     * Receives the robot version.  This method is called when the version is
     * requested.
     *
     * @param version the robot version information
     * @param supported indicates whether the API supports this robot version
     */
    public void robotVersion(VersionInfo version, boolean supported) {
        Debug.info("Robot version = " + version);
        if (!supported) {
            Debug.severe("Robot version is UNSUPPPORTED!");
            return;
        }

        // Query the serial number

        if (version.isSerialNumberSupported()) {
            sendSerialNumberQuery();
        }

        // Set up the sensors, if we have to

        if (version.isIREnableSupported()) {
            // Turn on the sensors

            enableCornerSensors(true, true, true, true);
            enableSideSensors(true, true, true, true);
            enableCliffSensors(true, true, true, true);
        }

        if (version.isRangingSupported()) {
            // Set all the sensors to short range

            setCornerSensorRange(2);
            setSideSensorRange(3);
            setCliffSensorRange(1);
        }

        if (version.isIRPingIntervalSupported()) {
            setCornerSensorPingInterval(25);
            setSideAndCliffSensorPingInterval(25);
        }
    }

    public void robotTimeout(boolean state, int ms) {
        // Send a version query if we've been reconnected to a robot
        // because it may be any robot

        if (!state) {
            sendVersionQuery();
        }
    }

    /**
     * Returns the name of this application.
     *
     * @return the name of this application.
     */
    public String toString() {
        return "Avoider";
    }


    /** Does nothing right now. */
    protected void checkBeacons() {

    }

}//Avoider
