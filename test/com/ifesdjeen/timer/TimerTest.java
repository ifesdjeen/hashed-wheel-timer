package com.ifesdjeen.timer;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TimerTest {

  @Test
  public void timerTest() throws InterruptedException {
    int period = 50;
    final HashWheelTimer timer = new HashWheelTimer(10, 8, new HashWheelTimer.SleepWait());
    final CountDownLatch latch = new CountDownLatch(10);

    timer.schedule(new Consumer<Long>() {
                     @Override
                     public void accept(Long aLong) {
                       latch.countDown();
                     }
                   }, period,
                   TimeUnit.MILLISECONDS,
                   period);
    latch.await(10, TimeUnit.SECONDS);
  }
}
