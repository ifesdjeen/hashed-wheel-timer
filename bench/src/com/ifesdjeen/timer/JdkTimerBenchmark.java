package com.ifesdjeen.timer;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Control;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@Threads(1 )
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public class JdkTimerBenchmark {

  ScheduledExecutorService timer;

  @Param({"100"})
  public int delay;

  @Param({"10000"})
  public int times;

  @Setup
  public void setup() {
    timer = Executors.newSingleThreadScheduledExecutor();
  }

  @TearDown
  public void teardown() throws InterruptedException {
    timer.shutdown();
    timer.awaitTermination(10, TimeUnit.SECONDS);
  }

  @Benchmark
  public void in(Control ctrl) throws InterruptedException {
    AtomicInteger counterDown = new AtomicInteger(times);
    for (int i = 0; i < times; i++) {
      timer.schedule(() -> {
                       counterDown.decrementAndGet();
                     }
        , delay, TimeUnit.MILLISECONDS);
    }

    while (!ctrl.stopMeasurement && counterDown.get() != 0) {
      // spin
    }
  }


}
