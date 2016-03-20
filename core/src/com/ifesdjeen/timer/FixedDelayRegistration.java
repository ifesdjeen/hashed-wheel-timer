package com.ifesdjeen.timer;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

class FixedDelayRegistration<T> extends OneShotRegistration<T> {

  private final int                       rescheduleRounds;
  private final int                       scheduleOffset;
  private final Consumer<Registration<?>> rescheduleCallback;

  public FixedDelayRegistration(int rounds,
                                Callable<T> callable,
                                long delay,
                                int scheduleRounds,
                                int scheduleOffset,
                                Consumer<Registration<?>> rescheduleCallback) {
    super(rounds, callable, delay);
    this.rescheduleRounds = scheduleRounds;
    this.scheduleOffset = scheduleOffset;
    this.rescheduleCallback = rescheduleCallback;
  }

  public int getOffset() {
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
