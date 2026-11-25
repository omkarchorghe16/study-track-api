package com.studytrack.api.search;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemContent;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.ItemType;
import com.studytrack.api.search.ItemSearchRepository.SearchRow;
import com.studytrack.api.search.SearchDtos.SearchPageResponse;
import com.studytrack.api.search.SearchDtos.SearchResultResponse;
import com.studytrack.api.search.SearchDtos.SnippetPart;
import com.studytrack.api.subject.SubjectService;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

/** Searches the current user's items by title, question, answer and notes. */
@Service
public class SearchService {
    private final ItemSearchRepository itemSearchRepository;
    private final SubjectService subjectService;
    private final CurrentUser currentUser;

    public SearchService(ItemSearchRepository itemSearchRepository, SubjectService subjectService,
                         CurrentUser currentUser) {
        this.itemSearchRepository = itemSearchRepository;
        this.subjectService = subjectService;
        this.currentUser = currentUser;
    }

    /**
     * Items matching every word of {@code query} (as a word prefix), best match first. A query without
     * letters or digits matches nothing. {@code subjectId}, when given, must be one of the user's subjects.
     */
    @Transactional(readOnly = true)
    public SearchPageResponse searchItems(String query, Long subjectId, int page, int size) {
        if (subjectId != null) {
            subjectService.findOwnedSubject(subjectId);
        }
        String trimmedQuery = query.strip();
        SearchTerms terms = SearchTerms.parseQuery(trimmedQuery);
        if (terms.isEmpty()) {
            return new SearchPageResponse(trimmedQuery, List.of(), page, size, 0, 0);
        }
        long userId = currentUser.getUserId();
        long total = itemSearchRepository.countMatchingItems(userId, subjectId, terms);
        List<SearchResultResponse> results = total == 0 ? List.of()
                : itemSearchRepository.findMatchingItems(userId, subjectId, terms, size, (long) page * size).stream()
                        .map(row -> toResult(row, terms))
                        .toList();
        int totalPages = (int) ((total + size - 1) / size);
        return new SearchPageResponse(trimmedQuery, results, page, size, total, totalPages);
    }

    private static SearchResultResponse toResult(SearchRow row, SearchTerms terms) {
        return new SearchResultResponse(
                row.itemId(),
                row.title(),
                ItemType.valueOf(row.itemType()),
                Difficulty.valueOf(row.difficulty()),
                ItemStatus.valueOf(row.status()),
                row.subjectId(),
                row.subjectName(),
                row.topicId(),
                row.topicName(),
                buildSnippet(row, terms));
    }

    /** Snippet from the first of question, answer and notes that contains a matching word. */
    private static List<SnippetPart> buildSnippet(SearchRow row, SearchTerms terms) {
        List<String> texts = Stream.of(ItemContent.toPlainText(row.questionHtml()), row.answerPlain(),
                        ItemContent.toPlainText(row.notesHtml()))
                .filter(text -> text != null && !text.isBlank())
                .toList();
        for (String text : texts) {
            List<SnippetPart> snippet = SnippetBuilder.buildSnippet(text, terms);
            if (snippet.stream().anyMatch(SnippetPart::match)) {
                return snippet;
            }
        }
        return texts.isEmpty() ? List.of() : SnippetBuilder.buildSnippet(texts.get(0), terms);
    }
}
