package com.studytrack.api.progress;

import com.studytrack.api.item.Difficulty;
import com.studytrack.api.item.ItemStatus;
import com.studytrack.api.item.ItemType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ItemProgressRepository extends JpaRepository<ItemProgressEntity, Long> {

    Optional<ItemProgressEntity> findByUserIdAndItemId(Long userId, Long itemId);

    /**
     * Flashcards of a subject that are due before {@code dueBefore}: reviews scheduled before then
     * (overdue first), then items never reviewed that are not DONE, in topic and item order.
     */
    @Query("""
            select i.id as id, i.topicId as topicId, t.name as topicName, i.itemType as itemType,
                   i.title as title, i.questionHtml as questionHtml, i.answerHtml as answerHtml,
                   i.notesHtml as notesHtml, i.difficulty as difficulty, p.status as status,
                   p.reviewCount as reviewCount, p.nextReviewAt as nextReviewAt
            from StudyItemEntity i
            join TopicEntity t on t.id = i.topicId
            left join ItemProgressEntity p on p.itemId = i.id and p.userId = :userId
            where t.subjectId = :subjectId
              and (p.nextReviewAt < :dueBefore
                   or (p.nextReviewAt is null and (p.id is null or p.status <> com.studytrack.api.item.ItemStatus.DONE)))
            order by case when p.nextReviewAt is null then 1 else 0 end, p.nextReviewAt,
                     t.sortOrder, t.id, i.sortOrder, i.id
            """)
    List<DueItemRow> findDueItems(Long subjectId, Long userId, Instant dueBefore, Pageable pageable);

    /** Due and new flashcard counts per subject, with the same rule as {@link #findDueItems}. */
    @Query("""
            select t.subjectId as subjectId,
                   sum(case when p.nextReviewAt < :dueBefore then 1 else 0 end) as dueCount,
                   sum(case when p.nextReviewAt is null then 1 else 0 end) as newCount
            from StudyItemEntity i
            join TopicEntity t on t.id = i.topicId
            join SubjectEntity s on s.id = t.subjectId
            left join ItemProgressEntity p on p.itemId = i.id and p.userId = :userId
            where s.userId = :userId
              and (p.nextReviewAt < :dueBefore
                   or (p.nextReviewAt is null and (p.id is null or p.status <> com.studytrack.api.item.ItemStatus.DONE)))
            group by t.subjectId
            """)
    List<SubjectDueCount> countDueItemsBySubject(Long userId, Instant dueBefore);

    interface DueItemRow {
        Long getId();

        Long getTopicId();

        String getTopicName();

        ItemType getItemType();

        String getTitle();

        String getQuestionHtml();

        String getAnswerHtml();

        String getNotesHtml();

        Difficulty getDifficulty();

        ItemStatus getStatus();

        Integer getReviewCount();

        Instant getNextReviewAt();
    }

    interface SubjectDueCount {
        Long getSubjectId();

        long getDueCount();

        long getNewCount();
    }
}
