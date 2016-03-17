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

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.RingBuffer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

  public static final  int    DEFAULT_RESOLUTION = 100;
  public static final  int    DEFAULT_WHEEL_SIZE = 512;
  private static final String DEFAULT_TIMER_NAME = "hash-wheel-timer";

  private final RingBuffer<Set<Registration<?>>> wheel;
  private final int                              resolution;
  private final ExecutorService                  loop;
  private final ExecutorService                  executor;
  private final WaitStrategy                     waitStrategy;

  /**
   * Create a new {@code HashWheelTimer} using the given with default resolution of 100 milliseconds and
   * default wheel size.
   */
  public HashWheelTimer() {
    this(DEFAULT_RESOLUTION, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer resolution. All times will rounded up to the closest
   * multiple of this resolution.
   *
   * @param resolution the resolution of this timer, in milliseconds
   */
  public HashWheelTimer(int resolution) {
    this(resolution, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer {@data resolution} and {@data wheelSize}. All times will
   * rounded up to the closest multiple of this resolution.
   *
   * @param res          resolution of this timer in milliseconds
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
   * @param res       resolution of this timer in milliseconds
   * @param wheelSize size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
   *                  for sparse timeouts. Sane default is 512.
   * @param strategy  strategy for waiting for the next tick
   * @param exec      Executor instance to submit tasks to
   */
  public HashWheelTimer(String name, int res, int wheelSize, WaitStrategy strategy, ExecutorService exec) {
    this.waitStrategy = strategy;

    this.wheel = RingBuffer.createSingleProducer(new EventFactory<Set<Registration<?>>>() {
      @Override
      public Set<Registration<?>> newInstance() {
        return new ConcurrentSkipListSet<Registration<?>>();
      }
    }, wheelSize);

    this.resolution = res;
    final Runnable loopRunnable = new Runnable() {
      @Override
      public void run() {
        long deadline = System.currentTimeMillis();

        while (true) {
          Set<Registration<?>> registrations = wheel.get(wheel.getCursor());

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

          wheel.publish(wheel.next());
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

  //  public TimerRegistration schedule(Runnable runnable,
  //                                    long period,
  //                                    TimeUnit timeUnit,
  //                                    long delayInMilliseconds) {
  //    isTrue(!loop.isTerminated(), "Cannot submit tasks to this timer as it has been cancelled.");
  //    return schedule(TimeUnit.MILLISECONDS.convert(period, timeUnit), delayInMilliseconds, runnable);
  //  }
  //
  //  private TimerRegistration submit(Runnable runnable,
  //                                  long period,
  //                                  TimeUnit timeUnit) {
  //    isTrue(!loop.isTerminated(), "Cannot submit tasks to this timer as it has been cancelled.");
  //    long ms = TimeUnit.MILLISECONDS.convert(period, timeUnit);
  //    return schedule(ms, ms, runnable, true);
  //  }

  //  public TimerRegistration schedule(Runnable runnable,
  //                                    long period,
  //                                    long delay,
  //                                    TimeUnit timeUnit) {
  //    return schedule(TimeUnit.MILLISECONDS.convert(period, timeUnit), delay, runnable);
  //  }

  @Override
  public ScheduledFuture<?> submit(Runnable runnable) {
    return scheduleOneShot(resolution, constantlyNull(runnable));
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable runnable,
                                     long period,
                                     TimeUnit timeUnit) {
    return scheduleOneShot(TimeUnit.MILLISECONDS.convert(period, timeUnit), constantlyNull(runnable));
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long period, TimeUnit timeUnit) {
    return scheduleOneShot(TimeUnit.MILLISECONDS.convert(period, timeUnit), callable);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
    return scheduleFixedRate(TimeUnit.MILLISECONDS.convert(period, unit), initialDelay, constantlyNull(runnable));
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
    return scheduleFixedDelay(TimeUnit.MILLISECONDS.convert(delay, unit), initialDelay, constantlyNull(runnable));
  }

  private <V> Registration<V> scheduleOneShot(long firstDelay,
                                              Callable<V> callable) {
    isTrue(firstDelay >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");
    // TODO: check if switching to int gives anything
    long firstFireOffset = firstDelay / resolution;
    long firstFireRounds = firstFireOffset / wheel.getBufferSize();

    Registration<V> r = new OneShotRegistration<V>(firstFireRounds, callable, firstDelay);
    wheel.get(wheel.getCursor() + firstFireOffset + 1).add(r);
    return r;
  }

  private <V> Registration<V> scheduleFixedRate(long recurringTimeout,
                                                long firstDelay,
                                                Callable<V> callable) {
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    long offset = recurringTimeout / resolution;
    long rounds = offset / wheel.getBufferSize();

    long firstFireOffset = firstDelay / resolution;
    long firstFireRounds = firstFireOffset / wheel.getBufferSize();

    System.out.println(offset);
    Registration<V> r = new FixedRateRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset);
    wheel.get(wheel.getCursor() + firstFireOffset + 1).add(r);
    return r;
  }

  private <V> Registration<V> scheduleFixedDelay(long recurringTimeout,
                                                 long firstDelay,
                                                 Callable<V> callable) {
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    long offset = recurringTimeout / resolution;
    long rounds = offset / wheel.getBufferSize();

    long firstFireOffset = firstDelay / resolution;
    long firstFireRounds = firstFireOffset / wheel.getBufferSize();

    Registration<V> r = new FixedDelayRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset,
                                                     this::rescheduleForward);
    wheel.get(wheel.getCursor() + firstFireOffset + 1).add(r);
    return r;
  }

  /**
   * Rechedule a {@link TimerRegistration}  for the next fire
   *
   * @param registration
   */
  private void reschedule(Registration<?> registration) {
    registration.reset();
    wheel.get(wheel.getCursor() + registration.getOffset()).add(registration);
  }

  private void rescheduleForward(Registration<?> registration) {
    registration.reset();
    wheel.get(wheel.getCursor() + registration.getOffset() + 1).add(registration);
  }

  @Override
  public String toString() {
    return String.format("HashWheelTimer { Buffer Size: %d, Resolution: %d }",
                         wheel.getBufferSize(),
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

  private static Callable<?> constantlyNull(Runnable r) {
    return () -> {
      r.run();
      return null;
    };
  }
}

