package com.studytrack.api.explanation;

import com.studytrack.api.item.ItemContent;

/** The key a shared explanation is saved and found by: the subject name and the question text. */
final class ExplanationKeys {
    private ExplanationKeys() {
    }

    /** Same subject name and question text, ignoring case and spacing, give the same key (SHA-256, hex). */
    static String of(String subjectName, String questionText) {
        return ItemContent.computeContentHash(subjectName + " > " + questionText, null);
    }
}
