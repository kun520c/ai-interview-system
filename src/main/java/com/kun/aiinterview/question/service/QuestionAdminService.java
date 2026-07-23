package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionAdminService {
    private final QuestionMapper questionMapper;
    private final QuestionScoringPointMapper questionScoringPointMapper;

    @Transactional
    public Long createQuestion(CreateQuestionRequest request) {
        if (request == null) {
            throw new BusinessException("请求参数不能为空");
        }

        List<ScoringPointRequest> scoringPointRequests = request.getScoringPoints();

        if (scoringPointRequests == null || scoringPointRequests.isEmpty()) {
            throw new BusinessException("题目至少需要一个评分点");
        }

        long totalWeight = 0;

        for (ScoringPointRequest scoringPointRequest : scoringPointRequests) {
            if (scoringPointRequest == null
                    || scoringPointRequest.getWeight() == null) {
                throw new BusinessException("评分点参数不完整");
            }

            int weight = scoringPointRequest.getWeight();
            if (weight < 1 || weight > 100) {
                throw new BusinessException("评分点权重必须在1到100之间");
            }

            totalWeight += weight;
        }

        if (totalWeight != 100L) {
            throw new BusinessException("评分点权重和必须为100");
        }

        Question question = Question.builder()
                .category(request.getCategory())
                .knowledgePoint(request.getKnowledgePoint())
                .difficulty(request.getDifficulty())
                .questionContent(request.getQuestionContent())
                .referenceAnswer(request.getReferenceAnswer())
                .status(QuestionStatus.ENABLED)
                .build();

        int affectedRows = questionMapper.insertQuestion(question);

        if (affectedRows != 1) {
            throw new BusinessException("题目创建失败");
        }

        Long questionId = question.getId();
        if (questionId == null) {
            throw new BusinessException("题目主键回填失败");
        }

        List<QuestionScoringPoint> scoringPoints = getScoringPoints(
                request.getScoringPoints(),
                questionId
        );

        int insertRows = questionScoringPointMapper.batchInsert(scoringPoints);

        if (insertRows != scoringPoints.size()) {
            throw new BusinessException("评分点创建失败");
        }

        return questionId;
    }

    @Transactional
    public void updateQuestion(
            Long questionId,
            UpdateQuestionRequest request
    ) {
        if (request == null) {
            throw new BusinessException("请求参数不能为空");
        }

        if (questionId == null || questionId <= 0) {
            throw new BusinessException("题目ID不合法");
        }

        List<ScoringPointRequest> scoringPointRequests = request.getScoringPoints();

        if (scoringPointRequests == null || scoringPointRequests.isEmpty()) {
            throw new BusinessException("题目至少需要一个评分点");
        }

        long totalWeight = 0;

        for (ScoringPointRequest scoringPointRequest : scoringPointRequests) {
            if (scoringPointRequest == null
                    || scoringPointRequest.getWeight() == null) {
                throw new BusinessException("评分点参数不完整");
            }

            int weight = scoringPointRequest.getWeight();

            if (weight < 1 || weight > 100) {
                throw new BusinessException("评分点权重必须在1到100之间");
            }

            totalWeight += weight;
        }

        if (totalWeight != 100L) {
            throw new BusinessException("评分点权重和必须为100");
        }

        Question existingQuestion = questionMapper.getQuestionById(questionId);

        if (existingQuestion == null) {
            throw new BusinessException("题目不存在");
        }

        Question question = Question.builder()
                .id(questionId)
                .category(request.getCategory())
                .knowledgePoint(request.getKnowledgePoint())
                .difficulty(request.getDifficulty())
                .questionContent(request.getQuestionContent())
                .referenceAnswer(request.getReferenceAnswer())
                .build();

        questionMapper.updateQuestion(question);

        int deleteRows = questionScoringPointMapper.deleteByQuestionId(questionId);

        if (deleteRows <= 0) {
            throw new BusinessException("原评分点删除失败");
        }

        List<QuestionScoringPoint> scoringPoints = getScoringPoints(
                request.getScoringPoints(),
                questionId
        );

        int insertRows = questionScoringPointMapper.batchInsert(scoringPoints);

        if (insertRows != scoringPoints.size()) {
            throw new BusinessException("评分点更新失败");
        }
    }

    private List<QuestionScoringPoint> getScoringPoints(
            List<ScoringPointRequest> scoringPointRequests,
            Long questionId
    ) {
        List<QuestionScoringPoint> scoringPoints = new ArrayList<>();

        for (int i = 0; i < scoringPointRequests.size(); i++) {
            ScoringPointRequest pointRequest = scoringPointRequests.get(i);

            QuestionScoringPoint questionScoringPoint = QuestionScoringPoint.builder()
                    .questionId(questionId)
                    .pointType(pointRequest.getPointType())
                    .content(pointRequest.getContent())
                    .weight(pointRequest.getWeight())
                    .sortOrder(i + 1)
                    .status(QuestionPointStatus.ENABLED)
                    .build();

            scoringPoints.add(questionScoringPoint);
        }
        return scoringPoints;
    }
}
