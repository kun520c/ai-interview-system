package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** One FINAL/association invariant for acceptance, execution and completion. No writes. */
@Component
@RequiredArgsConstructor
public class InterviewReportFinalReader {
    private final AnswerEvaluationMapper answerEvaluationMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAnswerMapper interviewAnswerMapper;

    public FinalEvaluationAggregate aggregateFinalEvaluations(InterviewSession session) {
        List<AnswerEvaluation> evaluations = answerEvaluationMapper.listFinalEffectiveEvaluationsBySessionId(session.getId());
        validateEvaluations(session, evaluations);
        return new FinalEvaluationAggregate(evaluations, calculateOverallScore(evaluations));
    }

    public record FinalEvaluationAggregate(List<AnswerEvaluation> evaluations, BigDecimal overallScore) {}

    private void validateEvaluations(
            InterviewSession session,
            List<AnswerEvaluation> evaluations
    ) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalStateException(
                    "面试缺少有效Evaluation"
            );
        }
        if (session.getPlannedQuestionCount() == null
                || evaluations.size()
                != session.getPlannedQuestionCount()) {
            throw new IllegalStateException(
                    "有效Evaluation数量与计划的MAIN题数不一致"
            );
        }
        Set<Long> mainIds = new HashSet<>();
        for (AnswerEvaluation evaluation : evaluations) {
            if (evaluation == null || evaluation.getEvaluationPhase() != EvaluationPhase.FINAL) {
                throw new IllegalStateException("报告评价必须为FINAL");
            }
            if (evaluation.getMainInterviewQuestionId() == null
                    || !mainIds.add(evaluation.getMainInterviewQuestionId())) {
                throw new IllegalStateException("每道MAIN必须恰有一个FINAL评价");
            }
            if (evaluation.getDecisionAction() != DecisionAction.NEXT_MAIN
                    && evaluation.getDecisionAction() != DecisionAction.FINISH) {
                throw new IllegalStateException("FINAL评价决策非法");
            }
        }
        validateEvaluationAssociations(session, evaluations, mainIds);
    }

    private void validateEvaluationAssociations(
            InterviewSession session, List<AnswerEvaluation> evaluations, Set<Long> mainIds
    ) {
        List<InterviewQuestion> questions = interviewQuestionMapper.listBySessionId(session.getId());
        List<InterviewQuestion> mains = questions.stream()
                .filter(question -> question.getQuestionType() == InterviewQuestionType.MAIN)
                .sorted(Comparator.comparing(InterviewQuestion::getPlanOrder))
                .toList();
        if (mains.size() != session.getPlannedQuestionCount()
                || !mainIds.equals(new HashSet<>(mains.stream().map(InterviewQuestion::getId).toList()))) {
            throw new IllegalStateException("FINAL评价未完整覆盖本场MAIN计划");
        }

        Map<Long, InterviewQuestion> followUps = new HashMap<>();
        for (InterviewQuestion question : questions) {
            if (!Objects.equals(question.getSessionId(), session.getId())
                    || question.getStatus() != InterviewQuestionStatus.ANSWERED) {
                throw new IllegalStateException("报告问题不属于本场或尚未回答");
            }
            if (question.getQuestionType() == InterviewQuestionType.FOLLOW_UP) {
                if (!mainIds.contains(question.getParentQuestionId())
                        || followUps.put(question.getParentQuestionId(), question) != null) {
                    throw new IllegalStateException("报告追问必须属于本场MAIN且每题最多一个");
                }
            }
        }
        Map<Long, InterviewAnswer> answers = new HashMap<>();
        for (InterviewAnswer answer : interviewAnswerMapper.listByInterviewQuestionIds(
                questions.stream().map(InterviewQuestion::getId).toList())) {
            answers.put(answer.getInterviewQuestionId(), answer);
        }
        for (int index = 0; index < mains.size(); index++) {
            InterviewQuestion main = mains.get(index);
            AnswerEvaluation evaluation = evaluations.get(index);
            if (!Objects.equals(main.getPlanOrder(), index + 1)
                    || !Objects.equals(main.getId(), evaluation.getMainInterviewQuestionId())) {
                throw new IllegalStateException("报告MAIN计划或评价顺序非法");
            }
            InterviewAnswer mainAnswer = requireEvaluatedAnswer(answers.get(main.getId()));
            InterviewQuestion followUp = followUps.get(main.getId());
            InterviewAnswer finalAnswer = followUp == null ? mainAnswer
                    : requireEvaluatedAnswer(answers.get(followUp.getId()));
            if (!Objects.equals(evaluation.getAnswerId(), finalAnswer.getId())) {
                throw new IllegalStateException("FINAL评价未关联所属MAIN的最终答案");
            }
            if (followUp != null) {
                AnswerEvaluation initial = answerEvaluationMapper.getByAnswerId(mainAnswer.getId());
                if (initial == null || initial.getEvaluationPhase() != EvaluationPhase.INITIAL
                        || initial.getDecisionAction() != DecisionAction.FOLLOW_UP
                        || !Objects.equals(initial.getMainInterviewQuestionId(), main.getId())) {
                    throw new IllegalStateException("追问缺少所属MAIN的INITIAL评价");
                }
            }
        }
    }

    private InterviewAnswer requireEvaluatedAnswer(InterviewAnswer answer) {
        if (answer == null || answer.getStatus() != InterviewAnswerStatus.EVALUATED) {
            throw new IllegalStateException("报告答案缺失或尚未完成评价");
        }
        return answer;
    }

    private BigDecimal calculateOverallScore(
            List<AnswerEvaluation> evaluations
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (AnswerEvaluation evaluation : evaluations) {
            if (evaluation == null
                    || evaluation.getTotalScore() == null) {
                throw new IllegalStateException(
                        "有效Evaluation缺少totalScore"
                );
            }
            total = total.add(
                    BigDecimal.valueOf(
                            evaluation.getTotalScore()
                    )
            );
        }

        return total.divide(
                BigDecimal.valueOf(evaluations.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

}
