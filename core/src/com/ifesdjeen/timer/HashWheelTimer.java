/*
 * ORIGINAL IMPL IS FROM PROJECT REACTOR!
 *
 * But I'm also an original author.
 *
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ifesdjeen.timer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Hash Wheel Timer, as per the paper:
 * <p/>
 * Hashed and hierarchical timing wheels:
 * http://www.cs.columbia.edu/~nahum/w6998/papers/ton97-timing-wheels.pdf
 * <p/>
 * More comprehensive slides, explaining the paper can be found here:
 * http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt
 * <p/>
 * Hash Wheel timer is an approximated timer that allows performant execution of
 * larger amount of tasks with better performance compared to traditional scheduling.
 *
 * @author Oleksandr Petrov
 */
public class HashWheelTimer implements ScheduledExecutorService {

  public static final  int    DEFAULT_RESOLUTION = (int) TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS);
  public static final  int    DEFAULT_WHEEL_SIZE = 512;
  private static final String DEFAULT_TIMER_NAME = "hash-wheel-timer";

  private final Set<Registration<?>>[] wheel;
  private final int                    wheelSize;
  private final int                    resolution;
  private final ExecutorService        loop;
  private final ExecutorService        executor;
  private final WaitStrategy           waitStrategy;

  private volatile int cursor = 0;

  /**
   * Create a new {@code HashWheelTimer} using the given with default resolution of 10 MILLISECONDS and
   * default wheel size.
   */
  public HashWheelTimer() {
    this(DEFAULT_RESOLUTION, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer resolution. All times will rounded up to the closest
   * multiple of this resolution.
   *
   * @param resolution the resolution of this timer, in NANOSECONDS
   */
  public HashWheelTimer(int resolution) {
    this(resolution, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer {@data resolution} and {@data wheelSize}. All times will
   * rounded up to the closest multiple of this resolution.
   *
   * @param res          resolution of this timer in NANOSECONDS
   * @param wheelSize    size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
   *                     for sparse timeouts. Sane default is 512.
   * @param waitStrategy strategy for waiting for the next tick
   */
  public HashWheelTimer(int res, int wheelSize, WaitStrategy waitStrategy) {
    this(DEFAULT_TIMER_NAME, res, wheelSize, waitStrategy, Executors.newFixedThreadPool(1));
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer {@data resolution} and {@data wheelSize}. All times will
   * rounded up to the closest multiple of this resolution.
   *
   * @param name      name for daemon thread factory to be displayed
   * @param res       resolution of this timer in NANOSECONDS
   * @param wheelSize size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
   *                  for sparse timeouts. Sane default is 512.
   * @param strategy  strategy for waiting for the next tick
   * @param exec      Executor instance to submit tasks to
   */
  public HashWheelTimer(String name, int res, int wheelSize, WaitStrategy strategy, ExecutorService exec) {
    this.waitStrategy = strategy;

    this.wheel = new Set[wheelSize];
    for (int i = 0; i < wheelSize; i++) {
      wheel[i] = new ConcurrentSkipListSet<>();
    }

    this.wheelSize = wheelSize;

    this.resolution = res;
    final Runnable loopRunnable = new Runnable() {
      @Override
      public void run() {
        long deadline = System.nanoTime();

        while (true) {
          // TODO: consider extracting processing until deadline for test purposes
          Set<Registration<?>> registrations = wheel[cursor];

          for (Registration r : registrations) {
            if (r.isCancelled()) {
              registrations.remove(r);
            } else if (r.ready()) {
              executor.execute(r);
              registrations.remove(r);

              if (!r.isCancelAfterUse()) {
                reschedule(r);
              }
            } else {
              r.decrement();
            }
          }

          deadline += resolution;

          try {
            waitStrategy.waitUntil(deadline);
          } catch (InterruptedException e) {
            return;
          }

          cursor = (cursor + 1) % wheelSize;
        }
      }
    };

    this.loop = Executors.newSingleThreadExecutor(new ThreadFactory() {
      AtomicInteger i = new AtomicInteger();

      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, name + "-" + i.getAndIncrement());
        thread.setDaemon(true);
        return thread;
      }
    });
    this.loop.submit(loopRunnable);
    this.executor = exec;
  }

  @Override
  public ScheduledFuture<?> submit(Runnable runnable) {
    return scheduleOneShot(resolution, constantlyNull(runnable));
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable runnable,
                                     long period,
                                     TimeUnit timeUnit) {
    return scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                           constantlyNull(runnable));
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long period, TimeUnit timeUnit) {
    return scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                           callable);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
    return scheduleFixedRate(TimeUnit.NANOSECONDS.convert(period, unit),
                             TimeUnit.NANOSECONDS.convert(initialDelay, unit),
                             constantlyNull(runnable));
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
    return scheduleFixedDelay(TimeUnit.NANOSECONDS.convert(delay, unit),
                              TimeUnit.NANOSECONDS.convert(initialDelay, unit),
                              constantlyNull(runnable));
  }

  private <V> Registration<V> scheduleOneShot(long firstDelay,
                                              Callable<V> callable) {
    isTrue(firstDelay >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");
    int firstFireOffset = (int) firstDelay / resolution;
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new OneShotRegistration<V>(firstFireRounds, callable, firstDelay);
    wheel[idx(cursor + firstFireOffset)].add(r);
    return r;
  }

  private int idx(int cursor) {
    return cursor % wheelSize;
  }

  private <V> Registration<V> scheduleFixedRate(long recurringTimeout,
                                                long firstDelay,
                                                Callable<V> callable) {
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    int offset = (int) recurringTimeout / resolution;
    int rounds = offset / wheelSize;

    int firstFireOffset = (int) firstDelay / resolution;
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new FixedRateRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset);
    wheel[idx(cursor + firstFireOffset)].add(r);
    return r;
  }

  private <V> Registration<V> scheduleFixedDelay(long recurringTimeout,
                                                 long firstDelay,
                                                 Callable<V> callable) {
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    int offset = (int) recurringTimeout / resolution;
    int rounds = offset / wheelSize;

    int firstFireOffset = (int) firstDelay / resolution;
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new FixedDelayRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset,
                                                     this::rescheduleForward);
    wheel[idx(cursor + firstFireOffset)].add(r);
    return r;
  }

  /**
   * Rechedule a {@link Registration} for the next fire
   *
   * @param registration
   */
  private void reschedule(Registration<?> registration) {
    registration.reset();
    wheel[idx(cursor + registration.getOffset())].add(registration);
  }

  private void rescheduleForward(Registration<?> registration) {
    registration.reset();
    wheel[idx(cursor + registration.getOffset() + 1)].add(registration);
  }

  @Override
  public String toString() {
    return String.format("HashWheelTimer { Buffer Size: %d, Resolution: %d }",
                         wheelSize,
                         resolution);
  }

  /**
   * Executor Delegates
   */

  @Override
  public void execute(Runnable command) {
    executor.execute(command);
  }


  public static void isTrue(boolean expression, String message) {
    if (!expression) {
      throw new IllegalArgumentException(message);
    }
  }

  @Override
  public void shutdown() {
    this.loop.shutdown();
    this.executor.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    this.loop.shutdownNow();
    return this.executor.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return this.loop.isShutdown() && this.executor.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return this.loop.isTerminated() && this.executor.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return this.loop.awaitTermination(timeout, unit) && this.executor.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return this.executor.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return this.executor.submit(task, result);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return this.executor.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
                                       TimeUnit unit) throws InterruptedException {
    return this.executor.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return this.executor.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
                         TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return this.executor.invokeAny(tasks, timeout, unit);
  }

  public Runnable debounce(Runnable delegate) {
    return new Runnable() {
      @Override
      public void run() {

      }
    };
  }

  public <T> Consumer<T> debounce(Consumer<T> delegate) {
    return new Consumer<T>() {
      @Override
      public void accept(T t) {

      }
    };
  }

  public <T, V> Function<T, Future<V>> debounce(Function<T, V> debounce) {
    CompletableFuture<V> future = new CompletableFuture<>();
    Consumer<T> consumer = new Consumer<T>() {
      @Override
      public void accept(T t) {

      }
    };
    return new Function<T, Future<V>>() {
      @Override
      public Future<V> apply(T t) {

        return future;
      }
    };
  }

  private static Callable<?> constantlyNull(Runnable r) {
    return () -> {
      r.run();
      return null;
    };
  }

}

