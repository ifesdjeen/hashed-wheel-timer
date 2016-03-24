package com.ifesdjeen.timer;

public class TimerWithYieldWait extends AbstractTimerTest {
  @Override
  public WaitStrategy waitStrategy() {
    return new WaitStrategy.YieldingWait();
  }
}
