package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.vo.InterviewEvaluationResponse;
import com.kun.aiinterview.interview.vo.InterviewQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewSessionResponse;
import com.kun.aiinterview.interview.vo.SubmitInterviewAnswerResponse;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final InterviewSessionService interviewSessionService;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAnswerMapper interviewAnswerMapper;
    private final InterviewWorkflowTransactionService
            interviewWorkflowTransactionService;
    private final AnswerEvaluationMapper answerEvaluationMapper;
    private final ObjectProvider<InterviewWorkflowService>
            interviewWorkflowServiceProvider;
    private final ObjectMapper objectMapper;

    public InterviewSessionResponse createOrResume(
            Long userId,
            QuestionDifficulty difficulty
    ) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }

        if (difficulty == null) {
            throw new IllegalArgumentException(
                    "difficulty不能为空"
            );
        }

        InterviewSession session =
                interviewSessionService.createSession(
                        userId,
                        difficulty
                );

        validateOwnership(session, userId);

        return buildSessionResponse(session);
    }

    public InterviewSessionResponse getCurrent(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }

        InterviewSession session =
                interviewSessionMapper
                        .getActiveSessionByUserId(userId);

        if (session == null) {
            return null;
        }

        validateOwnership(session, userId);

        return buildSessionResponse(session);
    }

    public SubmitInterviewAnswerResponse submitAnswer(
            Long userId,
            Long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        validateSubmitRequest(userId, sessionId, request);

        InterviewSession session = loadSession(sessionId);
        validateOwnership(session, userId);

        InterviewQuestion question = loadAndValidateQuestion(
                sessionId,
                request.interviewQuestionId()
        );

        InterviewAnswer existingByRequestId =
                interviewAnswerMapper
                        .getInterviewAnswerByRequestId(
                                request.requestId()
                        );

        if (existingByRequestId != null) {
            return handleExistingAnswer(
                    userId,
                    session,
                    question,
                    existingByRequestId
            );
        }

        validateNormalSubmission(session, question);

        InterviewAnswer existingByQuestion =
                interviewAnswerMapper
                        .getInterviewAnswerByInterviewQuestionId(
                                question.getId()
                        );

        if (existingByQuestion != null) {
            throw new BusinessException(
                    "当前问题已经使用其他requestId提交过答案"
            );
        }

        if (question.getStatus()
                != InterviewQuestionStatus.WAITING_ANSWER) {
            throw new BusinessException(
                    "当前问题不可提交答案"
            );
        }

        InterviewWorkflowService workflowService =
                requireWorkflowService();
        InterviewAnswer answer = buildAnswer(question, request);

        try {
            answer = interviewWorkflowTransactionService
                    .submitAnswer(answer, question);
        } catch (DuplicateKeyException exception) {
            return recoverFromDuplicateKey(
                    userId,
                    sessionId,
                    question,
                    request,
                    exception
            );
        }

        workflowService.evaluateAnswer(answer.getId());

        return buildSubmitAnswerResponse(
                userId,
                sessionId,
                answer.getId()
        );
    }

    private void validateSubmitRequest(
            Long userId,
            Long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId必须大于0"
            );
        }

        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException(
                    "sessionId必须大于0"
            );
        }

        if (request == null) {
            throw new BusinessException(
                    "提交答案请求不能为空"
            );
        }

        if (request.interviewQuestionId() == null
                || request.interviewQuestionId() <= 0) {
            throw new BusinessException(
                    "interviewQuestionId必须大于0"
            );
        }

        if (request.answerContent() == null
                || request.answerContent().isBlank()) {
            throw new BusinessException(
                    "answerContent不能为空"
            );
        }

        if (request.requestId() == null
                || request.requestId().isBlank()) {
            throw new BusinessException(
                    "requestId不能为空"
            );
        }

        if (request.requestId().length() > 64) {
            throw new BusinessException(
                    "requestId长度不能超过64位"
            );
        }
    }

    private InterviewSession loadSession(Long sessionId) {
        InterviewSession session =
                interviewSessionMapper
                        .getInterviewSessionById(sessionId);

        if (session == null) {
            throw new BusinessException(
                    "InterviewSession不存在"
            );
        }

        return session;
    }

    private void validateNormalSubmission(
            InterviewSession session,
            InterviewQuestion question
    ) {
        if (session.getStatus()
                != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "当前InterviewSession不可提交答案"
            );
        }

        if (!Objects.equals(
                session.getCurrentInterviewQuestionId(),
                question.getId()
        )) {
            throw new BusinessException(
                    "提交的问题不是当前问题"
            );
        }
    }

    private InterviewQuestion loadAndValidateQuestion(
            Long sessionId,
            Long interviewQuestionId
    ) {
        InterviewQuestion question =
                interviewQuestionMapper
                        .getInterviewQuestionById(
                                interviewQuestionId
                        );

        if (question == null) {
            throw new BusinessException(
                    "InterviewQuestion不存在"
            );
        }

        if (!Objects.equals(
                question.getSessionId(),
                sessionId
        )) {
            throw new BusinessException(
                    "InterviewQuestion不属于当前Session"
            );
        }

        return question;
    }

    private SubmitInterviewAnswerResponse handleExistingAnswer(
            Long userId,
            InterviewSession session,
            InterviewQuestion question,
            InterviewAnswer answer
    ) {
        if (!Objects.equals(
                answer.getInterviewQuestionId(),
                question.getId()
        )) {
            throw new BusinessException(
                    "requestId已用于其他问题"
            );
        }

        if (answer.getId() == null || answer.getStatus() == null) {
            throw new IllegalStateException(
                    "已存在的InterviewAnswer数据不完整"
            );
        }

        return switch (answer.getStatus()) {
            case EVALUATED -> buildSubmitAnswerResponse(
                    userId,
                    session.getId(),
                    answer.getId()
            );
            case EVALUATING -> throw new BusinessException(
                    "答案正在评估中，请稍后重试"
            );
            case SUBMITTED, FAILED -> {
                validateNormalSubmission(session, question);
                requireWorkflowService()
                        .evaluateAnswer(answer.getId());
                yield buildSubmitAnswerResponse(
                        userId,
                        session.getId(),
                        answer.getId()
                );
            }
        };
    }

    private InterviewAnswer buildAnswer(
            InterviewQuestion question,
            SubmitInterviewAnswerRequest request
    ) {
        return InterviewAnswer.builder()
                .interviewQuestionId(question.getId())
                .answerContent(request.answerContent())
                .requestId(request.requestId())
                .build();
    }

    private InterviewWorkflowService requireWorkflowService() {
        InterviewWorkflowService workflowService =
                interviewWorkflowServiceProvider.getIfAvailable();

        if (workflowService == null) {
            throw new BusinessException(
                    "面试评价服务当前不可用"
            );
        }

        return workflowService;
    }

    private SubmitInterviewAnswerResponse recoverFromDuplicateKey(
            Long userId,
            Long sessionId,
            InterviewQuestion question,
            SubmitInterviewAnswerRequest request,
            DuplicateKeyException duplicateKeyException
    ) {
        InterviewAnswer existingByRequestId =
                interviewAnswerMapper
                        .getInterviewAnswerByRequestId(
                                request.requestId()
                        );

        if (existingByRequestId != null) {
            InterviewSession latestSession = loadSession(sessionId);
            validateOwnership(latestSession, userId);
            return handleExistingAnswer(
                    userId,
                    latestSession,
                    question,
                    existingByRequestId
            );
        }

        InterviewAnswer existingByQuestion =
                interviewAnswerMapper
                        .getInterviewAnswerByInterviewQuestionId(
                                question.getId()
                        );

        if (existingByQuestion != null) {
            throw new BusinessException(
                    "当前问题已经使用其他requestId提交过答案",
                    duplicateKeyException
            );
        }

        throw new IllegalStateException(
                "Answer唯一约束冲突后未找到已存在记录",
                duplicateKeyException
        );
    }

    private SubmitInterviewAnswerResponse buildSubmitAnswerResponse(
            Long userId,
            Long sessionId,
            Long answerId
    ) {
        AnswerEvaluation evaluation =
                answerEvaluationMapper.getByAnswerId(answerId);

        if (evaluation == null) {
            throw new IllegalStateException(
                    "AnswerEvaluation不存在"
            );
        }

        if (!Objects.equals(evaluation.getAnswerId(), answerId)) {
            throw new IllegalStateException(
                    "AnswerEvaluation不属于当前Answer"
            );
        }

        InterviewSession latestSession = loadSession(sessionId);
        validateOwnership(latestSession, userId);

        InterviewQuestionResponse nextQuestion =
                latestSession.getStatus()
                        == InterviewSessionStatus.COMPLETED
                        ? null
                        : loadCurrentQuestion(latestSession);

        return new SubmitInterviewAnswerResponse(
                buildEvaluationResponse(evaluation),
                evaluation.getDecisionAction(),
                latestSession.getStatus(),
                nextQuestion
        );
    }

    private InterviewEvaluationResponse buildEvaluationResponse(
            AnswerEvaluation evaluation
    ) {
        if (evaluation.getEvaluationPhase() == null
                || evaluation.getDecisionAction() == null) {
            throw new IllegalStateException(
                    "AnswerEvaluation缺少阶段或决策"
            );
        }

        EvaluationLevel level;
        try {
            level = EvaluationLevel.valueOf(evaluation.getLevel());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException(
                    "AnswerEvaluation中的level非法",
                    exception
            );
        }

        return new InterviewEvaluationResponse(
                evaluation.getEvaluationPhase(),
                evaluation.getCorrectnessScore(),
                evaluation.getCompletenessScore(),
                evaluation.getDepthScore(),
                evaluation.getClarityScore(),
                evaluation.getPracticeScore(),
                evaluation.getTotalScore(),
                level,
                parseStringList(
                        evaluation.getStrengths(),
                        "strengths"
                ),
                parseStringList(
                        evaluation.getMissingPoints(),
                        "missingPoints"
                ),
                evaluation.getCorrection()
        );
    }

    private List<String> parseStringList(
            String json,
            String fieldName
    ) {
        if (json == null) {
            return List.of();
        }

        if (json.isBlank()) {
            throw new IllegalStateException(
                    "AnswerEvaluation中的" + fieldName + " JSON为空"
            );
        }

        try {
            List<String> values = objectMapper.readValue(
                    json,
                    STRING_LIST_TYPE
            );

            if (values == null) {
                throw new IllegalStateException(
                        "AnswerEvaluation中的" + fieldName + "为空"
                );
            }

            return List.copyOf(values);
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalStateException(
                    "AnswerEvaluation中的" + fieldName + " JSON非法",
                    exception
            );
        }
    }

    private InterviewSessionResponse buildSessionResponse(
            InterviewSession session
    ) {
        if (session == null) {
            throw new IllegalStateException(
                    "InterviewSession不能为空"
            );
        }

        if (session.getId() == null) {
            throw new IllegalStateException(
                    "InterviewSession缺少id"
            );
        }

        InterviewQuestionResponse currentQuestion =
                loadCurrentQuestion(session);

        return new InterviewSessionResponse(
                session.getId(),
                session.getDifficulty(),
                session.getStatus(),
                session.getPlannedQuestionCount(),
                session.getCompletedQuestionCount(),
                currentQuestion
        );
    }

    private InterviewQuestionResponse loadCurrentQuestion(
            InterviewSession session
    ) {
        Long currentQuestionId =
                session.getCurrentInterviewQuestionId();

        if (currentQuestionId == null) {
            return null;
        }

        InterviewQuestion question =
                interviewQuestionMapper
                        .getInterviewQuestionById(
                                currentQuestionId
                        );

        if (question == null) {
            throw new IllegalStateException(
                    "Session当前问题不存在"
            );
        }

        if (!Objects.equals(
                question.getSessionId(),
                session.getId()
        )) {
            throw new IllegalStateException(
                    "当前问题不属于当前Session"
            );
        }

        return new InterviewQuestionResponse(
                question.getId(),
                question.getCategory(),
                question.getKnowledgePoint(),
                question.getQuestionType(),
                question.getQuestionContent()
        );
    }

    private void validateOwnership(
            InterviewSession session,
            Long userId
    ) {
        if (session == null) {
            throw new IllegalStateException(
                    "InterviewSession不能为空"
            );
        }

        if (!Objects.equals(
                session.getUserId(),
                userId
        )) {
            throw new BusinessException(
                    "InterviewSession不属于当前用户"
            );
        }
    }
}
