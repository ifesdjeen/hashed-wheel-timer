package com.ifesdjeen.timer;

import java.util.concurrent.Callable;

class FixedRateRegistration<T> extends OneShotRegistration<T> {

  private final long rescheduleRounds;
  private final long scheduleOffset;

  public FixedRateRegistration(long rounds,
                               Callable<T> callable,
                               long delay,
                               long rescheduleRounds,
                               long scheduleOffset) {
    super(rounds, callable, delay);
    this.rescheduleRounds = rescheduleRounds;
    this.scheduleOffset = scheduleOffset;
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
    return false;
  }

}
