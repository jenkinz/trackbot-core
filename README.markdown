# TrackBot Core API

The TrackBot is an autonomous robot developed for education by Systronix Inc. ([http://www.systronix.com/trackbot/](http://www.systronix.com/trackbot/)). It is unique in that that barrier to entry for writing application-level code for the TrackBot is relatively low, since the entire application "brain" is implemented in Java. On a physical TrackBot, the brain is the ([SunSPOT](http://www.sunspotworld.com)). There is also a simulation available for Greenfoot.

The TrackBot Core API provides the Java basis for writing such application-level behaviors. It consists of the I/O subsystem necessary for communication with the robot, and presents an event listener-based API for behaviors to use. There are several demo behaviors implemented in `com.systronix.trackbot.demo`, with `Avoider.java` being the fundamental behavior (avoiding objects) that others extend from.

# Instructions

- [Download](http://www.sunspotworld.com) and install the SunSPOT SDK (the latest tested working version of the SDK is "Yellow v6.0"). This is required to compile the project, since it compiles against the [Java ME](http://en.wikipedia.org/wiki/Java_ME) API provided by the [Squawk](http://en.wikipedia.org/wiki/Squawk_virtual_machine) JVM in the SDK.

- To compile, invoke the default ant build target which compiles and builds the JAR library by running `ant` inside `trackbot-core` once you have cloned the repository.
