package com.ifesdjeen.timer;

public class TimerWithSleepWait extends AbstractTimerTest {
  @Override
  public WaitStrategy waitStrategy() {
    return new WaitStrategy.SleepWait();
  }
}
