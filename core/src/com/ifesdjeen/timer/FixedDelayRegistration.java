package com.ifesdjeen.timer;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

class FixedDelayRegistration<T> extends OneShotRegistration<T> {

  private final long                      rescheduleRounds;
  private final long                      scheduleOffset;
  private final Consumer<Registration<?>> rescheduleCallback;

  public FixedDelayRegistration(long rounds,
                                Callable<T> callable,
                                long delay,
                                long scheduleRounds,
                                long scheduleOffset,
                                Consumer<Registration<?>> rescheduleCallback) {
    super(rounds, callable, delay);
    this.rescheduleRounds = scheduleRounds;
    this.scheduleOffset = scheduleOffset;
    this.rescheduleCallback = rescheduleCallback;
  }

  public long getOffset() {
    return this.scheduleOffset;
  }

  public void reset() {
    this.status = Status.READY;
    this.rounds = rescheduleRounds;

  }

  @Override
  public boolean isCancelAfterUse() {
    return true;
  }

  @Override
  public void run() {
    super.run();
    rescheduleCallback.accept(this);
  }

}
