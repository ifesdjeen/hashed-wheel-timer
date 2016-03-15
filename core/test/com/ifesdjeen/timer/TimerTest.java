package com.ifesdjeen.timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TimerTest {

  HashWheelTimer timer;

  @Before
  public void before() {
    timer = new HashWheelTimer(10, 8, new WaitStrategy.SleepWait());
  }

  @After
  public void after() throws InterruptedException {
    timer.shutdownNow();
    assertTrue(timer.awaitTermination(10, TimeUnit.SECONDS));
  }

  @Test
  public void scheduleOneShotRunnableTest() throws InterruptedException {
    AtomicInteger i = new AtomicInteger(1);
    timer.schedule(() -> {
                     i.decrementAndGet();
                   },
                   100,
                   TimeUnit.MILLISECONDS);

    Thread.sleep(300);
    assertThat(i.get(), is(0));
  }

  @Test
  public void testOneShotRunnableFuture() throws InterruptedException, TimeoutException, ExecutionException {
    AtomicInteger i = new AtomicInteger(1);
    long start = System.currentTimeMillis();
    assertNull(timer.schedule(() -> {
                                i.decrementAndGet();
                              },
                              100,
                              TimeUnit.MILLISECONDS)
                    .get(10, TimeUnit.SECONDS));
    long end = System.currentTimeMillis();
    assertTrue(end - start > 100);
  }

  @Test
  public void scheduleOneShotCallableTest() throws InterruptedException {
    AtomicInteger i = new AtomicInteger(1);
    timer.schedule(() -> {
                     i.decrementAndGet();
                     return "Hello";
                   },
                   100,
                   TimeUnit.MILLISECONDS);

    Thread.sleep(300);
    assertThat(i.get(), is(0));
  }

  @Test
  public void testOneShotCallableFuture() throws InterruptedException, TimeoutException, ExecutionException {
    AtomicInteger i = new AtomicInteger(1);
    long start = System.currentTimeMillis();
    assertThat(timer.schedule(() -> {
                                i.decrementAndGet();
                                return "Hello";
                              },
                              100,
                              TimeUnit.MILLISECONDS)
                    .get(10, TimeUnit.SECONDS),
               is("Hello"));
    long end = System.currentTimeMillis();
    assertTrue(end - start > 100);
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
                                                    1024,
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
      "Hash Wheel: Elapsed:" + (end - start) + ", Max Deviation: " + max + ", Mean Deviation: " + (sum / arr.length));
  }

  @Test
  public void jdkTimerTest() throws InterruptedException {
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);

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
