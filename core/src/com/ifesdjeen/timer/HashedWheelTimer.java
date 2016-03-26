package com.ifesdjeen.timer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
public class HashedWheelTimer implements ScheduledExecutorService {

  public static final  long   DEFAULT_RESOLUTION = TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS);
  public static final  int    DEFAULT_WHEEL_SIZE = 512;
  private static final String DEFAULT_TIMER_NAME = "hashed-wheel-timer";

  private final Set<Registration<?>>[] wheel;
  private final int                    wheelSize;
  private final long                   resolution;
  private final ExecutorService        loop;
  private final ExecutorService        executor;
  private final WaitStrategy           waitStrategy;

  private volatile int cursor = 0;

  /**
   * Create a new {@code HashedWheelTimer} using the given with default resolution of 10 MILLISECONDS and
   * default wheel size.
   */
  public HashedWheelTimer() {
    this(DEFAULT_RESOLUTION, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashedWheelTimer} using the given timer resolution. All times will rounded up to the closest
   * multiple of this resolution.
   *
   * @param resolution the resolution of this timer, in NANOSECONDS
   */
  public HashedWheelTimer(long resolution) {
    this(resolution, DEFAULT_WHEEL_SIZE, new WaitStrategy.SleepWait());
  }

  /**
   * Create a new {@code HashedWheelTimer} using the given timer {@data resolution} and {@data wheelSize}. All times will
   * rounded up to the closest multiple of this resolution.
   *
   * @param res          resolution of this timer in NANOSECONDS
   * @param wheelSize    size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
   *                     for sparse timeouts. Sane default is 512.
   * @param waitStrategy strategy for waiting for the next tick
   */
  public HashedWheelTimer(long res, int wheelSize, WaitStrategy waitStrategy) {
    this(DEFAULT_TIMER_NAME, res, wheelSize, waitStrategy, Executors.newFixedThreadPool(1));
  }

  /**
   * Create a new {@code HashedWheelTimer} using the given timer {@data resolution} and {@data wheelSize}. All times will
   * rounded up to the closest multiple of this resolution.
   *
   * @param name      name for daemon thread factory to be displayed
   * @param res       resolution of this timer in NANOSECONDS
   * @param wheelSize size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
   *                  for sparse timeouts. Sane default is 512.
   * @param strategy  strategy for waiting for the next tick
   * @param exec      Executor instance to submit tasks to
   */
  public HashedWheelTimer(String name, long res, int wheelSize, WaitStrategy strategy, ExecutorService exec) {
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

  @Override
  public String toString() {
    return String.format("HashedWheelTimer { Buffer Size: %d, Resolution: %d }",
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

  /**
   * Create a wrapper Function, which will "debounce" i.e. postpone the function execution until after <code>period</code>
   * has elapsed since last time it was invoked. <code>delegate</code> will be called most once <code>period</code>.
   *
   * @param delegate delegate runnable to be wrapped
   * @param period given time period
   * @param timeUnit unit of the period
   * @return wrapped runnable
   */
  public Runnable debounce(Runnable delegate,
                           long period,
                           TimeUnit timeUnit) {
    AtomicReference<ScheduledFuture<?>> reg = new AtomicReference<>();

    return new Runnable() {
      @Override
      public void run() {
        ScheduledFuture<?> future = reg.getAndSet(scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                                                                  new Callable<Object>() {
                                                                    @Override
                                                                    public Object call() throws Exception {
                                                                      delegate.run();
                                                                      return null;
                                                                    }
                                                                  }));
        if (future != null) {
          future.cancel(true);
        }
      }
    };
  }

  /**
   * Create a wrapper Consumer, which will "debounce" i.e. postpone the function execution until after <code>period</code>
   * has elapsed since last time it was invoked. <code>delegate</code> will be called most once <code>period</code>.
   *
   * @param delegate delegate consumer to be wrapped
   * @param period given time period
   * @param timeUnit unit of the period
   * @return wrapped runnable
   */
  public <T> Consumer<T> debounce(Consumer<T> delegate,
                                  long period,
                                  TimeUnit timeUnit) {
    AtomicReference<ScheduledFuture<T>> reg = new AtomicReference<>();

    return new Consumer<T>() {
      @Override
      public void accept(T t) {
        ScheduledFuture<T> future = reg.getAndSet(scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                                                                  new Callable<T>() {
                                                                    @Override
                                                                    public T call() throws Exception {
                                                                      delegate.accept(t);
                                                                      return t;
                                                                    }
                                                                  }));
        if (future != null) {
          future.cancel(true);
        }
      }
    };
  }

  /**
   * Create a wrapper Runnable, which creates a throttled version, which, when called repeatedly, will call the
   * original function only once per every <code>period</code> milliseconds. It's easier to think about throttle
   * in terms of it's "left bound" (first time it's called within the current period).
   *
   * @param delegate delegate runnable to be called
   * @param period period to be elapsed between the runs
   * @param timeUnit unit of the period
   * @return wrapped runnable
   */
  public Runnable throttle(Runnable delegate,
                           long period,
                           TimeUnit timeUnit) {
    AtomicBoolean alreadyWaiting = new AtomicBoolean();

    return new Runnable() {
      @Override
      public void run() {
        if (alreadyWaiting.compareAndSet(false, true)) {
          scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                          new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                              delegate.run();
                              alreadyWaiting.compareAndSet(true, false);
                              return null;
                            }
                          });
        }
      }
    };
  }

  /**
   * Create a wrapper Consumer, which creates a throttled version, which, when called repeatedly, will call the
   * original function only once per every <code>period</code> milliseconds. It's easier to think about throttle
   * in terms of it's "left bound" (first time it's called within the current period).
   *
   * @param delegate delegate consumer to be called
   * @param period period to be elapsed between the runs
   * @param timeUnit unit of the period
   * @return wrapped runnable
   */
  public <T> Consumer<T> throttle(Consumer<T> delegate,
                                  long period,
                                  TimeUnit timeUnit) {
    AtomicBoolean alreadyWaiting = new AtomicBoolean();
    AtomicReference<T> lastValue = new AtomicReference<>();

    return new Consumer<T>() {
      @Override
      public void accept(T val) {
        lastValue.set(val);
        if (alreadyWaiting.compareAndSet(false, true)) {
          scheduleOneShot(TimeUnit.NANOSECONDS.convert(period, timeUnit),
                          new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                              delegate.accept(lastValue.getAndSet(null));
                              alreadyWaiting.compareAndSet(true, false);
                              return null;
                            }
                          });
        }
      }
    };
  }

  // TODO: biConsumer

  /**
   * INTERNALS
   */

  private <V> Registration<V> scheduleOneShot(long firstDelay,
                                              Callable<V> callable) {
    assertRunning();
    isTrue(firstDelay >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");
    int firstFireOffset = (int) (firstDelay / resolution);
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new OneShotRegistration<V>(firstFireRounds, callable, firstDelay);
    // We always add +1 because we'd like to keep to the right boundary of event on execution, not to the left:
    //
    // For example:
    //    |          now          |
    // res start               next tick
    // The earliest time we can tick is aligned to the right. Think of it a bit as a `ceil` function.
    wheel[idx(cursor + firstFireOffset + 1)].add(r);
    return r;
  }

  private <V> Registration<V> scheduleFixedRate(long recurringTimeout,
                                                long firstDelay,
                                                Callable<V> callable) {
    assertRunning();
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    int offset = (int) (recurringTimeout / resolution);
    int rounds = offset / wheelSize;

    int firstFireOffset = (int) (firstDelay / resolution);
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new FixedRateRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset);
    wheel[idx(cursor + firstFireOffset + 1)].add(r);
    return r;
  }

  private <V> Registration<V> scheduleFixedDelay(long recurringTimeout,
                                                 long firstDelay,
                                                 Callable<V> callable) {
    assertRunning();
    isTrue(recurringTimeout >= resolution,
           "Cannot schedule tasks for amount of time less than timer precision.");

    int offset = (int) (recurringTimeout / resolution);
    int rounds = offset / wheelSize;

    int firstFireOffset = (int) (firstDelay / resolution);
    int firstFireRounds = firstFireOffset / wheelSize;

    Registration<V> r = new FixedDelayRegistration<>(firstFireRounds, callable, recurringTimeout, rounds, offset,
                                                     this::reschedule);
    wheel[idx(cursor + firstFireOffset + 1)].add(r);
    return r;
  }

  /**
   * Rechedule a {@link Registration} for the next fire
   */
  private void reschedule(Registration<?> registration) {
    registration.reset();
    wheel[idx(cursor + registration.getOffset() + 1)].add(registration);
  }

  private int idx(int cursor) {
    return cursor % wheelSize;
  }

  private void assertRunning() {
    if (this.loop.isTerminated()) {
      throw new IllegalStateException("Timer is not running");
    }
  }

  private static void isTrue(boolean expression, String message) {
    if (!expression) {
      throw new IllegalArgumentException(message);
    }
  }

  private static Callable<?> constantlyNull(Runnable r) {
    return () -> {
      r.run();
      return null;
    };
  }

}
