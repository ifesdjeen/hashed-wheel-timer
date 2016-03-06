package com.ifesdjeen.timer;

import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * Strategy employed to wait for specific {@link LongSupplier} values with various spinning strategies.
 */
public abstract class HashWheelWaitStrategy
{

    final static HashWheelWaitStrategy YIELDING  = new Yielding();
    final static HashWheelWaitStrategy SLEEPING  = new Sleeping();
    final static HashWheelWaitStrategy BUSY_SPIN = new BusySpin();

    /**
     * Busy Spin strategy that uses a busy spin loop for ringbuffer consumers waiting on a barrier.
     *
     * This strategy will use CPU resource to avoid syscalls which can introduce latency jitter.  It is best
     * used when threads can be bound to specific CPU cores.
     */
    public static HashWheelWaitStrategy busySpin() {
        return BUSY_SPIN;
    }

    /**
     * Sleeping strategy that initially spins, then uses a Thread.yield(), and eventually sleep
     * (<code>LockSupport.parkNanos(1)</code>) for the minimum number of nanos the OS and JVM will allow while the
     * ringbuffer consumers are waiting on a barrier.
     * <p>
     * This strategy is a good compromise between performance and CPU resource. Latency spikes can occur after quiet
     * periods.
     */
    public static HashWheelWaitStrategy sleeping() {
        return SLEEPING;
    }

    /**
     * Sleeping strategy that initially spins, then uses a Thread.yield(), and eventually sleep
     * (<code>LockSupport.parkNanos(1)</code>) for the minimum number of nanos the OS and JVM will allow while the
     * ringbuffer consumers are waiting on a barrier.
     * <p>
     * This strategy is a good compromise between performance and CPU resource. Latency spikes can occur after quiet
     * periods.
     *
     * @param retries the spin cycle count before parking
     */
    public static HashWheelWaitStrategy sleeping(int retries) {
        return new Sleeping(retries);
    }

    /**
     * Yielding strategy that uses a Thread.yield() for ringbuffer consumers waiting on a barrier
     * after an initially spinning.
     *
     * This strategy is a good compromise between performance and CPU resource without incurring significant latency spikes.
     */
    public static HashWheelWaitStrategy yielding() {
        return YIELDING;
    }

    /**
     * Implementations should signal the waiting ringbuffer consumers that the cursor has advanced.
     */
    public void signalAllWhenBlocking() {
    }

    /**
     * Wait for the given sequence to be available.  It is possible for this method to return a value
     * less than the sequence number supplied depending on the implementation of the WaitStrategy.  A common
     * use for this is to signal a timeout.  Any EventProcessor that is using a WaitStragegy to get notifications
     * about message becoming available should remember to handle this case.
     *
     * @param sequence to be waited on.
     * @param cursor the main sequence from ringbuffer. Wait/notify strategies will
     *    need this as is notified upon update.
     * @param spinObserver Spin observer
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws Exceptions.AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    public abstract long waitFor(long sequence, LongSupplier cursor, Runnable spinObserver)
        throws Exceptions.AlertException, InterruptedException;


    final static class BusySpin extends HashWheelWaitStrategy {

        @Override
        public long waitFor(final long sequence, LongSupplier cursor, final Runnable barrier)
            throws Exceptions.AlertException, InterruptedException
        {
            long availableSequence;

            while ((availableSequence = cursor.getAsLong()) < sequence)
            {
                barrier.run();
            }

            return availableSequence;
        }
    }

    final static class Sleeping extends HashWheelWaitStrategy {

        private static final int DEFAULT_RETRIES = 200;

        private final int retries;

        Sleeping() {
            this(DEFAULT_RETRIES);
        }

        Sleeping(int retries) {
            this.retries = retries;
        }

        private int applyWaitMethod(final Runnable barrier, int counter)
            throws Exceptions.AlertException
        {
            barrier.run();

            if (counter > 100)
            {
                --counter;
            }
            else if (counter > 0)
            {
                --counter;
                Thread.yield();
            }
            else
            {
                LockSupport.parkNanos(1L);
            }

            return counter;
        }

        @Override
        public long waitFor(final long sequence, LongSupplier cursor, final Runnable barrier)
            throws Exceptions.AlertException, InterruptedException
        {
            long availableSequence;
            int counter = retries;

            while ((availableSequence = cursor.getAsLong()) < sequence)
            {
                counter = applyWaitMethod(barrier, counter);
            }

            return availableSequence;
        }
    }

    final static class Yielding extends HashWheelWaitStrategy {

        private static final int SPIN_TRIES = 100;

        private int applyWaitMethod(final Runnable barrier, int counter)
            throws Exceptions.AlertException
        {
            barrier.run();

            if (0 == counter)
            {
                Thread.yield();
            }
            else
            {
                --counter;
            }

            return counter;
        }

        @Override
        public long waitFor(final long sequence, LongSupplier cursor, final Runnable barrier)
            throws Exceptions.AlertException, InterruptedException
        {
            long availableSequence;
            int counter = SPIN_TRIES;

            while ((availableSequence = cursor.getAsLong()) < sequence)
            {
                counter = applyWaitMethod(barrier, counter);
            }

            return availableSequence;
        }
    }
}
