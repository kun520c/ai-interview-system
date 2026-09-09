package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.evaluation.decision.EvaluationDecision;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {
                "milvus.enabled",
                "deepseek.enabled"
        },
        havingValue = "true"
)
public class InterviewWorkflowService {

    private static final String EVALUATION_ERROR_CODE =
            "EVALUATION_ERROR";

    private final InterviewAnswerMapper interviewAnswerMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewWorkflowTransactionService transactionService;
    private final EvaluationOrchestrationService evaluationOrchestrationService;
    private final ObjectMapper objectMapper;

    public EvaluationOrchestrationResult evaluateAnswer(Long answerId) {
        if (answerId == null) {
            throw new IllegalArgumentException(
                    "answerId不能为空"
            );
        }

        InterviewAnswer answer =
                interviewAnswerMapper
                        .getInterviewAnswerById(answerId);

        if (answer == null) {
            throw new IllegalArgumentException(
                    "InterviewAnswer不存在"
            );
        }

        if (!Objects.equals(answer.getId(), answerId)) {
            throw new IllegalStateException(
                    "加载的InterviewAnswer与answerId不一致"
            );
        }

        if (answer.getInterviewQuestionId() == null) {
            throw new IllegalStateException(
                    "InterviewAnswer缺少interviewQuestionId"
            );
        }

        InterviewQuestion currentQuestion =
                interviewQuestionMapper
                        .getInterviewQuestionById(
                                answer.getInterviewQuestionId()
                        );

        if (currentQuestion == null) {
            throw new IllegalArgumentException(
                    "Answer对应的InterviewQuestion不存在"
            );
        }

        if (currentQuestion.getId() == null
                || currentQuestion.getSessionId() == null) {
            throw new IllegalStateException(
                    "InterviewQuestion标识不能为空"
            );
        }

        InterviewSession session =
                interviewSessionMapper
                        .getInterviewSessionById(
                                currentQuestion.getSessionId()
                        );

        if (session == null) {
            throw new IllegalStateException(
                    "InterviewQuestion对应的Session不存在"
            );
        }

        if (!Objects.equals(
                session.getId(),
                currentQuestion.getSessionId()
        ) || session.getVersion() == null) {
            throw new IllegalStateException(
                    "InterviewSession标识和version非法"
            );
        }

        if (!Objects.equals(
                currentQuestion.getId(),
                session.getCurrentInterviewQuestionId()
        )) {
            throw new IllegalStateException(
                    "当前Answer不属于Session正在处理的问题"
            );
        }

        InterviewQuestion mainQuestion =
                resolveMainQuestion(
                        currentQuestion,
                        session
                );

        validateAnswerStatus(answer);

        InterviewQuestion nextMain =
                interviewQuestionMapper
                        .findNextPendingMainQuestion(
                                session.getId(),
                                mainQuestion.getPlanOrder()
                        );

        boolean hasNextMainQuestion = nextMain != null;

        claimEvaluation(answer);

        try {
            EvaluationOrchestrationResult result =
                    evaluationOrchestrationService
                            .evaluate(
                                    answerId,
                                    hasNextMainQuestion
                            );

            applyDecision(
                    answerId,
                    currentQuestion,
                    mainQuestion,
                    session,
                    nextMain,
                    result
            );

            return result;
        } catch (RuntimeException exception) {
            markEvaluationFailed(
                    answerId,
                    exception
            );
            throw exception;
        }
    }

    private void validateAnswerStatus(InterviewAnswer answer) {
        if (answer.getStatus()
                != InterviewAnswerStatus.SUBMITTED
                && answer.getStatus()
                != InterviewAnswerStatus.FAILED) {
            throw new IllegalStateException(
                    "当前Answer状态不允许进入评价"
            );
        }
    }

    private void claimEvaluation(InterviewAnswer answer) {
        if (answer.getStatus()
                == InterviewAnswerStatus.SUBMITTED) {
            transactionService.claimEvaluation(
                    answer.getId()
            );
            return;
        }

        transactionService.retryEvaluation(
                answer.getId()
        );
    }

    private void markEvaluationFailed(
            Long answerId,
            RuntimeException primaryException
    ) {
        try {
            transactionService.markEvaluationFailed(
                    answerId,
                    EVALUATION_ERROR_CODE
            );
        } catch (RuntimeException markFailedException) {
            primaryException.addSuppressed(
                    markFailedException
            );
        }
    }

