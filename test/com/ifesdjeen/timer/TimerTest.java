package com.ifesdjeen.timer;

import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

public class TimerTest {


  @Test
  public void timerTest() throws InterruptedException {
    int period = 50;
    final HashWheelTimer timer = new HashWheelTimer(10, 8, new WaitStrategy.SleepWait());
    final CountDownLatch latch = new CountDownLatch(10);

    timer.schedule(() -> {
                     latch.countDown();
                   }, period,
                   TimeUnit.MILLISECONDS,
                   period);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

  }

  @Test
  public void reverseEngineering() throws InterruptedException, ExecutionException {
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(16);

    ScheduledFuture<?> f = timer.schedule(new Callable<Object>() {
      @Override
      public Object call() {
        return new HashMap<>();
      }
    }, 1, TimeUnit.SECONDS);

    Thread.sleep(2000);
    System.out.println(f.get());

  }

  @Test
  public void hashWheelTimerTest() throws InterruptedException {
    int submittedTasks = 1000000;
    int delay = 1000;

    final HashWheelTimer timer = new HashWheelTimer("timer",
                                                    10,
                                                    32768,
                                                    new WaitStrategy.BusySpinWait(),
                                                    Executors.newFixedThreadPool(16));

    final CountDownLatch latch = new CountDownLatch(submittedTasks);
    long[] arr = new long[submittedTasks];

    long start = System.currentTimeMillis();
    for (int i = 0; i < submittedTasks; i++) {
      final int idx = i;
      timer.schedule(() -> {
        arr[idx] = System.currentTimeMillis();
        latch.countDown();
      }, delay, delay, TimeUnit.MILLISECONDS);
      //      if (i % 1000 == 0) {
      //        //System.out.println(i);
      //        Thread.sleep(10);
      //      }
    }

    assertThat(latch.await(10, TimeUnit.SECONDS), is(true));

    long end = System.currentTimeMillis();
    long max = 0;
    long sum = 0;
    for (int i = 0; i < submittedTasks; i++) {
      long diff = end - arr[i];
      sum += diff;
      if (diff > max) {
        max = diff;
      }
    }
    //
    System.out.println(
      "Hash Wheel: Elapsed:" + (end - start) + ", Max Deviation: " + max + ", Mean Deviation: " + (sum / arr.length));
  }

  @Test
  public void jdkTimerTest() throws InterruptedException {
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(16);

    int submittedTasks = 1000000;
    int delay = 1000;

    final CountDownLatch latch = new CountDownLatch(submittedTasks);
    long[] arr = new long[submittedTasks];

    long start = System.currentTimeMillis();
    for (int i = 0; i < submittedTasks; i++) {
      final int idx = i;
      timer.schedule(() -> {
        arr[idx] = System.currentTimeMillis();
        latch.countDown();
      }, delay, TimeUnit.MILLISECONDS);
      //      if (i % 1000 == 0) {
      //        //System.out.println(i);
      //        Thread.sleep(10);
      //      }
    }

    assertThat(latch.await(10, TimeUnit.SECONDS), is(true));

    long end = System.currentTimeMillis();
    long max = 0;
    long sum = 0;
    for (int i = 0; i < submittedTasks; i++) {
      long diff = end - arr[i];
      sum += diff;
      if (diff > max) {
        max = diff;
      }
    }
    //
    System.out.println(
      "JDK Timer: Elapsed:" + (end - start) + ", Max Deviation: " + max + ", Mean Deviation: " + (sum / arr.length));
  }
}
