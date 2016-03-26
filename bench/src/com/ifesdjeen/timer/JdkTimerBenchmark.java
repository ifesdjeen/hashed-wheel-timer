package com.ifesdjeen.timer;

import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JdkTimerBenchmark extends AbstractBenchmark {

  @Setup
  public void setup() {
    timer = Executors.newScheduledThreadPool(8);
  }

  @TearDown
  public void teardown() throws InterruptedException {
    timer.shutdown();
    timer.awaitTermination(10, TimeUnit.SECONDS);
  }

}
