package com.ifesdjeen.timer;

import java.util.concurrent.Callable;

class FixedRateRegistration<T> extends OneShotRegistration<T> {

  private final int rescheduleRounds;
  private final int scheduleOffset;

  public FixedRateRegistration(int rounds,
                               Callable<T> callable,
                               long delay,
                               int rescheduleRounds,
                               int scheduleOffset) {
    super(rounds, callable, delay);
    this.rescheduleRounds = rescheduleRounds;
    this.scheduleOffset = scheduleOffset;
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
    return false;
  }

}
