package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewWorkflowTransactionService {

    private final AnswerEvaluationMapper answerEvaluationMapper;
    private final InterviewAnswerMapper interviewAnswerMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;

    @Transactional
    public InterviewAnswer submitAnswer(
            InterviewAnswer answer,
            InterviewQuestion currentQuestion
    ) {
        if (answer == null) {
            throw new IllegalArgumentException(
                    "InterviewAnswer不能为空"
            );
        }

        requireQuestionIdentity(currentQuestion);

        answer.setId(null);
        answer.setInterviewQuestionId(
                currentQuestion.getId()
        );
        answer.setStatus(
                InterviewAnswerStatus.SUBMITTED
        );
        answer.setErrorCode(null);

        if (answer.getSubmittedAt() == null) {
            answer.setSubmittedAt(
                    LocalDateTime.now()
            );
        }

        int answerRows =
                interviewAnswerMapper
                        .insertInterviewAnswer(answer);

        if (answerRows != 1) {
            throw new IllegalStateException(
                    "Answer写入失败"
            );
        }

        if (answer.getId() == null) {
            throw new IllegalStateException(
                    "Answer主键未回填"
            );
        }

        int questionRows =
                interviewQuestionMapper.markAnswered(
                        currentQuestion.getId(),
                        currentQuestion.getSessionId()
                );

        if (questionRows != 1) {
            throw new IllegalStateException(
                    "Question状态更新为ANSWERED失败"
            );
        }

        return answer;
    }

    @Transactional
    public boolean tryClaimEvaluation(Long answerId, String attemptId) {
        int affectedRows =
                interviewAnswerMapper.claimEvaluation(answerId, attemptId);
        return affectedRows == 1;
    }

    @Transactional
    public boolean tryRetryEvaluation(Long answerId, String attemptId) {
        int affectedRows =
                interviewAnswerMapper.retryEvaluation(answerId, attemptId);
        return affectedRows == 1;
    }

    @Transactional
    public boolean tryReclaimStaleEvaluating(
            Long answerId,
            LocalDateTime cutoff,
            String attemptId
    ) {
        if (answerId == null || cutoff == null) {
            throw new IllegalArgumentException(
                    "answerId和cutoff不能为空"
            );
        }

        int affectedRows =
                interviewAnswerMapper.reclaimStaleEvaluating(
                        answerId,
                        cutoff,
                        attemptId
                );
        return affectedRows == 1;
    }

    @Transactional
    public void markEvaluationFailed(
            Long answerId,
            String errorCode,
            String attemptId
    ) {
        int affectedRows =
                interviewAnswerMapper.markFailed(
                        answerId,
                        errorCode,
                        attemptId
                );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "Answer失败状态更新失败"
            );
        }
    }

    @Transactional
    public InterviewQuestion moveToFollowUp(
            Long answerId,
            InterviewQuestion parentMain,
            InterviewSession session,
            InterviewQuestion followUpDraft,
            String attemptId,
            AnswerEvaluation evaluation
    ) {
        requireQuestionIdentity(parentMain);
        requireSessionIdentity(session);

        if (parentMain.getQuestionType()
                != InterviewQuestionType.MAIN) {
            throw new IllegalArgumentException(
                    "只有MAIN问题可以创建FOLLOW_UP"
            );
        }

        if (parentMain.getDisplayOrder() == null) {
            throw new IllegalArgumentException(
                    "MAIN问题displayOrder不能为空"
            );
        }

        if (!Objects.equals(
                parentMain.getSessionId(),
                session.getId()
        )) {
            throw new IllegalArgumentException(
                    "MAIN问题不属于当前Session"
            );
        }

        InterviewQuestion followUp =
                interviewQuestionMapper
                        .getFollowUpByParentQuestionId(
                                parentMain.getId()
                        );

        if (followUp != null) {
            validateExistingFollowUp(
                    followUp,
                    parentMain,
                    session
            );

            if (followUp.getStatus()
                            == InterviewQuestionStatus.WAITING_ANSWER
                    && Objects.equals(
                            session.getCurrentInterviewQuestionId(),
                            followUp.getId()
                    )) {
                persistOwnedEvaluation(answerId, attemptId, evaluation);
                return followUp;
            }
        }

        if (!Objects.equals(
                session.getCurrentInterviewQuestionId(),
                parentMain.getId()
        )) {
            throw new IllegalStateException(
                    "Session当前问题不是目标MAIN"
            );
        }

        persistOwnedEvaluation(answerId, attemptId, evaluation);

        if (followUp == null) {
            followUp = prepareFollowUp(
                    followUpDraft,
                    parentMain,
                    session
            );

            int insertRows =
                    interviewQuestionMapper
                            .insertFollowUp(followUp);

            if (insertRows != 1) {
                throw new IllegalStateException(
                        "FOLLOW_UP问题写入失败"
                );
            }

            if (followUp.getId() == null) {
                throw new IllegalStateException(
                        "FOLLOW_UP问题主键未回填"
                );
            }
        }

        int answerRows =
                interviewAnswerMapper.markEvaluated(answerId, attemptId);

        if (answerRows != 1) {
            throw new IllegalStateException(
                    "Answer状态更新为EVALUATED失败"
            );
        }

        int questionRows =
                interviewQuestionMapper.markWaitingAnswer(
                        followUp.getId(),
                        session.getId()
                );

        if (questionRows != 1) {
            throw new IllegalStateException(
                    "FOLLOW_UP问题激活失败"
            );
        }

        int sessionRows =
                interviewSessionMapper.moveToFollowUp(
                        session.getId(),
                        session.getVersion(),
                        parentMain.getId(),
                        followUp.getId()
                );

        if (sessionRows != 1) {
            throw new IllegalStateException(
                    "Session推进到FOLLOW_UP失败"
            );
        }

        return followUp;
    }

    @Transactional
    public void advanceToNextMain(
            Long answerId,
            InterviewQuestion currentQuestion,
            InterviewSession session,
            InterviewQuestion nextMain,
            String attemptId,
            AnswerEvaluation evaluation
    ) {
        validateCurrentQuestionAndSession(
                currentQuestion,
                session
        );
        requireQuestionIdentity(nextMain);

        if (nextMain.getQuestionType()
                != InterviewQuestionType.MAIN
                || nextMain.getPlanOrder() == null) {
            throw new IllegalArgumentException(
                    "下一道问题必须是带planOrder的MAIN"
            );
        }

        if (!Objects.equals(
                nextMain.getSessionId(),
                session.getId()
        )) {
            throw new IllegalArgumentException(
                    "下一道MAIN不属于当前Session"
            );
        }

        persistOwnedEvaluation(answerId, attemptId, evaluation);

        int answerRows =
                interviewAnswerMapper.markEvaluated(answerId, attemptId);

        if (answerRows != 1) {
            throw new IllegalStateException(
                    "Answer状态更新为EVALUATED失败"
            );
        }

        int nextQuestionRows =
                interviewQuestionMapper.markWaitingAnswer(
                        nextMain.getId(),
                        session.getId()
                );

        if (nextQuestionRows != 1) {
            throw new IllegalStateException(
                    "下一道MAIN激活失败"
            );
        }

        int  sessionRows =
                interviewSessionMapper.advanceToNextMain(
                        session.getId(),
                        session.getVersion(),
                        currentQuestion.getId(),
                        nextMain.getId()
                );

        if (sessionRows != 1) {
            throw new IllegalStateException(
                    "Session推进到下一道MAIN失败"
            );
        }
    }

    @Transactional
    public void finishSession(
            Long answerId,
            InterviewQuestion currentQuestion,
            InterviewSession session,
            String attemptId,
            AnswerEvaluation evaluation
    ) {
        validateCurrentQuestionAndSession(
                currentQuestion,
                session
        );

        persistOwnedEvaluation(answerId, attemptId, evaluation);

        int answerRows =
                interviewAnswerMapper.markEvaluated(answerId, attemptId);

        if (answerRows != 1) {
            throw new IllegalStateException(
                    "Answer状态更新为EVALUATED失败"
            );
        }

        int sessionRows =
                interviewSessionMapper.completeSession(
                        session.getId(),
                        session.getVersion(),
                        currentQuestion.getId()
                );

        if (sessionRows != 1) {
            throw new IllegalStateException(
                    "Session完成失败"
            );
        }
    }

    private void persistOwnedEvaluation(Long answerId, String attemptId, AnswerEvaluation evaluation) {
        InterviewAnswer current = interviewAnswerMapper.lockById(answerId);
        if (attemptId == null || current == null
                || current.getStatus() != InterviewAnswerStatus.EVALUATING
                || !attemptId.equals(current.getAttemptId())) {
            throw new ConflictException("评价任务已失效，请重试");
        }
        if (evaluation == null || !Objects.equals(answerId, evaluation.getAnswerId())) {
            throw new IllegalArgumentException("评价结果不属于当前Answer");
        }
        AnswerEvaluation existing = answerEvaluationMapper.getByAnswerId(answerId);
        if (existing != null) {
            if (!Objects.equals(existing.getId(), evaluation.getId())) {
                throw new ConflictException("评价结果已发生变化，请重试");
            }
            return; // Legacy persisted evaluations are replayed under the current attempt.
        }
        if (evaluation.getId() != null
                || answerEvaluationMapper.insertEvaluation(evaluation) != 1) {
            throw new IllegalStateException("AnswerEvaluation写入失败");
        }
        if (evaluation.getId() == null) {
            throw new IllegalStateException("AnswerEvaluation主键未回填");
        }
    }

    private InterviewQuestion prepareFollowUp(
            InterviewQuestion followUpDraft,
            InterviewQuestion parentMain,
            InterviewSession session
    ) {
        if (followUpDraft == null) {
            throw new IllegalArgumentException(
                    "FOLLOW_UP问题不能为空"
            );
        }

        if (followUpDraft.getFollowUpTargetPoints() == null
                || followUpDraft
                        .getFollowUpTargetPoints()
                        .isBlank()) {
            throw new IllegalArgumentException(
                    "FOLLOW_UP目标评分点不能为空"
            );
        }

        if (followUpDraft.getQuestionContent() == null
                || followUpDraft.getQuestionContent().isBlank()) {
            throw new IllegalArgumentException(
                    "FOLLOW_UP问题内容不能为空"
            );
        }

        followUpDraft.setId(null);
        followUpDraft.setSessionId(session.getId());
        followUpDraft.setQuestionId(null);
        followUpDraft.setCategory(parentMain.getCategory());
        followUpDraft.setKnowledgePoint(
                parentMain.getKnowledgePoint()
        );
        followUpDraft.setReferenceAnswerSnapshot(null);
        followUpDraft.setScoringPointsSnapshot(null);
        followUpDraft.setQuestionType(
                InterviewQuestionType.FOLLOW_UP
        );
        followUpDraft.setParentQuestionId(
                parentMain.getId()
        );
        followUpDraft.setPlanOrder(null);
        followUpDraft.setDisplayOrder(
                Math.addExact(
                        parentMain.getDisplayOrder(),
                        1
                )
        );
        followUpDraft.setStatus(
                InterviewQuestionStatus.PENDING
        );

        return followUpDraft;
    }

    private void validateExistingFollowUp(
            InterviewQuestion followUp,
            InterviewQuestion parentMain,
            InterviewSession session
    ) {
        if (followUp.getId() == null
                || followUp.getQuestionType()
                        != InterviewQuestionType.FOLLOW_UP
                || !Objects.equals(
                        followUp.getParentQuestionId(),
                        parentMain.getId()
                )
                || !Objects.equals(
                        followUp.getSessionId(),
                        session.getId()
                )
                || followUp.getPlanOrder() != null
                || followUp.getFollowUpTargetPoints() == null
                || followUp.getFollowUpTargetPoints().isBlank()
                || !Objects.equals(
                        followUp.getDisplayOrder(),
                        Math.addExact(
                                parentMain.getDisplayOrder(),
                                1
                        )
                )) {
            throw new IllegalStateException(
                    "已有FOLLOW_UP问题数据非法"
            );
        }
    }

    private void requireQuestionIdentity(
            InterviewQuestion question
    ) {
        if (question == null
                || question.getId() == null
                || question.getSessionId() == null) {
            throw new IllegalArgumentException(
                    "InterviewQuestion标识不能为空"
            );
        }
    }

    private void requireSessionIdentity(
            InterviewSession session
    ) {
        if (session == null
                || session.getId() == null
                || session.getVersion() == null) {
            throw new IllegalArgumentException(
                    "InterviewSession标识和version不能为空"
            );
        }
    }

    private void validateCurrentQuestionAndSession(
            InterviewQuestion currentQuestion,
            InterviewSession session
    ) {
        requireQuestionIdentity(currentQuestion);
        requireSessionIdentity(session);

        if (!Objects.equals(
                currentQuestion.getSessionId(),
                session.getId()
        )) {
            throw new IllegalArgumentException(
                    "当前问题不属于当前Session"
            );
        }

        if (!Objects.equals(
                currentQuestion.getId(),
                session.getCurrentInterviewQuestionId()
        )) {
            throw new IllegalStateException(
                    "Session当前问题与待推进问题不一致"
            );
        }
    }

}
