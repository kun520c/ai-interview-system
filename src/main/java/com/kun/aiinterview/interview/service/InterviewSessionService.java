package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewPlanConfig interviewPlanConfig;
    private final InterviewQuestionPlanSnapshotService snapshotService;
    private final InterviewSessionTransactionService transactionService;

    public InterviewSession createSession(
            Long userId,
            QuestionDifficulty difficulty
    ){
        validateCreateRequest(userId,difficulty);

        InterviewSession activeSession =
                interviewSessionMapper.getActiveSessionByUserId(userId);

        if(activeSession != null){
            return activeSession;
        }

        InterviewPlanConfig.InterviewPlanRule rule =
                interviewPlanConfig.getRule(difficulty);

        List<InterviewMainQuestionDraft> drafts =
                snapshotService.prepareSnapshot(difficulty, rule);

        if (drafts.size() != rule.plannedQuestionCount()) {
            throw new IllegalStateException(
                    "question draft count must equal plannedQuestionCount"
            );
        }

        InterviewSession session =
                buildCreatedSession(
                        userId,
                        difficulty,
                        rule.plannedQuestionCount()
                );

        return transactionService.createAndStartSession(
                session,
                drafts
        );
    }

    private void validateCreateRequest(
            Long userId,
            QuestionDifficulty difficulty
    ){
        if(userId == null){
            throw new IllegalArgumentException(
                    "userId must not be null"
            );
        }

        if(difficulty == null){
            throw new IllegalArgumentException(
                    "difficulty must not be null"
            );
        }
    }

    private InterviewSession buildCreatedSession(
            Long userId,
            QuestionDifficulty difficulty,
            int plannedQuestionCount
    ){
        return InterviewSession.builder()
                .userId(userId)
                .difficulty(difficulty)
                .status(InterviewSessionStatus.CREATED)
                .currentInterviewQuestionId(null)
                .plannedQuestionCount(plannedQuestionCount)
                .completedQuestionCount(0)
                .totalScore(null)
                .reportStatus(InterviewReportStatus.NOT_STARTED)
                .version(0)
                .startedAt(null)
                .endedAt(null)
                .build();
    }

}
