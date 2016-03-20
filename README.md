# Ticky Tacky

> Little boxes on the hillside,
> Little boxes made of ticky tacky,
> Little boxes on the hillside,
> Little boxes all the same
> - Malvina Meynolds

# What is the Timer Wheel?

Hashed and Hierarchical Wheels were used as a base for Kernels and Network stacks, and
were described by the [freebsd](http://people.freebsd.org/~davide/asia/callout_paper.pdf),
[linux people](http://lwn.net/Articles/156329/),
[researchers](http://www.cs.columbia.edu/~nahum/w6998/papers/ton97-timing-wheels.pdf) and
in many other searches.

Many modern Java frameworks have their own implementations of Timing Wheels, for example,
[Netty](https://github.com/netty/netty/blob/4.1/common/src/main/java/io/netty/util/HashedWheelTimer.java),
[Agrona](https://github.com/real-logic/Agrona/blob/master/src/main/java/uk/co/real_logic/agrona/TimerWheel.java),
[Reactor](https://github.com/reactor/reactor-core/blob/master/src/main/java/reactor/core/timer/HashWheelTimer.java),
[Kafka](https://github.com/apache/kafka/blob/trunk/core/src/main/scala/kafka/utils/timer/Timer.scala)
and many others. Of course, every implementation is adapted for the needs of the particular
framework.

The concept on the Timer Wheel is rather simple to understand: in order to keep track
of events on given resolution, an array of linked lists (alternatively - sets or even
arrays, YMMV) is preallocated. When event is scheduled, it's address is found by
dividing deadline time `t` by `resolution` and `wheel size`.

This implementation was contributed to Reactor in [2014](https://github.com/reactor/reactor/commit/53c0dcfab40b91838694843729c85c2effe7272b),
and now is extracted and adopted to be used as a standalone library with benchmarks,
`debounce`, `throttle` implementations, `ScheduledExecutorService` impl and
other bells and whistles.

# JDK Timers

JDK Timers are great for the majority of cases. Benchmarks show that they're working
stably for "reasonable" amounts of events (tens of thousands).

TODO: post the perf degradation charts

## License

Copyright © 2016 Alex P

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
