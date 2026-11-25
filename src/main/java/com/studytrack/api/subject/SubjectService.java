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
        List<Long> subjectIds = subjects.stream().map(SubjectEntity::getId).toList();
        Map<Long, Long> topicCountsBySubject = subjectRepository.countTopicsBySubject(subjectIds).stream()
                .collect(Collectors.toMap(SubjectRepository.SubjectCount::getSubjectId,
                        SubjectRepository.SubjectCount::getTopicCount));
        Map<Long, Long> itemCountsBySubject = subjectRepository.countItemsBySubject(subjectIds).stream()
                .collect(Collectors.toMap(SubjectRepository.SubjectItemCount::getSubjectId,
                        SubjectRepository.SubjectItemCount::getItemCount));
        return subjects.stream()
                .map(subject -> toResponse(subject,
                        topicCountsBySubject.getOrDefault(subject.getId(), 0L),
                        itemCountsBySubject.getOrDefault(subject.getId(), 0L)))
                .toList();
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
        return toResponse(subjectRepository.save(subject), 0, 0);
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
        List<Long> subjectIds = List.of(subject.getId());
        long topicCount = subjectRepository.countTopicsBySubject(subjectIds).stream()
                .mapToLong(SubjectRepository.SubjectCount::getTopicCount).sum();
        long itemCount = subjectRepository.countItemsBySubject(subjectIds).stream()
                .mapToLong(SubjectRepository.SubjectItemCount::getItemCount).sum();
        return toResponse(subject, topicCount, itemCount);
    }

    private SubjectResponse toResponse(SubjectEntity subject, long topicCount, long itemCount) {
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getDescription(), subject.getIcon(),
                subject.getSortOrder(), subject.isArchived(), topicCount, itemCount,
                subject.getCreatedAt(), subject.getUpdatedAt());
    }
}
