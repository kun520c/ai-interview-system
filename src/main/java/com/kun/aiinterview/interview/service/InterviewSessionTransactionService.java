package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewSessionTransactionService {

    private final UserMapper userMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewQuestionSnapshotFactory snapshotFactory;

    @Transactional
    public InterviewSession createAndStartSession(
            InterviewSession session,
            List<InterviewMainQuestionDraft> drafts
    ) {
        validate(session, drafts);

        lockUser(session.getUserId());

        InterviewSession existingSession =
                interviewSessionMapper.getActiveSessionByUserId(
                        session.getUserId()
                );

        if (existingSession != null) {
            return existingSession;
        }

        insertSession(session);

        List<InterviewQuestion> mainQuestions =
                buildMainQuestions(session.getId(), drafts);

        insertMainQuestions(mainQuestions);

        InterviewQuestion firstQuestion =
                interviewQuestionMapper.getMainQuestionByPlanOrder(
                        session.getId(),
                        1
                );

        validateFirstQuestion(firstQuestion, session.getId());

        activateFirstQuestion(firstQuestion, session.getId());

        startSession(session, firstQuestion.getId());

        InterviewSession startedSession =
                interviewSessionMapper.getInterviewSessionById(
                        session.getId()
                );
        validateStartedSession(
                startedSession,
                session,
                firstQuestion.getId()
        );
        return startedSession;
    }

    private void lockUser(Long userId) {
        User user = userMapper.getUserByIdForUpdate(userId);

        if (user == null) {
            throw new IllegalStateException(
                    "user does not exist: " + userId
            );
        }
    }

    private void insertSession(InterviewSession session) {
        int affectedRows =
                interviewSessionMapper.insertInterviewSession(session);

        if (affectedRows != 1 || session.getId() == null) {
            throw new IllegalStateException(
                    "failed to insert interview session"
            );
        }
    }

    private List<InterviewQuestion> buildMainQuestions(
            Long sessionId,
            List<InterviewMainQuestionDraft> drafts
    ) {
        List<InterviewQuestion> questions =
                new ArrayList<>(drafts.size());

        for (int i = 0; i < drafts.size(); i++) {
            InterviewMainQuestionDraft draft = drafts.get(i);

            InterviewQuestion question =
                    snapshotFactory.createMainQuestion(
                            sessionId,
                            draft.question(),
                            draft.scoringPoints(),
                            i + 1
                    );

            questions.add(question);
        }

        return List.copyOf(questions);
    }

    private void insertMainQuestions(
            List<InterviewQuestion> mainQuestions
    ) {
        int affectedRows =
                interviewQuestionMapper.batchInsertMainQuestions(
                        mainQuestions
                );

        if (affectedRows != mainQuestions.size()) {
            throw new IllegalStateException(
                    "failed to insert complete interview question plan"
            );
        }
    }

    private void activateFirstQuestion(
            InterviewQuestion firstQuestion,
            Long sessionId
    ) {
        int affectedRows =
                interviewQuestionMapper.markWaitingAnswer(
                        firstQuestion.getId(),
                        sessionId
                );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "failed to activate first interview question"
            );
        }
    }

    private void startSession(
            InterviewSession session,
            Long firstQuestionId
    ) {
        int affectedRows =
                interviewSessionMapper.startSession(
                        session.getId(),
                        session.getVersion(),
                        firstQuestionId
                );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "failed to start interview session"
            );
        }
    }

    private void validate(
            InterviewSession session,
            List<InterviewMainQuestionDraft> drafts
    ) {
        if (session == null) {
            throw new IllegalArgumentException(
                    "session must not be null"
            );
        }

        if (session.getUserId() == null) {
            throw new IllegalArgumentException(
                    "session.userId must not be null"
            );
        }

        if (session.getVersion() == null) {
            throw new IllegalArgumentException(
                    "session.version must not be null"
            );
        }

        if (session.getId() != null
                || session.getDifficulty() == null
                || session.getStatus() != InterviewSessionStatus.CREATED
                || session.getReportStatus() != InterviewReportStatus.NOT_STARTED
                || session.getVersion() != 0
                || session.getCurrentInterviewQuestionId() != null
                || session.getCompletedQuestionCount() == null
                || session.getCompletedQuestionCount() != 0
                || session.getTotalScore() != null
                || session.getStartedAt() != null
                || session.getEndedAt() != null) {
            throw new IllegalArgumentException(
                    "session must be a new CREATED session"
            );
        }

        if (session.getPlannedQuestionCount() == null
                || session.getPlannedQuestionCount() <= 0) {
            throw new IllegalArgumentException(
                    "plannedQuestionCount must be positive"
            );
        }

        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException(
                    "question drafts must not be empty"
            );
        }

        if (drafts.size()
                != session.getPlannedQuestionCount()) {
            throw new IllegalStateException(
                    "question draft count must equal plannedQuestionCount"
            );
        }

        for (int index = 0; index < drafts.size(); index++) {
            if (drafts.get(index) == null) {
                throw new IllegalArgumentException(
                        "question draft must not be null at index " + index
                );
            }
        }
    }

    private void validateFirstQuestion(
            InterviewQuestion firstQuestion,
            Long sessionId
    ) {
        if (firstQuestion == null
                || firstQuestion.getId() == null
                || firstQuestion.getQuestionType() != InterviewQuestionType.MAIN
                || !Objects.equals(firstQuestion.getPlanOrder(), 1)
                || !Objects.equals(firstQuestion.getSessionId(), sessionId)
                || firstQuestion.getStatus() != InterviewQuestionStatus.PENDING) {
            throw new IllegalStateException(
                    "invalid first MAIN question persisted for session"
            );
        }
    }

    private void validateStartedSession(
            InterviewSession startedSession,
            InterviewSession requestedSession,
            Long firstQuestionId
    ) {
        if (startedSession == null
                || !Objects.equals(startedSession.getId(), requestedSession.getId())
                || !Objects.equals(startedSession.getUserId(), requestedSession.getUserId())
                || startedSession.getDifficulty() != requestedSession.getDifficulty()
                || startedSession.getStatus() != InterviewSessionStatus.IN_PROGRESS
                || !Objects.equals(
                        startedSession.getCurrentInterviewQuestionId(),
                        firstQuestionId
                )
                || !Objects.equals(startedSession.getVersion(), 1)
                || !Objects.equals(startedSession.getCompletedQuestionCount(), 0)
                || startedSession.getStartedAt() == null
                || !Objects.equals(
                        startedSession.getPlannedQuestionCount(),
                        requestedSession.getPlannedQuestionCount()
                )) {
            throw new IllegalStateException(
                    "started interview session state is invalid"
            );
        }
    }
}
