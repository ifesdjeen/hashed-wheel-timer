package com.ifesdjeen.timer;

public interface WaitStrategy {
  /**
   * Wait until the given deadline, {@data deadlineMilliseconds}
   *
   * @param deadlineMilliseconds deadline to wait for, in milliseconds
   */
  public void waitUntil(long deadlineMilliseconds) throws InterruptedException;

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


}
