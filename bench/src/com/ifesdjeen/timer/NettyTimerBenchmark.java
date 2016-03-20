package com.ifesdjeen.timer;

import io.netty.util.HashedWheelTimer;
import io.netty.util.TimerTask;
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
public class NettyTimerBenchmark {

  HashedWheelTimer timer;

  @Param({"100"})
  public int delay;

  @Param({"100000"})
  public int times;

  private final AtomicInteger counterDown = new AtomicInteger();

  @Setup
  public void setup() {
    timer = new HashedWheelTimer(10L, TimeUnit.MILLISECONDS);
    timer.start();
  }

  @TearDown
  public void teardown() throws InterruptedException {
    timer.stop();
  }

  //@Benchmark
  public void timerThroughputTest(Control ctrl) throws InterruptedException {
    counterDown.set(times);
    for (int i = 0; i < times; i++) {
      timer.newTimeout((TimerTask) (v) -> counterDown.decrementAndGet(),
                       delay,
                       TimeUnit.MILLISECONDS);
    }

    while (!ctrl.stopMeasurement && counterDown.get() > 0) {
      // spin
    }

  }


}
