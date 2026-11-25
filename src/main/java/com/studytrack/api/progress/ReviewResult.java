package com.studytrack.api.progress;

/** A flashcard rating, from forgotten (AGAIN) to effortless (EASY). */
public enum ReviewResult {
    AGAIN, HARD, GOOD, EASY;

    public boolean isSuccess() {
        return this == GOOD || this == EASY;
    }
}
