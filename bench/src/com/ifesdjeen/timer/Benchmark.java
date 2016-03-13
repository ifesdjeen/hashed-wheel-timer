package com.ifesdjeen.timer;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Thread)
public class Benchmark {

  @Param({"1000", "10000", "100000"})
  public int tasksToSubmit;

  private final int delay = 1000;

  @Setup
  public void setup() {

  }

  @org.openjdk.jmh.annotations.Benchmark
  public void testJdkTimer() {
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
    final CountDownLatch latch = new CountDownLatch(tasksToSubmit);
    for (int i = 0; i < tasksToSubmit; i++) {
      timer.schedule(() -> {
        latch.countDown();
      }, delay, TimeUnit.MILLISECONDS);
    }
    timer.shutdown();
  }

}
