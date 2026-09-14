package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionScoringPointMapper questionScoringPointMapper;

    private final InterviewPlanConfig interviewPlanConfig;
    private final QuestionPlanSelector questionPlanSelector;
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

        List<Question> candidates =
                questionMapper.selectEnabledQuestionsForInterview(difficulty);

        List<Question> selectedQuestions =
                questionPlanSelector.select(candidates,rule);

        validateSelectedQuestionCount(
                selectedQuestions,
                rule.plannedQuestionCount()
        );

        List<InterviewMainQuestionDraft> drafts =
                prepareMainQuestionDrafts(selectedQuestions);

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

    private List<InterviewMainQuestionDraft> prepareMainQuestionDrafts(
            List<Question> selectedQuestions
    ){
        List<InterviewMainQuestionDraft> drafts =
                new ArrayList<>();

        for(Question question : selectedQuestions){
            if (question == null || question.getId() == null) {
                throw new IllegalStateException(
                        "selected question and question.id must not be null"
                );
            }

            List<QuestionScoringPoint> scoringPoints =
                    questionScoringPointMapper
                            .selectEnabledByQuestionId(question.getId());

            if(scoringPoints == null || scoringPoints.isEmpty()){
                throw new IllegalStateException(
                        "question has no enabled scoring points:"
                            + question.getId()
                );
            }

            drafts.add(
                    new InterviewMainQuestionDraft(
                            question,
                            scoringPoints
                    )
            );
        }

        return List.copyOf(drafts);
    }

    private void validateSelectedQuestionCount(
            List<Question> selectedQuestions,
            int plannedQuestionCount
    ) {
        if (selectedQuestions == null
                || selectedQuestions.size() != plannedQuestionCount) {
            throw new IllegalStateException(
                    "selected question count must equal plannedQuestionCount"
            );
        }
    }
}
