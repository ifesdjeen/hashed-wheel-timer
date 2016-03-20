package com.ifesdjeen.timer;

import java.util.concurrent.*;

interface Registration<T> extends ScheduledFuture<T>, Runnable {

  enum Status {
    CANCELLED,
    READY
    // COMPLETED ??
  }

  int rounds();

  /**
   * Decrement an amount of runs Registration has to run until it's elapsed
   */
  void decrement();

  /**
   * Check whether the current Registration is ready for execution
   *
   * @return whether or not the current Registration is ready for execution
   */
  boolean ready();

  /**
   * Reset the Registration
   */
  void reset();

  boolean cancel(boolean mayInterruptIfRunning);

  /**
   * Check whether the current Registration is cancelled
   *
   * @return whether or not the current Registration is cancelled
   */
  boolean isCancelled();

  boolean isDone();

  /**
   * Get the offset of the Registration relative to the current cursor position
   * to make it fire timely.
   *
   * @return the offset of current Registration
   */
  int getOffset();

  boolean isCancelAfterUse();

  long getDelay(TimeUnit unit);

  @Override
  default int compareTo(Delayed o) {
    Registration other = (Registration) o;
    long r1 = rounds();
    long r2 = other.rounds();
    if (r1 == r2) {
      return other == this ? 0 : -1;
    } else {
      return Long.compare(r1, r2);
    }
  }

  @Override
  T get() throws InterruptedException, ExecutionException;

  @Override
  T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

}
