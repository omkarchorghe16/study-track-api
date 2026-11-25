package com.studytrack.api.search;

import com.studytrack.api.search.SearchDtos.SnippetPart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnippetBuilderTest {

    @Test
    void parsesOnlyWordsLowerCasedAndBuildsPrefixQuery() {
        SearchTerms terms = SearchTerms.parseQuery("HashMap's  'buckets' & | !java:* hashmap");
        assertEquals(List.of("hashmap", "s", "buckets", "java"), terms.words());
        assertEquals("hashmap:* & s:* & buckets:* & java:*", terms.toPrefixTsQuery());
        assertTrue(SearchTerms.parseQuery("!!! ---").isEmpty());
    }

    @Test
    void marksEveryWordStartingWithATerm() {
        List<SnippetPart> parts = SnippetBuilder.buildSnippet("Hash tables hash keys; rehashing is rare.",
                SearchTerms.parseQuery("hash"));
        assertEquals(List.of(new SnippetPart("Hash", true), new SnippetPart(" tables ", false),
                new SnippetPart("hash", true), new SnippetPart(" keys; rehashing is rare.", false)), parts);
    }

    @Test
    void cutsLongTextAroundTheFirstMatch() {
        String text = "word ".repeat(100) + "needle " + "tail ".repeat(100);
        List<SnippetPart> parts = SnippetBuilder.buildSnippet(text, SearchTerms.parseQuery("needle"));
        String joined = parts.stream().map(SnippetPart::text).reduce("", String::concat);
        assertTrue(joined.startsWith("…word"));
        assertTrue(joined.endsWith("…"));
        assertTrue(joined.length() <= SnippetBuilder.MAX_SNIPPET_CHARS + 2);
        assertTrue(parts.stream().anyMatch(part -> part.match() && part.text().equals("needle")));
    }

    @Test
    void marksWordsWhoseStemMatches() {
        SearchTerms terms = SearchTerms.parseQuery("storing trees");
        assertTrue(terms.matchesWord("Store"));
        assertTrue(terms.matchesWord("tree"));
        assertTrue(!terms.matchesWord("stack"));
    }
}
