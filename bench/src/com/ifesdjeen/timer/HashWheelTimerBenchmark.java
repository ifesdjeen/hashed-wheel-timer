package com.ifesdjeen.timer;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public class HashWheelTimerBenchmark {

  HashWheelTimer timer;

  @Param({"100"})
  public int delay;

  @Param({"100000"})
  public int times;

  private final AtomicInteger counterDown = new AtomicInteger();

  @Setup
  public void setup() {
    timer = new HashWheelTimer("hash-wheel-timer",
                               (int)TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS),
                               1024,
                               new WaitStrategy.BusySpinWait(),
                               Executors.newFixedThreadPool(8));
  }

  @TearDown
  public void teardown() throws InterruptedException {
    timer.shutdownNow();
    timer.awaitTermination(10, TimeUnit.SECONDS);
  }

  @Benchmark
  public void timerThroughputTest(Control ctrl) throws InterruptedException {
    counterDown.set(times);
    for (int i = 0; i < times; i++) {
      timer.schedule(new Runnable() {
                       @Override
                       public void run() {
                         counterDown.decrementAndGet();
                       }
                     },
                     delay,
                     TimeUnit.MILLISECONDS);
    }

    while (!ctrl.stopMeasurement && counterDown.get() > 0) {
      // spin
    }

  }


}
