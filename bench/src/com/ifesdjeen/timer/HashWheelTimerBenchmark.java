package com.ifesdjeen.timer;

import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HashWheelTimerBenchmark extends AbstractBenchmark {

  @Setup
  public void setup() {
    timer = new HashedWheelTimer("hashed-wheel-timer",
                                 (int) TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS),
                                 1024,
                                 new WaitStrategy.BusySpinWait(),
                                 Executors.newFixedThreadPool(8));
  }

  @TearDown
  public void teardown() throws InterruptedException {
    timer.shutdownNow();
    timer.awaitTermination(10, TimeUnit.SECONDS);
  }

}
