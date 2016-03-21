package com.ifesdjeen.timer;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class AbstractBenchmark {

  protected ScheduledExecutorService timer;

  @Param({"100"})
  public int delay;

  @Param({"1000", "5000", "10000", "20000", "30000", "40000", "50000"})
  public int times;

  private final AtomicInteger counterDown = new AtomicInteger();

  @Setup
  public abstract void setup();

  @TearDown
  public abstract void teardown() throws InterruptedException;

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

  @Benchmark
  public void multiTimerTest(Control ctrl) throws InterruptedException {
    int threads = 10;
    counterDown.set(times * threads);
    for (int i = 0; i < threads; i++) {
      final int idx = i;
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            Thread.sleep(10 * idx);
          } catch (InterruptedException e) {
          }
          for (int j = 0; j < times; j++) {
            timer.schedule(new Runnable() {
                             @Override
                             public void run() {
                               counterDown.decrementAndGet();
                             }
                           },
                           delay,
                           TimeUnit.MILLISECONDS);
          }
        }
      }).start();
    }

    while (!ctrl.stopMeasurement && counterDown.get() > 0) {
      // spin
    }

  }


}
