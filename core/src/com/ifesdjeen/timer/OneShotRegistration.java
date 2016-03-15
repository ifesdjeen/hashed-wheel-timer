package com.ifesdjeen.timer;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OneShotRegistration<T> extends CompletableFuture<T> implements Registration<T> {

  private final    Callable<T> callable;
  private volatile long        rounds;
  private volatile Status      status;

  public OneShotRegistration(long rounds, Callable<T> callable) {
    this.rounds = rounds;
    this.status = Status.READY;
    this.callable = callable;
  }

  @Override
  public void decrement() {
    rounds -= 1;
  }

  @Override
  public boolean ready() {
    return status == Status.READY && rounds == 0;
  }

  @Override
  public void reset() {
    throw new RuntimeException("One Shot Registrations can not be rescheduled");
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    this.status = Status.CANCELLED;
    return true;
  }

  @Override
  public boolean isCancelled() {
    return status == Status.CANCELLED;
  }

  @Override
  public long getOffset() {
    throw new RuntimeException("One Shot Registration can not be rescheduled");
  }

  @Override
  public boolean isCancelAfterUse() {
    return true;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    throw new NotImplementedException(); // TODO
  }

  @Override
  public long rounds() {
    return rounds;
  }

  @Override
  public void run() {
    try {
      this.complete(callable.call());
    } catch (Exception e) {
      this.completeExceptionally(e);
    }
  }

}