    private InterviewQuestion resolveMainQuestion(
            InterviewQuestion currentQuestion,
            InterviewSession session
    ) {
        if (currentQuestion.getQuestionType()
                == InterviewQuestionType.MAIN) {
            validateMainQuestion(
                    currentQuestion,
                    session
            );
            return currentQuestion;
        }

        if (currentQuestion.getQuestionType()
                != InterviewQuestionType.FOLLOW_UP) {
            throw new IllegalStateException(
                    "未知InterviewQuestionType"
            );
        }

        if (currentQuestion.getParentQuestionId() == null) {
            throw new IllegalStateException(
                    "FOLLOW_UP缺少parentQuestionId"
            );
        }

        InterviewQuestion mainQuestion =
                interviewQuestionMapper
                        .getInterviewQuestionById(
                                currentQuestion
                                        .getParentQuestionId()
                        );

        if (mainQuestion == null) {
            throw new IllegalStateException(
                    "FOLLOW_UP对应的MAIN不存在"
            );
        }

        validateMainQuestion(
                mainQuestion,
                session
        );

        return mainQuestion;
    }

    private void validateMainQuestion(
            InterviewQuestion mainQuestion,
            InterviewSession session
    ) {
        if (mainQuestion.getQuestionType()
                != InterviewQuestionType.MAIN) {
            throw new IllegalStateException(
                    "FOLLOW_UP的parent不是MAIN"
            );
        }

        if (!Objects.equals(
                mainQuestion.getSessionId(),
                session.getId()
        )) {
            throw new IllegalStateException(
                    "FOLLOW_UP与MAIN不属于同一个Session"
            );
        }

        if (mainQuestion.getPlanOrder() == null) {
            throw new IllegalStateException(
                    "MAIN问题planOrder不能为空"
            );
        }
    }

    private void applyDecision(
            Long answerId,
            InterviewQuestion currentQuestion,
            InterviewQuestion mainQuestion,
            InterviewSession session,
            InterviewQuestion nextMain,
            EvaluationOrchestrationResult result
    ) {
        validateResult(
                answerId,
                mainQuestion,
                result
        );

        switch (result.decisionAction()) {
            case FOLLOW_UP -> handleFollowUp(
                    answerId,
                    currentQuestion,
                    mainQuestion,
                    session,
                    result
            );
            case NEXT_MAIN -> {
                if (nextMain == null) {
                    throw new IllegalStateException(
                            "DecisionAction为NEXT_MAIN但不存在下一道MAIN"
                    );
                }

                transactionService.advanceToNextMain(
                        answerId,
                        currentQuestion,
                        session,
                        nextMain
                );
            }
            case FINISH -> {
                if (nextMain != null) {
                    throw new IllegalStateException(
                            "DecisionAction为FINISH但仍存在下一道MAIN"
                    );
                }

                transactionService.finishSession(
                        answerId,
                        currentQuestion,
                        session
                );
            }
        }
    }

    private void validateResult(
            Long answerId,
            InterviewQuestion mainQuestion,
            EvaluationOrchestrationResult result
    ) {
        if (result == null) {
            throw new IllegalStateException(
                    "评价结果不能为空"
            );
        }

        if (!Objects.equals(
                result.answerId(),
                answerId
        )) {
            throw new IllegalStateException(
                    "评价结果不属于当前Answer"
            );
        }

        if (!Objects.equals(
                result.mainInterviewQuestionId(),
                mainQuestion.getId()
        )) {
            throw new IllegalStateException(
                    "评价结果不属于当前MAIN"
            );
        }

        try {
            new EvaluationDecision(
                    result.evaluationPhase(),
                    result.decisionAction(),
                    result.followUpTargetPointIds()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "评价结果决策非法",
                    exception
            );
        }
    }

    private void handleFollowUp(
            Long answerId,
            InterviewQuestion currentQuestion,
            InterviewQuestion mainQuestion,
            InterviewSession session,
            EvaluationOrchestrationResult result
    ) {
        if (currentQuestion.getQuestionType()
                != InterviewQuestionType.MAIN) {
            throw new IllegalStateException(
                    "只有MAIN评价可以进入FOLLOW_UP"
            );
        }

        if (result.suggestedFollowUp() == null
                || result.suggestedFollowUp().isBlank()) {
            throw new IllegalStateException(
                    "FOLLOW_UP缺少追问内容"
            );
        }

        if (result.followUpTargetPointIds().isEmpty()) {
            throw new IllegalStateException(
                    "FOLLOW_UP缺少目标评分点"
            );
        }

        InterviewQuestion followUpDraft =
                InterviewQuestion.builder()
                        .questionContent(
                                result.suggestedFollowUp()
                        )
                        .followUpTargetPoints(
                                toJson(
                                        result.followUpTargetPointIds()
                                )
                        )
                        .build();

        transactionService.moveToFollowUp(
                answerId,
                mainQuestion,
                session,
                followUpDraft
        );
    }

    private String toJson(List<Long> targetPointIds) {
        try {
            return objectMapper
                    .writeValueAsString(targetPointIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "FOLLOW_UP目标评分点JSON序列化失败",
                    exception
            );
        }
    }
}
