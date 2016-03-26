package com.ifesdjeen.timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public abstract class AbstractTimerTest {

  HashedWheelTimer timer;

  public abstract WaitStrategy waitStrategy();

  @Before
  public void before() {
    // TODO: run tests on different sequences
    timer = new HashedWheelTimer(TimeUnit.MILLISECONDS.toNanos(10),
                                 8,
                                 waitStrategy());
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
    assertTrue(end - start >= 100);
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
    assertTrue(end - start >= 100);
  }

  @Test
  public void fixedRateFirstFireTest() throws InterruptedException, TimeoutException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(1);
    long start = System.currentTimeMillis();
    timer.scheduleAtFixedRate(() -> {
                                latch.countDown();
                              },
                              100,
                              100,
                              TimeUnit.MILLISECONDS);
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    long end = System.currentTimeMillis();
    assertTrue(end - start >= 100);
  }

  @Test
  public void delayBetweenFixedRateEvents() throws InterruptedException, TimeoutException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(2);
    List<Long> r = new ArrayList<>();
    timer.scheduleAtFixedRate(() -> {

                                r.add(System.currentTimeMillis());

                                latch.countDown();

                                if (latch.getCount() == 0)
                                  return; // to avoid sleep interruptions

                                try {
                                  Thread.sleep(50);
                                } catch (InterruptedException e) {
                                  e.printStackTrace();
                                }

                                r.add(System.currentTimeMillis());
                              },
                              100,
                              100,
                              TimeUnit.MILLISECONDS);
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    // time difference between the beginning of second tick and end of first one
    assertTrue(r.get(2) - r.get(1) <= (50 * 100)); // allow it to wiggle
  }

  @Test
  public void delayBetweenFixedDelayEvents() throws InterruptedException, TimeoutException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(2);
    List<Long> r = new ArrayList<>();
    timer.scheduleWithFixedDelay(() -> {

                                   r.add(System.currentTimeMillis());

                                   latch.countDown();

                                   if (latch.getCount() == 0)
                                     return; // to avoid sleep interruptions

                                   try {
                                     Thread.sleep(50);
                                   } catch (InterruptedException e) {
                                     e.printStackTrace();
                                   }

                                   r.add(System.currentTimeMillis());
                                 },
                                 100,
                                 100,
                                 TimeUnit.MILLISECONDS);
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    // time difference between the beginning of second tick and end of first one
    assertTrue(r.get(2) - r.get(1) >= 100);
  }

  @Test
  public void fixedRateSubsequentFireTest() throws InterruptedException, TimeoutException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(10);
    long start = System.currentTimeMillis();
    timer.scheduleAtFixedRate(() -> {
                                latch.countDown();
                                //thre
                              },
                              100,
                              100,
                              TimeUnit.MILLISECONDS);
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    long end = System.currentTimeMillis();
    assertTrue(end - start >= 1000);
  }

  // TODO: precision test
  // capture deadline and check the deviation from the deadline for different amounts of tasks

  // DISCLAIMER:
  // THE FOLLOWING TESTS WERE PORTED FROM NETTY. BIG PROPS TO NETTY AUTHORS FOR THEM.

  @Test
  public void testScheduleTimeoutShouldNotRunBeforeDelay() throws InterruptedException {
    final CountDownLatch barrier = new CountDownLatch(1);
    final Future timeout = timer.schedule(() -> {
      fail("This should not have run");
      barrier.countDown();
    }, 10, TimeUnit.SECONDS);
    assertFalse(barrier.await(3, TimeUnit.SECONDS));
    assertFalse("timer should not expire", timeout.isDone());
    // timeout.cancel(true);
  }

  @Test
  public void testScheduleTimeoutShouldRunAfterDelay() throws InterruptedException {
    final CountDownLatch barrier = new CountDownLatch(1);
    final Future timeout = timer.schedule(() -> {
      barrier.countDown();
    }, 2, TimeUnit.SECONDS);
    assertTrue(barrier.await(3, TimeUnit.SECONDS));
    assertTrue("timer should expire", timeout.isDone());
  }

  @Test(expected = IllegalStateException.class)
  public void testTimerShouldThrowExceptionAfterShutdownForNewTimeouts() throws InterruptedException {
    for (int i = 0; i < 3; i++) {
      timer.schedule(() -> {
      }, 10, TimeUnit.MILLISECONDS);
    }

    timer.shutdown();
    Thread.sleep(1000L); // sleep for a second

    timer.schedule(() -> {
      fail("This should not run");
    }, 1, TimeUnit.SECONDS);
  }

  @Test
  public void testTimerOverflowWheelLength() throws InterruptedException {
    final AtomicInteger counter = new AtomicInteger();

    timer.schedule(new Runnable() {
      @Override
      public void run() {
        counter.incrementAndGet();
        timer.schedule(this, 1, TimeUnit.SECONDS);
      }
    }, 1, TimeUnit.SECONDS);
    Thread.sleep(3500);
    assertEquals(3, counter.get());
  }

  @Test
  public void testExecutionOnTime() throws InterruptedException {
    int tickDuration = 200;
    int timeout = 130;
    int maxTimeout = 2 * (tickDuration + timeout);
    final BlockingQueue<Long> queue = new LinkedBlockingQueue<Long>();

    int scheduledTasks = 100000;
    for (int i = 0; i < scheduledTasks; i++) {
      final long start = System.nanoTime();
      timer.schedule(() -> {
        queue.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      }, timeout, TimeUnit.MILLISECONDS);
    }

    for (int i = 0; i < scheduledTasks; i++) {
      long delay = queue.take();
      assertTrue("Timeout + " + scheduledTasks + " delay must be " + timeout + " < " + delay + " < " + maxTimeout,
                 delay >= timeout && delay < maxTimeout);
    }
  }

  // Debounce tests

  @Test
  public void debounceTest() throws InterruptedException {
    AtomicReference ref = new AtomicReference(null);

    Consumer<String> debounced = timer.debounce(new Consumer<String>() {
      @Override
      public void accept(String s) {
        ref.set(s);
        assertThat(s, is("g'suffa"));
      }
    }, 500, TimeUnit.MILLISECONDS);

    for (int i = 0; i < 3; i++) {
      ref.set("wat");
      long start = System.currentTimeMillis();
      debounced.accept("oanz");
      Thread.sleep(100);
      debounced.accept("zwoa");
      Thread.sleep(100);
      debounced.accept("g'suffa");

      assertThat(ref.get(), is("wat"));
      assertTrue(waitFor(ref, "g'suffa", 10, TimeUnit.SECONDS));
      Thread.sleep(1000);
      assertThat(ref.get(), is("g'suffa"));
      long end = System.currentTimeMillis();

      assertTrue(end - start >= 700);
    }
  }

  private boolean waitFor(Supplier<Boolean> condition,
                          long timeout, TimeUnit timeUnit) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
    while (!condition.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(1);
    }
    return condition.get();
  }

  private <T> boolean waitFor(AtomicReference<T> v, T expected,
                              long timeout, TimeUnit timeUnit) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
    while (!expected.equals(v.get()) && System.currentTimeMillis() < deadline) {
      Thread.sleep(1);
    }
    return expected.equals(v.get());
  }

  @Test
  public void singleDebounceTest() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Consumer<String> debounced = timer.debounce(new Consumer<String>() {
      @Override
      public void accept(String s) {
        latch.countDown();
        assertThat(s, is("oanz"));
      }
    }, 500, TimeUnit.MILLISECONDS);

    long start = System.currentTimeMillis();
    debounced.accept("oanz");

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    long end = System.currentTimeMillis();

    assertTrue(end - start >= 500);
  }

  @Test
  public void runnableDebounceTest() throws InterruptedException {
    AtomicInteger ref = new AtomicInteger(0);

    Runnable debounced = timer.debounce(() -> {
      ref.incrementAndGet();
    }, 500, TimeUnit.MILLISECONDS);

    for (int i = 0; i < 3; i++) {
      ref.set(0);
      long start = System.currentTimeMillis();
      debounced.run();
      Thread.sleep(100);
      debounced.run();
      Thread.sleep(100);
      debounced.run();

      assertThat(ref.get(), is(0)); // isn't set immediately
      assertTrue(waitFor(() -> ref.get() == 1, 10, TimeUnit.SECONDS));
      assertThat(ref.get(), is(1)); // set after debounce
      long end = System.currentTimeMillis();
      long diff = end - start;
      assertTrue(diff >= 700 && diff <= 800);

      Thread.sleep(1000);
      assertThat(ref.get(), is(1)); // not changed after that
    }
  }

  // throttle test

  @Test
  public void throttleTest() throws InterruptedException {
    AtomicInteger ref = new AtomicInteger(0);

    Runnable throttled = timer.throttle(ref::incrementAndGet,
                                        500, TimeUnit.MILLISECONDS);

    for (int i = 0; i < 3; i++) {
      ref.set(0);
      long start = System.currentTimeMillis();
      throttled.run();
      Thread.sleep(100);
      throttled.run();
      Thread.sleep(100);
      throttled.run();

      assertThat(ref.get(), is(0));

      assertTrue(waitFor(() -> ref.get() == 1, 10, TimeUnit.SECONDS));
      long end = System.currentTimeMillis();
      long diff = end - start;
      assertTrue(diff >= 500 && diff <= 600); // wiggle

      assertThat(ref.get(), is(1));
      Thread.sleep(1000);
      assertThat(ref.get(), is(1));
    }
  }

  @Test
  public void throttleConsumerTest() throws InterruptedException {
    AtomicInteger ref = new AtomicInteger(0);

    Consumer<Integer> throttled = timer.throttle(new Consumer<Integer>() {
                                          @Override
                                          public void accept(Integer t) {
                                            ref.addAndGet(t);
                                          }
                                        },
                                        500, TimeUnit.MILLISECONDS);

    for (int i = 0; i < 3; i++) {
      ref.set(0);
      long start = System.currentTimeMillis();
      throttled.accept(3);
      Thread.sleep(100);
      throttled.accept(5);
      Thread.sleep(100);
      throttled.accept(7);

      assertThat(ref.get(), is(0));

      assertTrue(waitFor(() -> ref.get() == 7, 10, TimeUnit.SECONDS));
      long end = System.currentTimeMillis();
      long diff = end - start;
      assertTrue(diff >= 500 && diff <= 600); // wiggle

      assertThat(ref.get(), is(7));
      Thread.sleep(1000);
      assertThat(ref.get(), is(7));
    }
  }
}
