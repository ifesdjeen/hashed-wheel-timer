package com.ifesdjeen.timer;

public class Exceptions {

    /**
     * Used to alert consumers waiting with a {@link HashWheelWaitStrategy} for status changes.
     * <p/>
     * It does not fill in a stack trace for performance reasons.
     */
    @SuppressWarnings("serial")
    public static final class AlertException extends RuntimeException {

        /**
         * Pre-allocated exception to avoid garbage generation
         */
        public static final AlertException INSTANCE = new AlertException();

        /**
         * Private constructor so only a single instance exists.
         */
        private AlertException() {
        }

        /**
         * Overridden so the stack trace is not filled in for this exception for performance reasons.
         *
         * @return this instance.
         */
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

    }

}
