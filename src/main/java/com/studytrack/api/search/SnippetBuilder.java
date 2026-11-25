package com.studytrack.api.search;

import com.studytrack.api.search.SearchDtos.SnippetPart;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cuts a short plain-text snippet around the first matching word and marks every matching word. */
final class SnippetBuilder {
    static final int MAX_SNIPPET_CHARS = 220;
    private static final int CHARS_BEFORE_MATCH = 60;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private SnippetBuilder() {
    }

    static List<SnippetPart> buildSnippet(String text, SearchTerms terms) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String plain = text.replaceAll("\\s+", " ").strip();
        int start = Math.max(0, findFirstMatch(plain, terms) - CHARS_BEFORE_MATCH);
        if (start > 0) {
            int space = plain.indexOf(' ', start);
            start = space < 0 ? start : space + 1;
        }
        int end = Math.min(plain.length(), start + MAX_SNIPPET_CHARS);
        if (end < plain.length()) {
            int space = plain.lastIndexOf(' ', end);
            end = space > start ? space : end;
        }
        String window = (start > 0 ? "…" : "") + plain.substring(start, end) + (end < plain.length() ? "…" : "");
        return markMatches(window, terms);
    }

    private static int findFirstMatch(String text, SearchTerms terms) {
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            if (terms.matchesWord(matcher.group())) {
                return matcher.start();
            }
        }
        return 0;
    }

    private static List<SnippetPart> markMatches(String text, SearchTerms terms) {
        List<SnippetPart> parts = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        int position = 0;
        while (matcher.find()) {
            if (terms.matchesWord(matcher.group())) {
                if (matcher.start() > position) {
                    parts.add(new SnippetPart(text.substring(position, matcher.start()), false));
                }
                parts.add(new SnippetPart(matcher.group(), true));
                position = matcher.end();
            }
        }
        if (position < text.length()) {
            parts.add(new SnippetPart(text.substring(position), false));
        }
        return parts;
    }
}
