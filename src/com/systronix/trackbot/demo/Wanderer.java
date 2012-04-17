/*
 * Date: Oct 22, 2007
 * Time: 3:38:58 PM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.demo;

import com.systronix.io.Debug;
import com.systronix.trackbot.RobotIO;

/**
 * Program for the robot that wanders around and avoids obstacles.
 *
 * @author Shawn Silverman
 * @version 0.5
 */
public class Wanderer extends Avoider {
    /** The WANDER state. */
    protected static final int STATE_WANDER = Avoider.MAX_STATE + 1;

    /**
     * The maximum state value in this class.  Additional states in subclasses
     * or other places should start at <code>MAX_STATE&nbsp;+&nbsp;1</code>.
     */
    protected static final int MAX_STATE = STATE_WANDER;

    /**
     * The main entry point.
     *
     * @param args the program arguments
     * @throws Exception if there was an error while attempting to connect to
     *         the robot.
     * @throws Error if the no connection could be made to the robot.
     */
    public static void main(String[] args) throws Exception {
        Debug.info("Wanderer application starting.");

        // Start the Wanderer

        Wanderer wanderer = new Wanderer();
        //wanderer.stateMachine();
    }

    // Current data, modules, and state

    /**
     * Creates the Wanderer.
     *
     * @throws Exception if there was an error while trying to connect to the
     *         robot.
     */
    public Wanderer() throws Exception {
        super();
    }

    /**
     * Creates the Wanderer using the given I/O connetion.
     *
     * @param robotIO the robot I/O connection
     */
    public Wanderer(RobotIO robotIO) {
        super(robotIO);
    }

    protected void runState() {
        switch (state) {
            case STATE_WANDER:
                doWander();
                break;
            default:
                super.runState();
        }
    }

    protected void chooseNextState() {
        switch (state) {
            case STATE_STOPPED:
            case STATE_RUNAWAY:
                // Move to the WANDER state if there are no front sensors,
                // including front cliff sensors

                if ((powerNodeState & 0xc000) == 0
                      && (sensorNodeState & 0xf000) == 0xf000) {
                    this.state = STATE_WANDER;
                } else {
                    this.state = STATE_RUNAWAY;
                }
                break;

            case STATE_WANDER:
                // Move to the RUNAWAY state if there are any rear sensors
                // and at least one front sensor
                // or if there are any cliff sensors

                if (((powerNodeState & 0x3000) != 0 && (powerNodeState & 0xc000) != 0)
                      || (sensorNodeState & 0xf000) != 0xf000) {
                    this.state = STATE_RUNAWAY;
                }
                break;

            default:
                super.chooseNextState();
        }
    }

    /**
     * Behavior for the WANDER state.
     */
    protected void doWander() {
        int dir = DIR_FORWARD;

        // Examine the fore and aft sensors
        // Recall that chooseNextState() moves the state machine into the
        // WANDER state if there is nothing in front

        switch ((powerNodeState >> 12) & 0x000f) {
            case 4:  // Just fore starboard
                // If not already turning, turn left

                if (DIR_TURN_RIGHT == lastDirection
                      || DIR_VEER_BACKWARD_LEFT == lastDirection) {
                    dir = DIR_TURN_RIGHT;
                } else if (DIR_TURN_LEFT == lastDirection
                           || DIR_VEER_BACKWARD_RIGHT == lastDirection) {
                    dir = DIR_TURN_LEFT;
                } else {
                    dir = DIR_TURN_LEFT;
                }
                break;
            case 8:  // Just fore port
                // If not already turning, turn right

                if (DIR_TURN_RIGHT == lastDirection
                      || DIR_VEER_BACKWARD_LEFT == lastDirection) {
                    dir = DIR_TURN_RIGHT;
                } else if (DIR_TURN_LEFT == lastDirection
                           || DIR_VEER_BACKWARD_RIGHT == lastDirection) {
                    dir = DIR_TURN_LEFT;
                } else {
                    dir = DIR_TURN_RIGHT;
                }
                break;
            case 12:  // Both front sensors
                // If not already turning, turn a random direction

                if (DIR_TURN_RIGHT == lastDirection
                      || DIR_VEER_BACKWARD_LEFT == lastDirection) {
                    dir = DIR_TURN_RIGHT;
                } else if (DIR_TURN_LEFT == lastDirection
                           || DIR_VEER_BACKWARD_RIGHT == lastDirection) {
                    dir = DIR_TURN_LEFT;
                } else {
                    if (random.nextBoolean()) {
                        dir = DIR_TURN_RIGHT;
                    } else {
                        dir = DIR_TURN_LEFT;
                    }
                }
                break;
        }

        // Examine the side sensors

        switch ((sensorNodeState >> 8) & 0x000f) {
            case 1:  // Just starboard aft
            case 8:  // Just port fore
            case 9:  // Port fore and starboard aft
            case 10:  // Both port
            case 11:  // Port fore and both aft
                if (DIR_FORWARD == dir) {
                    // Keep going if we were veering backwards

                    switch (lastDirection) {
                        case DIR_VEER_BACKWARD_LEFT:
                        case DIR_VEER_BACKWARD_RIGHT:
                            dir = lastDirection;
                            break;
                        default:
                            dir = DIR_VEER_FORWARD_RIGHT;
                    }
                }
                break;
            case 2:  // Just port aft
            case 4:  // Just starboard fore
            case 5:  // Both starboard
            case 6:  // Starboard fore and port aft
            case 7:  // Port aft and both starboard
                if (DIR_FORWARD == dir) {
                    // Keep going if we were veering backwards

                    switch (lastDirection) {
                        case DIR_VEER_BACKWARD_LEFT:
                        case DIR_VEER_BACKWARD_RIGHT:
                            dir = lastDirection;
                            break;
                        default:
                            dir = DIR_VEER_FORWARD_LEFT;
                    }
                }
                break;

            case 3:  // Both aft
            case 12:  // Both fore
                if (DIR_TURN_LEFT == dir) {
                    dir = DIR_VEER_BACKWARD_RIGHT;
                } else if (DIR_TURN_RIGHT == dir) {
                    dir = DIR_VEER_BACKWARD_LEFT;
                }
                break;
            case 13:  // Starboard aft and both fore
                if (DIR_TURN_LEFT == dir || DIR_TURN_RIGHT == dir) {
                    dir = DIR_VEER_BACKWARD_LEFT;
                }
                break;
            case 14:  // Port aft and both fore
                if (DIR_TURN_LEFT == dir || DIR_TURN_RIGHT == dir) {
                    dir = DIR_VEER_BACKWARD_RIGHT;
                }
                break;
        }

        // Perform the motor command

        Debug.finer("Wanderer: WANDER: " + DIR_NAMES[dir]);

        goDirection(dir);
    }

    /**
     * Returns the name of this application.
     *
     * @return the name of this application.
     */
    public String toString() {
        return "Wanderer";
    }
}
