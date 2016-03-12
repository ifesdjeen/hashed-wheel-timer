package com.ifesdjeen.timer;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timer Registration
 *
 * @param <T> type of the Timer Registration Consumer
 */
public class TimerRegistration implements Runnable, Comparable {

  public static int STATUS_CANCELLED = -1;
  public static int STATUS_READY     = 0;

  private final long     rescheduleRounds;
  private final long     scheduleOffset;
  private       long     rounds;
  private       int      status;
  private final boolean  cancelAfterUse;
  private final Runnable runnable;

  /**
   * Creates a new Timer Registration with given {@data rounds}, {@data offset} and {@data delegate}.
   *
   * @param rounds amount of rounds the Registration should go through until it's elapsed
   * @param offset offset of in the Ring Buffer for rescheduling
   */
  public TimerRegistration(long rounds, long offset, long rescheduleRounds, Runnable runnable) {
    this.rescheduleRounds = rescheduleRounds;
    this.scheduleOffset = offset;
    this.rounds = rounds;
    this.status = STATUS_READY;
    this.cancelAfterUse = false;
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

  /**
   * @return {@literal this}
   */
  public TimerRegistration cancelAfterUse() {
    // cancelAfterUse = false; TODO ?!?
    return this;
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

}
