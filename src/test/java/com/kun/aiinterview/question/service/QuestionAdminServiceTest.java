package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAdminServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionScoringPointMapper questionScoringPointMapper;

    @InjectMocks
    private QuestionAdminService questionAdminService;

    @Test
    void shouldCreateQuestionAndPreserveScoringPointOrder() {
        CreateQuestionRequest request = requestWith(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "说明数组和桶结构",
                        60
                ),
                new ScoringPointRequest(
                        QuestionPointType.KEY,
                        "说明哈希冲突处理",
                        40
                )
        );
        when(questionMapper.insertQuestion(any(Question.class)))
                .thenAnswer(invocation -> {
                    Question question = invocation.getArgument(0);
                    question.setId(101L);
                    return 1;
                });
        when(questionScoringPointMapper.batchInsert(anyList()))
                .thenReturn(2);

        Long questionId = questionAdminService.createQuestion(request);

        ArgumentCaptor<Question> questionCaptor =
                ArgumentCaptor.forClass(Question.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionScoringPoint>> pointsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(questionMapper).insertQuestion(questionCaptor.capture());
        verify(questionScoringPointMapper).batchInsert(
                pointsCaptor.capture()
        );

        Question question = questionCaptor.getValue();
        List<QuestionScoringPoint> points = pointsCaptor.getValue();
        assertAll(
                () -> assertEquals(101L, questionId),
                () -> assertEquals(QuestionStatus.ENABLED, question.getStatus()),
                () -> assertEquals(2, points.size()),
                () -> assertEquals(101L, points.get(0).getQuestionId()),
                () -> assertEquals(QuestionPointType.CORE, points.get(0).getPointType()),
                () -> assertEquals("说明数组和桶结构", points.get(0).getContent()),
                () -> assertEquals(60, points.get(0).getWeight()),
                () -> assertEquals(1, points.get(0).getSortOrder()),
                () -> assertEquals(QuestionPointStatus.ENABLED, points.get(0).getStatus()),
                () -> assertEquals(101L, points.get(1).getQuestionId()),
                () -> assertEquals(QuestionPointType.KEY, points.get(1).getPointType()),
                () -> assertEquals("说明哈希冲突处理", points.get(1).getContent()),
                () -> assertEquals(40, points.get(1).getWeight()),
                () -> assertEquals(2, points.get(1).getSortOrder()),
                () -> assertEquals(QuestionPointStatus.ENABLED, points.get(1).getStatus())
        );
    }

    @Test
    void shouldRejectEmptyScoringPointListBeforeDatabaseWrite() {
        CreateQuestionRequest request = requestWith();

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectNullRequestBeforeDatabaseWrite() {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(null)
        );
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectNullScoringPointBeforeDatabaseWrite() {
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .scoringPoints(Collections.singletonList(null))
                .build();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );

        assertEquals("评分点参数不完整", exception.getMessage());
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectMissingWeightBeforeDatabaseWrite() {
        CreateQuestionRequest request = requestWith(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "核心结论",
                        null
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );

        assertEquals("评分点参数不完整", exception.getMessage());
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void shouldRejectOutOfRangeWeightBeforeDatabaseWrite(int weight) {
        CreateQuestionRequest request = requestWith(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "核心结论",
                        weight
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );

        assertEquals("评分点权重必须在1到100之间", exception.getMessage());
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectWeightTotalOtherThanOneHundredBeforeDatabaseWrite() {
        CreateQuestionRequest request = requestWith(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "核心结论",
                        99
                )
        );

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectUnexpectedQuestionInsertCount() {
        CreateQuestionRequest request = requestWith(
                validScoringPoint()
        );
        when(questionMapper.insertQuestion(any(Question.class)))
                .thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );
        verify(questionScoringPointMapper, never()).batchInsert(anyList());
    }

    @Test
    void shouldRejectMissingGeneratedQuestionId() {
        CreateQuestionRequest request = requestWith(
                validScoringPoint()
        );
        when(questionMapper.insertQuestion(any(Question.class)))
                .thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );
        verify(questionScoringPointMapper, never()).batchInsert(anyList());
    }

    @Test
    void shouldRejectUnexpectedScoringPointInsertCount() {
        CreateQuestionRequest request = requestWith(
                validScoringPoint()
        );
        when(questionMapper.insertQuestion(any(Question.class)))
                .thenAnswer(invocation -> {
                    Question question = invocation.getArgument(0);
                    question.setId(102L);
                    return 1;
                });
        when(questionScoringPointMapper.batchInsert(anyList()))
                .thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );
    }

    @Test
    void shouldDeclareCreateQuestionAsTransactional() throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "createQuestion",
                CreateQuestionRequest.class
        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    private CreateQuestionRequest requestWith(
            ScoringPointRequest... scoringPoints
    ) {
        return CreateQuestionRequest.builder()
                .knowledgePoint("Java 集合")
                .questionContent("请说明 HashMap 的核心原理")
                .referenceAnswer("HashMap 基于数组和链表或红黑树实现")
                .scoringPoints(List.of(scoringPoints))
                .build();
    }

    private ScoringPointRequest validScoringPoint() {
        return new ScoringPointRequest(
                QuestionPointType.CORE,
                "说明核心结论",
                100
        );
    }
}
