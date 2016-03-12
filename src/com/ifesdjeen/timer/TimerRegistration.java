package com.ifesdjeen.timer;

import java.util.concurrent.*;

/**
 * Timer Registration
 *
 * @param <T> type of the Timer Registration Consumer
 */
public class TimerRegistration implements Runnable, Comparable {

  public static int STATUS_CANCELLED = -1;
  public static int STATUS_READY     = 0;

  // TODO: pad status to avoid cache hits? rounds are accessed from just one thread anyways
  private          long     rounds;
  private volatile int      status;

  private final    long     rescheduleRounds;
  private final    long     scheduleOffset;
  private final    boolean  cancelAfterUse;
  private final    Runnable runnable;

  /**
   * Creates a new Timer Registration with given {@data rounds}, {@data offset} and {@data delegate}.
   *
   * @param rounds amount of rounds the Registration should go through until it's elapsed
   * @param offset offset of in the Ring Buffer for rescheduling
   */
  public TimerRegistration(long rounds, long offset, long rescheduleRounds, Runnable runnable, boolean cancelAfterUse) {
    this.rescheduleRounds = rescheduleRounds;
    this.scheduleOffset = offset;
    this.rounds = rounds;
    this.status = STATUS_READY;
    this.cancelAfterUse = cancelAfterUse;
    this.runnable = runnable;
  }

  /**
   * Decrement an amount of runs Registration has to run until it's elapsed
   */
  public void decrement() {
    rounds -= 1;
  }

  /**
   * Check whether the current Registration is ready for execution
   *
   * @return whether or not the current Registration is ready for execution
   */
  public boolean ready() {
    return status == STATUS_READY && rounds == 0;
  }

  /**
   * Reset the Registration
   */
  public void reset() {
    this.status = STATUS_READY;
    this.rounds = rescheduleRounds;
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    this.status = STATUS_CANCELLED;
    return true;
  }

  /**
   * Check whether the current Registration is cancelled
   *
   * @return whether or not the current Registration is cancelled
   */
  public boolean isCancelled() {
    return status == STATUS_CANCELLED;
  }

  public boolean isDone() {
    return false;
  }

  /**
   * Get the offset of the Registration relative to the current Ring Buffer position
   * to make it fire timely.
   *
   * @return the offset of current Registration
   */
  public long getOffset() {
    return this.scheduleOffset;
  }

  public boolean isCancelAfterUse() {
    return this.cancelAfterUse;
  }

  @Override
  public String toString() {
    return String.format("HashWheelTimer { Rounds left: %d, Status: %d }", rounds, status);
  }

  public long getDelay(TimeUnit unit) {
    return 0; // TODO
  }

  public int compareTo(Object o) {
    TimerRegistration other = (TimerRegistration) o;
    if (rounds == other.rounds) {
      return other == this ? 0 : -1;
    } else {
      return Long.compare(rounds, other.rounds);
    }
  }

  @Override
  public void run() {
    runnable.run();
  }

  public ScheduledFuture<?> wrap() {
    TimerRegistration capture = this;
    return new ScheduledFuture<Object>() {
      @Override
      public long getDelay(TimeUnit unit) {
        return 0; // TODO
      }

      @Override
      public int compareTo(Delayed o) {
        return 0; // TODO
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return capture.cancel(mayInterruptIfRunning);
      }

      @Override
      public boolean isCancelled() {
        return capture.isCancelled();
      }

      @Override
      public boolean isDone() {
        return capture.isDone();
      }

      @Override
      public Object get() throws InterruptedException, ExecutionException {
        return null;
      }

      @Override
      public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null; // ?? or does it block/wait in this case it makes sense to use the settable future everywhere
      }
    };
  }
}
