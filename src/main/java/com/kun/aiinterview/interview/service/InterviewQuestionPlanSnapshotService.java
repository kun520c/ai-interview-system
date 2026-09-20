package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewQuestionPlanSnapshotService {

    private final QuestionMapper questionMapper;
    private final QuestionScoringPointMapper questionScoringPointMapper;
    private final QuestionPlanSelector questionPlanSelector;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public List<InterviewMainQuestionDraft> prepareSnapshot(
            QuestionDifficulty difficulty,
            InterviewPlanConfig.InterviewPlanRule rule
    ) {
        if (difficulty == null) {
            throw new IllegalArgumentException(
                    "difficulty must not be null"
            );
        }

        if (rule == null) {
            throw new IllegalArgumentException(
                    "rule must not be null"
            );
        }

        List<Question> candidates =
                questionMapper.selectEnabledQuestionsForInterview(difficulty);
        List<Question> selectedQuestions =
                questionPlanSelector.select(candidates, rule);

        validateSelectedQuestionCount(
                selectedQuestions,
                rule.plannedQuestionCount()
        );

        List<InterviewMainQuestionDraft> drafts =
                new ArrayList<>(selectedQuestions.size());

        for (Question question : selectedQuestions) {
            if (question == null || question.getId() == null) {
                throw new IllegalStateException(
                        "selected question and question.id must not be null"
                );
            }

            List<QuestionScoringPoint> scoringPoints =
                    questionScoringPointMapper
                            .selectEnabledByQuestionId(question.getId());

            if (scoringPoints == null || scoringPoints.isEmpty()) {
                throw new IllegalStateException(
                        "question has no enabled scoring points:"
                                + question.getId()
                );
            }

            drafts.add(new InterviewMainQuestionDraft(
                    question,
                    scoringPoints
            ));
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
