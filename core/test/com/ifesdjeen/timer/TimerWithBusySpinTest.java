package com.ifesdjeen.timer;

public class TimerWithBusySpinTest extends AbstractTimerTest {

  @Override
  public WaitStrategy waitStrategy() {
    return new WaitStrategy.BusySpinWait();
  }

}
