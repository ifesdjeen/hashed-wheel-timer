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
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
public class HashWheelTimer { // implements ScheduledExecutorService

  public static final  int    DEFAULT_WHEEL_SIZE = 512;
  private static final String DEFAULT_TIMER_NAME = "hash-wheel-timer";

  private final RingBuffer<Set<TimerRegistration>> wheel;
  private final int                                resolution;
  private final Thread                             loop;
  private final Executor                           executor;
  private final WaitStrategy                       waitStrategy;

  /**
   * Create a new {@code HashWheelTimer} using the given with default resolution of 100 milliseconds and
   * default wheel size.
   */
  public HashWheelTimer() {
    this(100, DEFAULT_WHEEL_SIZE, new SleepWait());
  }

  /**
   * Create a new {@code HashWheelTimer} using the given timer resolution. All times will rounded up to the closest
   * multiple of this resolution.
   *
   * @param resolution the resolution of this timer, in milliseconds
   */
  public HashWheelTimer(int resolution) {
    this(resolution, DEFAULT_WHEEL_SIZE, new SleepWait());
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
  public HashWheelTimer(String name, int res, int wheelSize, WaitStrategy strategy, Executor exec) {
    this.waitStrategy = strategy;

    this.wheel = RingBuffer.createSingleProducer(new EventFactory<Set<TimerRegistration>>() {
      @Override
      public Set<TimerRegistration> newInstance() {
        return new ConcurrentSkipListSet<TimerRegistration>();
      }
    }, wheelSize);

    this.resolution = res;
    this.loop = DaemonThreadFactory.INSTANCE.newThread(new Runnable() {
      @Override
      public void run() {
        long deadline = System.currentTimeMillis();

        while (true) {
          Set<TimerRegistration> registrations = wheel.get(wheel.getCursor());

          for (TimerRegistration r : registrations) {
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
    });

    this.executor = exec;
    this.start();
  }

  public TimerRegistration schedule(Runnable runnable,
                                    long period,
                                    TimeUnit timeUnit,
                                    long delayInMilliseconds) {
    isTrue(!loop.isInterrupted(), "Cannot submit tasks to this timer as it has been cancelled.");
    return schedule(TimeUnit.MILLISECONDS.convert(period, timeUnit), delayInMilliseconds, runnable);
  }

  public TimerRegistration submit(Runnable runnable,
                                  long period,
                                  TimeUnit timeUnit) {
    isTrue(!loop.isInterrupted(), "Cannot submit tasks to this timer as it has been cancelled.");
    long ms = TimeUnit.MILLISECONDS.convert(period, timeUnit);
    return schedule(ms, ms, runnable).cancelAfterUse();
  }

  public TimerRegistration submit(Runnable consumer) {
    return submit(consumer, resolution, TimeUnit.MILLISECONDS);
  }

  public TimerRegistration schedule(Runnable runnable,
                                    long period,
                                    long delay,
                                    TimeUnit timeUnit) {
    return schedule(TimeUnit.MILLISECONDS.convert(period, timeUnit), delay, runnable);
  }

  public TimerRegistration schedule(Runnable runnable,
                                    long period,
                                    TimeUnit timeUnit) {
    return schedule(TimeUnit.MILLISECONDS.convert(period, timeUnit), 0, runnable);
  }

  private TimerRegistration schedule(long recurringTimeout,
                                     long firstDelay,
                                     Runnable runnable) {
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    long offset = recurringTimeout / resolution;
    long rounds = offset / wheel.getBufferSize();

    long firstFireOffset = firstDelay / resolution;
    long firstFireRounds = firstFireOffset / wheel.getBufferSize();

    TimerRegistration r = new TimerRegistration(firstFireRounds, offset, rounds, runnable);
    wheel.get(wheel.getCursor() + firstFireOffset + 1).add(r);
    return r;
  }

  /**
   * Rechedule a {@link TimerRegistration}  for the next fire
   *
   * @param registration
   */
  private void reschedule(TimerRegistration registration) {
    registration.reset();
    wheel.get(wheel.getCursor() + registration.getOffset()).add(registration);
  }

  /**
   * Start the Timer
   */
  public void start() {
    this.loop.start();
    wheel.publish(0);
  }

  /**
   * Cancel current Timer
   */
  public void cancel() {
    this.loop.interrupt();
  }


  @Override
  public String toString() {
    return String.format("HashWheelTimer { Buffer Size: %d, Resolution: %d }",
                         wheel.getBufferSize(),
                         resolution);
  }


  /**
   * Wait strategy for the timer
   */
  public static interface WaitStrategy {

    /**
     * Wait until the given deadline, {@data deadlineMilliseconds}
     *
     * @param deadlineMilliseconds deadline to wait for, in milliseconds
     */
    public void waitUntil(long deadlineMilliseconds) throws InterruptedException;
  }

  /**
   * Yielding wait strategy.
   * <p/>
   * Spins in the loop, until the deadline is reached. Releases the flow control
   * by means of Thread.yield() call. This strategy is less precise than BusySpin
   * one, but is more scheduler-friendly.
   */
  public static class YieldingWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
      while (deadlineMilliseconds >= System.currentTimeMillis()) {
        Thread.yield();
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }
      }
    }
  }

  /**
   * BusySpin wait strategy.
   * <p/>
   * Spins in the loop until the deadline is reached. In a multi-core environment,
   * will occupy an entire core. Is more precise than Sleep wait strategy, but
   * consumes more resources.
   */
  public static class BusySpinWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
      while (deadlineMilliseconds >= System.currentTimeMillis()) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }
      }
    }
  }

  /**
   * Sleep wait strategy.
   * <p/>
   * Will release the flow control, giving other threads a possibility of execution
   * on the same processor. Uses less resources than BusySpin wait, but is less
   * precise.
   */
  public static class SleepWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
      long sleepTimeMs = deadlineMilliseconds - System.currentTimeMillis();
      if (sleepTimeMs > 0) {
        Thread.sleep(sleepTimeMs);
      }
    }
  }

  public static void isTrue(boolean expression, String message) {
    if (!expression) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Executor Delegates
   */

  //@Override
  public void execute(Runnable command) {
    executor.execute(command);
  }


}

