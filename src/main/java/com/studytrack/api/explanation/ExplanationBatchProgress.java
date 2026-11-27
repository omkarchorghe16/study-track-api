package com.studytrack.api.explanation;

import java.util.concurrent.atomic.AtomicInteger;

/** Live counters of one background run for a subject; written by the worker, read by the status endpoint. */
public final class ExplanationBatchProgress {
    public enum State { RUNNING, COMPLETED, FAILED }

    private final int total;
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private volatile State state = State.RUNNING;
    private volatile String message;

    ExplanationBatchProgress(int total) {
        this.total = total;
    }

    int getTotal() {
        return total;
    }

    int getCompleted() {
        return completed.get();
    }

    int getFailed() {
        return failed.get();
    }

    State getState() {
        return state;
    }

    String getMessage() {
        return message;
    }

    void recordCompleted() {
        completed.incrementAndGet();
    }

    void recordFailed() {
        failed.incrementAndGet();
    }

    /** All questions were tried; some may have failed one by one. */
    void finish() {
        state = State.COMPLETED;
    }

    /** The run stopped early because every further call would fail too (AI off, no API key). */
    void stopWith(String reason) {
        message = reason;
        state = State.FAILED;
    }
}
