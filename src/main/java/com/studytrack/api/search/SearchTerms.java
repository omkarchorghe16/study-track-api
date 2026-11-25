package com.studytrack.api.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of a search query: letters and digits only, lower-cased, de-duplicated, at most
 * {@link #MAX_TERMS}. Everything else (quotes, operators, punctuation) is dropped, so the terms are safe
 * to put into a PostgreSQL tsquery.
 */
public record SearchTerms(List<String> words) {
    public static final int MAX_TERMS = 10;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final List<String> ENDINGS = List.of("ing", "ed", "es", "s");
    private static final int MIN_STEM_LENGTH = 3;

    public static SearchTerms parseQuery(String query) {
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(query == null ? "" : query);
        while (matcher.find() && words.size() < MAX_TERMS) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return new SearchTerms(List.copyOf(words));
    }

    public boolean isEmpty() {
        return words.isEmpty();
    }

    /** Every word as a prefix, all required: "hash map" becomes "hash:* & map:*". */
    public String toPrefixTsQuery() {
        List<String> parts = new ArrayList<>();
        for (String word : words) {
            parts.add(word + ":*");
        }
        return String.join(" & ", parts);
    }

    /**
     * True when {@code word} starts with one of the terms, ignoring a common English ending of the term
     * ("storing" marks "store"), roughly as PostgreSQL's stemming matches. Used only for highlighting.
     */
    public boolean matchesWord(String word) {
        String lowerWord = word.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(term -> lowerWord.startsWith(stripEnding(term)));
    }

    private static String stripEnding(String term) {
        for (String ending : ENDINGS) {
            if (term.endsWith(ending) && term.length() - ending.length() >= MIN_STEM_LENGTH) {
                return term.substring(0, term.length() - ending.length());
            }
        }
        return term;
    }
}
