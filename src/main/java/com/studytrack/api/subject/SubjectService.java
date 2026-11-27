package com.studytrack.api.subject;

import com.studytrack.api.subject.SubjectDtos.SubjectRequest;
import com.studytrack.api.subject.SubjectDtos.SubjectResponse;
import com.studytrack.api.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final CurrentUser currentUser;

    public SubjectService(SubjectRepository subjectRepository, CurrentUser currentUser) {
        this.subjectRepository = subjectRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects() {
        List<SubjectEntity> subjects = subjectRepository.findByUserIdOrderBySortOrderAscNameAsc(currentUser.getUserId());
        if (subjects.isEmpty()) {
            return List.of();
        }
        SubjectCounts counts = readCounts(subjects.stream().map(SubjectEntity::getId).toList());
        return subjects.stream().map(subject -> toResponse(subject, counts)).toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubject(Long subjectId) {
        return toResponseWithCounts(findOwnedSubject(subjectId));
    }

    @Transactional
    public SubjectResponse createSubject(SubjectRequest request) {
        long userId = currentUser.getUserId();
        String name = request.name().strip();
        if (subjectRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateName(name);
        }
        SubjectEntity subject = new SubjectEntity(userId);
        applyRequest(subject, request, name);
        subject.setSortOrder(request.sortOrder() != null
                ? request.sortOrder()
                : subjectRepository.findMaxSortOrder(userId) + 1);
        SubjectEntity saved = subjectRepository.save(subject);
        return toResponse(saved, SubjectCounts.EMPTY);
    }

    /**
     * Creates a subject copied from a catalog subject. Fails with 409 when the user already copied it or
     * already has a subject with that name.
     */
    @Transactional
    public SubjectResponse createSubjectFromCatalog(Long catalogSubjectId, String name, String description,
                                                    String icon) {
        long userId = currentUser.getUserId();
        if (!subjectRepository.findByUserIdAndCatalogSubjectIdIn(userId, List.of(catalogSubjectId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already added this subject");
        }
        if (subjectRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw duplicateName(name);
        }
        SubjectEntity subject = new SubjectEntity(userId);
        subject.setName(name);
        subject.setDescription(blankToNull(description));
        subject.setIcon(blankToNull(icon));
        subject.setCatalogSubjectId(catalogSubjectId);
        subject.setSortOrder(subjectRepository.findMaxSortOrder(userId) + 1);
        return toResponse(subjectRepository.save(subject), SubjectCounts.EMPTY);
    }

    /** Maps each given catalog subject id the user already copied to the id of the user's subject. */
    @Transactional(readOnly = true)
    public Map<Long, Long> mapCopiedSubjects(List<Long> catalogSubjectIds) {
        if (catalogSubjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectRepository.findByUserIdAndCatalogSubjectIdIn(currentUser.getUserId(), catalogSubjectIds)
                .stream()
                .collect(Collectors.toMap(SubjectEntity::getCatalogSubjectId, SubjectEntity::getId));
    }

    @Transactional
    public SubjectResponse replaceSubject(Long subjectId, SubjectRequest request) {
        SubjectEntity subject = findOwnedSubject(subjectId);
        String name = request.name().strip();
        if (subjectRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(subject.getUserId(), name, subjectId)) {
            throw duplicateName(name);
        }
        applyRequest(subject, request, name);
        if (request.sortOrder() != null) {
            subject.setSortOrder(request.sortOrder());
        }
        return toResponseWithCounts(subjectRepository.saveAndFlush(subject));
    }

    /** Deletes the subject with all its topics and items (database cascade). */
    @Transactional
    public void deleteSubject(Long subjectId) {
        subjectRepository.delete(findOwnedSubject(subjectId));
    }

    /** Returns the subject if it belongs to the current user; other users' subjects are reported as missing. */
    @Transactional(readOnly = true)
    public SubjectEntity findOwnedSubject(Long subjectId) {
        return subjectRepository.findByIdAndUserId(subjectId, currentUser.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Subject " + subjectId + " not found"));
    }

    private void applyRequest(SubjectEntity subject, SubjectRequest request, String name) {
        subject.setName(name);
        subject.setDescription(blankToNull(request.description()));
        subject.setIcon(blankToNull(request.icon()));
        subject.setArchived(Boolean.TRUE.equals(request.archived()));
    }

    private ResponseStatusException duplicateName(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A subject named \"" + name + "\" already exists");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private SubjectResponse toResponseWithCounts(SubjectEntity subject) {
        return toResponse(subject, readCounts(List.of(subject.getId())));
    }

    private SubjectCounts readCounts(List<Long> subjectIds) {
        long userId = currentUser.getUserId();
        Map<Long, Long> topicCounts = subjectRepository.countTopicsBySubject(subjectIds).stream()
                .collect(Collectors.toMap(SubjectRepository.SubjectCount::getSubjectId,
                        SubjectRepository.SubjectCount::getTopicCount));
        Map<Long, Long> completedTopicCounts = subjectRepository.countCompletedTopicsBySubject(subjectIds, userId)
                .stream()
                .collect(Collectors.toMap(SubjectRepository.SubjectCount::getSubjectId,
                        SubjectRepository.SubjectCount::getTopicCount));
        Map<Long, SubjectRepository.SubjectItemCount> itemCounts =
                subjectRepository.countItemsBySubject(subjectIds, userId).stream()
                        .collect(Collectors.toMap(SubjectRepository.SubjectItemCount::getSubjectId, Function.identity()));
        return new SubjectCounts(topicCounts, completedTopicCounts, itemCounts);
    }

    private SubjectResponse toResponse(SubjectEntity subject, SubjectCounts counts) {
        Long subjectId = subject.getId();
        SubjectRepository.SubjectItemCount items = counts.itemCounts().get(subjectId);
        long itemCount = items == null ? 0 : items.getItemCount();
        long doneCount = items == null ? 0 : items.getDoneCount();
        long learningCount = items == null ? 0 : items.getLearningCount();
        return new SubjectResponse(subjectId, subject.getName(), subject.getDescription(), subject.getIcon(),
                subject.getSortOrder(), subject.isArchived(), counts.topicCounts().getOrDefault(subjectId, 0L),
                counts.completedTopicCounts().getOrDefault(subjectId, 0L), itemCount, doneCount, learningCount,
                percentOf(doneCount, itemCount), subject.getCreatedAt(), subject.getUpdatedAt());
    }

    /** Whole percent, rounded down so 100 means every item is done. */
    public static int percentOf(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) (part * 100 / total);
    }

    private record SubjectCounts(Map<Long, Long> topicCounts, Map<Long, Long> completedTopicCounts,
                                 Map<Long, SubjectRepository.SubjectItemCount> itemCounts) {
        static final SubjectCounts EMPTY = new SubjectCounts(Map.of(), Map.of(), Map.of());
    }
}
