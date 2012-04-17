/*
 * Date: Dec 4, 2007
 * Time: 9:44:24 AM
 *
 * (c) 2007 Systronix Inc.  All Rights reserved.
 * 939 Edison Street, Salt Lake City, UT, USA  84111
 * http://www.systronix.com/
 */
package com.systronix.trackbot.demo;

import com.systronix.io.Debug;
import com.systronix.trackbot.RobotIO;

/**
 * Implements a wall following behaviour.  This extends the Wanderer behavior.
 *
 * @author Shawn Silverman
 * @version 0.0
 */
public class WallFollower extends Wanderer {
    /** The WANDER state. */
    protected static final int STATE_FOLLOW_WALL = Wanderer.MAX_STATE + 1;

    /**
     * The maximum state value in this class.  Additional states in subclasses
     * or other places should start at <code>MAX_STATE&nbsp;+&nbsp;1</code>.
     */
    protected static final int MAX_STATE = STATE_FOLLOW_WALL;

    /**
     * The main entry point.
     *
     * @param args the program arguments
     * @throws Exception if there was an error while attempting to connect to
     *         the robot.
     * @throws Error if the no connection could be made to the robot.
     */
    public static void main(String[] args) throws Exception {
        Debug.info("WallFollower application starting.");

        // Start the WallFollower

        WallFollower wallFollower = new WallFollower();
        //wallFollower.stateMachine();
    }

    /**
     * Creates the WallFollower.
     *
     * @throws Exception if there was an error while trying to connect to the
     *         robot.
     */
    public WallFollower() throws Exception {
        super();
    }

    /**
     * Creates the WallFollower using the given I/O connetion.
     *
     * @param robotIO the robot I/O connection
     */
    public WallFollower(RobotIO robotIO) {
        super(robotIO);
    }

    protected void runState() {
        switch (state) {
            case STATE_FOLLOW_WALL:
                doFollowWall();
                break;
            default:
                super.runState();
        }
    }

    protected void chooseNextState() {
        switch (state) {
            case STATE_WANDER:
            case STATE_FOLLOW_WALL:
                state = STATE_WANDER;
                super.chooseNextState();

                // Only move to the FOLLOW_WALL state if it's safe to be wandering

                if (STATE_WANDER == state) {
                    // Move to the FOLLOW_WALL state if there is something on the
                    // sides and nothing in front

                    if ((sensorNodeState & 0x0f00) != 0) {
                        this.state = STATE_FOLLOW_WALL;
                    }
                }
                break;

            default:
                super.chooseNextState();
        }
    }

    /**
     * Behavior for the FOLLOW_WALL state.
     */
    protected void doFollowWall() {
        int dir = DIR_FORWARD;

        // Examine the side sensors

        switch ((sensorNodeState >> 8) & 0x000f) {
            case 1:  // Just starboard aft
                dir = DIR_VEER_FORWARD_RIGHT;
                break;
            case 2:  // Just port aft
                dir = DIR_VEER_FORWARD_LEFT;
                break;
            case 3:  // Both aft
                // If not already turning, turn a random direction

                if (DIR_VEER_FORWARD_RIGHT == lastDirection) {
                    dir = DIR_VEER_FORWARD_RIGHT;
                } else if (DIR_VEER_FORWARD_LEFT == lastDirection) {
                    dir = DIR_VEER_FORWARD_LEFT;
                } else {
                    if (random.nextBoolean()) {
                        dir = DIR_VEER_FORWARD_RIGHT;
                    } else {
                        dir = DIR_VEER_FORWARD_LEFT;
                    }
                }
                break;
            }

        // Perform the motor command

        goDirection(dir);
    }

    /**
     * Returns the name of this application.
     *
     * @return the name of this application.
     */
    public String toString() {
        return "WallFollower";
    }
}
