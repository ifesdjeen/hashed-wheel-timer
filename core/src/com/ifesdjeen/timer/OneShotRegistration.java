package com.ifesdjeen.timer;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class OneShotRegistration<T> extends CompletableFuture<T> implements Registration<T> {

  private volatile int rounds;
  private volatile Status status;

  public OneShotRegistration(int rounds) {
    this.rounds = rounds;
    this.status = Status.READY;
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
  public int rounds() {
    return rounds;
  }

  public static class CallableOneShotRegistration<T> extends OneShotRegistration<T> {

    private final Callable<T> callable;

    public CallableOneShotRegistration(int rounds, Callable<T> callable) {
      super(rounds);
      this.callable = callable;
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

  public static class RunnableOneShotRegistration extends OneShotRegistration<Object> {

    private final Runnable delegate;

    public RunnableOneShotRegistration(int rounds, Runnable runnable) {
      super(rounds);
      this.delegate = runnable;
    }

    @Override
    public void run() {
      this.delegate.run();
    }
  }
}
