package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.common.validation.Utf8ByteSize;
import com.kun.aiinterview.common.validation.Utf8ByteSizeValidator;
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
import com.kun.aiinterview.interview.vo.InterviewDetailAnswerResponse;
import com.kun.aiinterview.interview.vo.InterviewDetailQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewDetailResponse;
import com.kun.aiinterview.interview.vo.InterviewEvaluationResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryItemResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryPageResponse;
import com.kun.aiinterview.interview.vo.InterviewQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewSessionResponse;
import com.kun.aiinterview.interview.vo.SubmitInterviewAnswerResponse;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewService {

    static final String SESSION_NOT_FOUND = "面试会话不存在";
    static final String QUESTION_NOT_FOUND = "面试问题不存在";

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

    public InterviewHistoryPageResponse listHistory(
            Long userId,
            Integer page,
            Integer pageSize
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId必须大于0"
            );
        }

        int resolvedPage = page == null ? 1 : page;
        int resolvedPageSize = pageSize == null ? 10 : pageSize;

        if (resolvedPage < 1) {
            throw new BusinessException(
                    "页码必须大于等于1"
            );
        }

        if (resolvedPageSize < 1 || resolvedPageSize > 50) {
            throw new BusinessException(
                    "每页数量必须在1到50之间"
            );
        }

        long offset = (long) (resolvedPage - 1) * resolvedPageSize;
        long total = interviewSessionMapper
                .countCompletedSessionsByUserId(userId);

        List<InterviewHistoryItemResponse> items = List.of();
        if (total > 0 && offset < total) {
            List<InterviewSession> sessions =
                    interviewSessionMapper
                            .listCompletedSessionsByUserId(
                                    userId,
                                    resolvedPageSize,
                                    offset
                            );
            items = buildHistoryItems(sessions, userId);
        }

        long totalPages = total == 0
                ? 0L
                : (total + resolvedPageSize - 1) / resolvedPageSize;

        return new InterviewHistoryPageResponse(
                resolvedPage,
                resolvedPageSize,
                total,
                totalPages,
                List.copyOf(items)
        );
    }

    public InterviewDetailResponse getDetail(
            Long userId,
            Long sessionId
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

        InterviewSession session = loadOwnedSession(sessionId, userId);

        List<InterviewQuestion> questions =
                interviewQuestionMapper.listBySessionId(sessionId);
        if (questions == null || questions.isEmpty()) {
            return buildDetailResponse(session, List.of());
        }

        validateQuestions(sessionId, questions);

        List<Long> questionIds = questions.stream()
                .map(InterviewQuestion::getId)
                .toList();
        Map<Long, InterviewAnswer> answersByQuestionId =
                loadAnswersByQuestionId(questionIds);
        Map<Long, AnswerEvaluation> evaluationsByAnswerId =
                loadEvaluationsByAnswerId(answersByQuestionId);

        List<InterviewDetailQuestionResponse> questionResponses =
                new ArrayList<>(questions.size());
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer =
                    answersByQuestionId.get(question.getId());
            AnswerEvaluation evaluation = answer == null
                    ? null
                    : evaluationsByAnswerId.get(answer.getId());
            questionResponses.add(
                    buildDetailQuestion(question, answer, evaluation)
            );
        }

        return buildDetailResponse(
                session,
                List.copyOf(questionResponses)
        );
    }

    public SubmitInterviewAnswerResponse submitAnswer(
            Long userId,
            Long sessionId,
            SubmitInterviewAnswerRequest request
    ) {
        validateSubmitRequest(userId, sessionId, request);

        InterviewSession session = loadOwnedSession(sessionId, userId);

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
                    existingByRequestId,
                    request.answerContent()
            );
        }

        validateNormalSubmission(session, question);

        InterviewAnswer existingByQuestion =
                interviewAnswerMapper
                        .getInterviewAnswerByInterviewQuestionId(
                                question.getId()
                        );

        if (existingByQuestion != null) {
            throw new ConflictException(
                    "当前问题已经使用其他requestId提交过答案"
            );
        }

        if (question.getStatus()
                != InterviewQuestionStatus.WAITING_ANSWER) {
            throw new ConflictException(
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

        if (!Utf8ByteSizeValidator.isWithinLimit(
                request.answerContent(),
                Utf8ByteSize.MYSQL_TEXT_MAX_BYTES
        )) {
            throw new BusinessException(
                    "answerContent不能超过65535个UTF-8字节"
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

    private InterviewSession loadOwnedSession(Long sessionId, Long userId) {
        InterviewSession session =
                interviewSessionMapper
                        .getInterviewSessionById(sessionId);

        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new ResourceNotFoundException(SESSION_NOT_FOUND);
        }

        return session;
    }

    private void validateNormalSubmission(
            InterviewSession session,
            InterviewQuestion question
    ) {
        if (session.getStatus()
                != InterviewSessionStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "当前InterviewSession不可提交答案"
            );
        }

        if (!Objects.equals(
                session.getCurrentInterviewQuestionId(),
                question.getId()
        )) {
            throw new ConflictException(
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

        if (question == null
                || !Objects.equals(question.getSessionId(), sessionId)) {
            throw new ResourceNotFoundException(QUESTION_NOT_FOUND);
        }

        return question;
    }

    private SubmitInterviewAnswerResponse handleExistingAnswer(
            Long userId,
            InterviewSession session,
            InterviewQuestion question,
            InterviewAnswer answer,
            String submittedAnswerContent
    ) {
        if (!Objects.equals(
                answer.getInterviewQuestionId(),
                question.getId()
        )) {
            throw new ConflictException(
                    "requestId已用于其他问题"
            );
        }

        if (!Objects.equals(
                answer.getAnswerContent(),
                submittedAnswerContent
        )) {
            throw new ConflictException(
                    "requestId已用于不同的答案内容"
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
            case EVALUATING -> throw new ConflictException(
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
            InterviewSession latestSession = loadOwnedSession(sessionId, userId);
            return handleExistingAnswer(
                    userId,
                    latestSession,
                    question,
                    existingByRequestId,
                    request.answerContent()
            );
        }

        InterviewAnswer existingByQuestion =
                interviewAnswerMapper
                        .getInterviewAnswerByInterviewQuestionId(
                                question.getId()
                        );

        if (existingByQuestion != null) {
            throw new ConflictException(
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

        InterviewSession latestSession = loadOwnedSession(sessionId, userId);

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

    private List<InterviewHistoryItemResponse> buildHistoryItems(
            List<InterviewSession> sessions,
            Long userId
    ) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        List<InterviewHistoryItemResponse> items =
                new ArrayList<>(sessions.size());
        for (InterviewSession session : sessions) {
            if (session == null || session.getId() == null) {
                throw new IllegalStateException(
                        "历史InterviewSession数据不完整"
                );
            }

            if (!Objects.equals(session.getUserId(), userId)) {
                throw new IllegalStateException(
                        "历史InterviewSession不属于当前用户"
                );
            }

            if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
                throw new IllegalStateException(
                        "历史InterviewSession状态不是COMPLETED"
                );
            }

            items.add(new InterviewHistoryItemResponse(
                    session.getId(),
                    session.getDifficulty(),
                    session.getStatus(),
                    session.getReportStatus(),
                    session.getTotalScore(),
                    session.getPlannedQuestionCount(),
                    session.getCompletedQuestionCount(),
                    session.getStartedAt(),
                    session.getEndedAt(),
                    session.getCreatedAt()
            ));
        }

        return List.copyOf(items);
    }

    private void validateQuestions(
            Long sessionId,
            List<InterviewQuestion> questions
    ) {
        for (InterviewQuestion question : questions) {
            if (question == null
                    || question.getId() == null
                    || question.getQuestionType() == null
                    || question.getCategory() == null
                    || question.getKnowledgePoint() == null
                    || question.getQuestionContent() == null
                    || question.getDisplayOrder() == null
                    || question.getStatus() == null) {
                throw new IllegalStateException(
                        "InterviewQuestion数据不完整"
                );
            }

            if (!Objects.equals(question.getSessionId(), sessionId)) {
                throw new IllegalStateException(
                        "InterviewQuestion不属于当前Session"
                );
            }
        }
    }

    private Map<Long, InterviewAnswer> loadAnswersByQuestionId(
            List<Long> questionIds
    ) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }

        List<InterviewAnswer> answers =
                interviewAnswerMapper.listByInterviewQuestionIds(
                        questionIds
                );
        if (answers == null || answers.isEmpty()) {
            return Map.of();
        }

        Map<Long, InterviewAnswer> answersByQuestionId =
                new LinkedHashMap<>();
        for (InterviewAnswer answer : answers) {
            if (answer == null
                    || answer.getId() == null
                    || answer.getInterviewQuestionId() == null
                    || answer.getAnswerContent() == null
                    || answer.getStatus() == null) {
                throw new IllegalStateException(
                        "InterviewAnswer数据不完整"
                );
            }

            if (!questionIds.contains(answer.getInterviewQuestionId())) {
                throw new IllegalStateException(
                        "InterviewAnswer不属于当前Session问题"
                );
            }

            InterviewAnswer previous = answersByQuestionId.put(
                    answer.getInterviewQuestionId(),
                    answer
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "同一InterviewQuestion存在多条Answer"
                );
            }
        }

        return Map.copyOf(answersByQuestionId);
    }

    private Map<Long, AnswerEvaluation> loadEvaluationsByAnswerId(
            Map<Long, InterviewAnswer> answersByQuestionId
    ) {
        if (answersByQuestionId == null || answersByQuestionId.isEmpty()) {
            return Map.of();
        }

        List<Long> answerIds = answersByQuestionId.values().stream()
                .map(InterviewAnswer::getId)
                .toList();
        if (answerIds.isEmpty()) {
            return Map.of();
        }

        List<AnswerEvaluation> evaluations =
                answerEvaluationMapper.listByAnswerIds(answerIds);
        if (evaluations == null || evaluations.isEmpty()) {
            return Map.of();
        }

        Map<Long, AnswerEvaluation> evaluationsByAnswerId =
                new HashMap<>();
        for (AnswerEvaluation evaluation : evaluations) {
            if (evaluation == null
                    || evaluation.getId() == null
                    || evaluation.getAnswerId() == null) {
                throw new IllegalStateException(
                        "AnswerEvaluation数据不完整"
                );
            }

            if (!answerIds.contains(evaluation.getAnswerId())) {
                throw new IllegalStateException(
                        "AnswerEvaluation不属于当前Session答案"
                );
            }

            AnswerEvaluation previous = evaluationsByAnswerId.put(
                    evaluation.getAnswerId(),
                    evaluation
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "同一Answer存在多条Evaluation"
                );
            }
        }

        return Map.copyOf(evaluationsByAnswerId);
    }

    private InterviewDetailResponse buildDetailResponse(
            InterviewSession session,
            List<InterviewDetailQuestionResponse> questions
    ) {
        if (session.getId() == null
                || session.getDifficulty() == null
                || session.getStatus() == null
                || session.getReportStatus() == null
                || session.getPlannedQuestionCount() == null
                || session.getCompletedQuestionCount() == null) {
            throw new IllegalStateException(
                    "InterviewSession数据不完整"
            );
        }

        return new InterviewDetailResponse(
                session.getId(),
                session.getDifficulty(),
                session.getStatus(),
                session.getReportStatus(),
                session.getTotalScore(),
                session.getPlannedQuestionCount(),
                session.getCompletedQuestionCount(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt(),
                questions == null ? List.of() : List.copyOf(questions)
        );
    }

    private InterviewDetailQuestionResponse buildDetailQuestion(
            InterviewQuestion question,
            InterviewAnswer answer,
            AnswerEvaluation evaluation
    ) {
        InterviewDetailAnswerResponse answerResponse = answer == null
                ? null
                : new InterviewDetailAnswerResponse(
                        answer.getAnswerContent(),
                        answer.getStatus(),
                        answer.getSubmittedAt()
                );
        InterviewEvaluationResponse evaluationResponse = evaluation == null
                ? null
                : buildEvaluationResponse(evaluation);

        return new InterviewDetailQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getCategory(),
                question.getKnowledgePoint(),
                question.getQuestionContent(),
                question.getPlanOrder(),
                question.getDisplayOrder(),
                question.getStatus(),
                answerResponse,
                evaluationResponse
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
            throw new IllegalStateException(
                    "InterviewSession不属于当前用户"
            );
        }
    }
}
