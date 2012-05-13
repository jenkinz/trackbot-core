# TrackBot Core API

The TrackBot is an autonomous robot developed for education by Systronix Inc. ([*http://www.systronix.com/trackbot/*](http://www.systronix.com/trackbot/)). It is unique in that that barrier to entry for writing application-level code for the TrackBot is relatively low, since the entire "brain" is implemented in Java. On a physical TrackBot, this "brain" is the SunSPOT ([*http://www.sunspotworld.com*](http://www.sunspotworld.com)). There is also a simulation available for Greenfoot.

The TrackBot Core API provides the Java basis for writing such application-level behaviors. It consists of the I/O subsystem necessary for communication with the underlying Robot, and presents an event listener-based API for behaviors to use. There are several demo behaviors implemented in `com.systronix.trackbot.demo`, with `Avoider.java` being the fundamental behavior (avoiding objects) that others extend from.
