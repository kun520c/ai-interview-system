package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionStatusRequest;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import com.kun.aiinterview.question.vo.AdminQuestionDetailResponse;
import com.kun.aiinterview.question.vo.AdminScoringPointDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectInvalidUpdateQuestionIdBeforeDatabaseAccess(Long questionId) {
        UpdateQuestionRequest request = validUpdateRequest();

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(questionId, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectNullUpdateRequestBeforeDatabaseAccess() {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, null)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectNullUpdateScoringPointListBeforeDatabaseAccess() {
        UpdateQuestionRequest request = updateRequestWith(null);

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectEmptyUpdateScoringPointListBeforeDatabaseAccess() {
        UpdateQuestionRequest request = updateRequestWith(List.of());

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectNullUpdateScoringPointBeforeDatabaseAccess() {
        UpdateQuestionRequest request = updateRequestWith(
                Collections.singletonList(null)
        );

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectMissingUpdateWeightBeforeDatabaseAccess() {
        UpdateQuestionRequest request = updateRequestWith(List.of(
                scoringPoint("缺少权重", null)
        ));

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void shouldRejectOutOfRangeUpdateWeightBeforeDatabaseAccess(int weight) {
        UpdateQuestionRequest request = updateRequestWith(List.of(
                scoringPoint("非法权重", weight)
        ));

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectUpdateWeightTotalOtherThanOneHundred() {
        UpdateQuestionRequest request = updateRequestWith(List.of(
                scoringPoint("评分点一", 60),
                scoringPoint("评分点二", 30)
        ));

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldRejectMissingQuestionBeforeAnyDatabaseWrite() {
        UpdateQuestionRequest request = validUpdateRequest();
        when(questionMapper.getQuestionById(101L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        assertEquals("题目不存在", exception.getMessage());
        verify(questionMapper).getQuestionById(101L);
        verify(questionMapper, never()).updateQuestion(any(Question.class));
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void shouldStopWhenNoOldScoringPointWasDeleted() {
        UpdateQuestionRequest request = validUpdateRequest();
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionMapper.updateQuestion(any(Question.class))).thenReturn(1);
        when(questionScoringPointMapper.deleteByQuestionId(101L)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        assertEquals("原评分点删除失败", exception.getMessage());
        verify(questionMapper).updateQuestion(any(Question.class));
        verify(questionScoringPointMapper, never()).batchInsert(anyList());
    }

    @Test
    void shouldRejectUnexpectedReplacementInsertCount() {
        UpdateQuestionRequest request = validUpdateRequest();
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionMapper.updateQuestion(any(Question.class))).thenReturn(1);
        when(questionScoringPointMapper.deleteByQuestionId(101L)).thenReturn(2);
        when(questionScoringPointMapper.batchInsert(anyList())).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        assertEquals("评分点更新失败", exception.getMessage());
    }

    @Test
    void givenUnexpectedQuestionUpdateCount_whenUpdatingQuestion_thenStopsBeforeReplacingPoints() {
        UpdateQuestionRequest request = validUpdateRequest();
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionMapper.updateQuestion(any(Question.class))).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void shouldPropagateDatabaseExceptionWithoutContinuing() {
        UpdateQuestionRequest request = validUpdateRequest();
        DataAccessResourceFailureException databaseException =
                new DataAccessResourceFailureException("database unavailable");
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        doThrow(databaseException)
                .when(questionMapper)
                .updateQuestion(any(Question.class));

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> questionAdminService.updateQuestion(101L, request)
        );

        assertSame(databaseException, thrown);
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void shouldReplaceScoringPointsInBusinessOrder() {
        Long questionId = 205L;
        UpdateQuestionRequest request = updateRequestWith(List.of(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "核心结论",
                        50
                ),
                new ScoringPointRequest(
                        QuestionPointType.INTERNAL,
                        "内部原理",
                        30
                ),
                new ScoringPointRequest(
                        QuestionPointType.ADVANCED,
                        "进阶分析",
                        20
                )
        ));
        when(questionMapper.getQuestionById(questionId))
                .thenReturn(existingQuestion());
        when(questionMapper.updateQuestion(any(Question.class))).thenReturn(1);
        when(questionScoringPointMapper.deleteByQuestionId(questionId))
                .thenReturn(2);
        when(questionScoringPointMapper.batchInsert(anyList()))
                .thenReturn(3);
        ArgumentCaptor<Question> questionCaptor =
                ArgumentCaptor.forClass(Question.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionScoringPoint>> pointsCaptor =
                ArgumentCaptor.forClass(List.class);

        questionAdminService.updateQuestion(questionId, request);

        InOrder mapperOrder = inOrder(
                questionMapper,
                questionScoringPointMapper
        );
        mapperOrder.verify(questionMapper).getQuestionById(questionId);
        mapperOrder.verify(questionMapper).updateQuestion(questionCaptor.capture());
        mapperOrder.verify(questionScoringPointMapper)
                .deleteByQuestionId(questionId);
        mapperOrder.verify(questionScoringPointMapper)
                .batchInsert(pointsCaptor.capture());

        Question update = questionCaptor.getValue();
        List<QuestionScoringPoint> points = pointsCaptor.getValue();
        assertAll(
                () -> assertEquals(questionId, update.getId()),
                () -> assertEquals(QuestionCategory.JVM, update.getCategory()),
                () -> assertEquals("类加载机制", update.getKnowledgePoint()),
                () -> assertEquals(QuestionDifficulty.HARD, update.getDifficulty()),
                () -> assertEquals("请说明类加载过程", update.getQuestionContent()),
                () -> assertEquals("类加载过程参考答案", update.getReferenceAnswer()),
                () -> assertNull(update.getStatus()),
                () -> assertEquals(3, points.size()),
                () -> assertScoringPoint(
                        points.get(0),
                        questionId,
                        QuestionPointType.CORE,
                        "核心结论",
                        50,
                        1
                ),
                () -> assertScoringPoint(
                        points.get(1),
                        questionId,
                        QuestionPointType.INTERNAL,
                        "内部原理",
                        30,
                        2
                ),
                () -> assertScoringPoint(
                        points.get(2),
                        questionId,
                        QuestionPointType.ADVANCED,
                        "进阶分析",
                        20,
                        3
                )
        );
    }

    @Test
    void givenExistingQuestionAndOrderedPoints_whenGettingDetail_thenAssemblesIndependentResponseVo() {
        Question question = existingQuestion();
        List<AdminScoringPointDetail> points = List.of(
                scoringPointDetail(QuestionPointType.CORE, "核心结论", 60),
                scoringPointDetail(QuestionPointType.INTERNAL, "内部原理", 40)
        );
        when(questionMapper.getQuestionById(101L)).thenReturn(question);
        when(questionScoringPointMapper.selectDetailByQuestionId(101L))
                .thenReturn(points);

        AdminQuestionDetailResponse response =
                questionAdminService.getQuestionScoringPointDetail(101L);

        assertAll(
                () -> assertEquals(101L, response.getId()),
                () -> assertEquals(question.getCategory(), response.getCategory()),
                () -> assertEquals(
                        question.getKnowledgePoint(),
                        response.getKnowledgePoint()
                ),
                () -> assertEquals(
                        question.getDifficulty(),
                        response.getDifficulty()
                ),
                () -> assertEquals(
                        question.getQuestionContent(),
                        response.getContent()
                ),
                () -> assertEquals(
                        question.getReferenceAnswer(),
                        response.getReferenceAnswer()
                ),
                () -> assertEquals(question.getStatus(), response.getStatus()),
                () -> assertSame(points, response.getScoringPoints()),
                () -> assertEquals(
                        QuestionPointType.CORE,
                        response.getScoringPoints().get(0).getPointType()
                ),
                () -> assertEquals(
                        QuestionPointType.INTERNAL,
                        response.getScoringPoints().get(1).getPointType()
                )
        );
        InOrder mapperOrder = inOrder(
                questionMapper,
                questionScoringPointMapper
        );
        mapperOrder.verify(questionMapper).getQuestionById(101L);
        mapperOrder.verify(questionScoringPointMapper)
                .selectDetailByQuestionId(101L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void givenInvalidQuestionId_whenGettingDetail_thenRejectsBeforeMapperAccess(
            Long questionId
    ) {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.getQuestionScoringPointDetail(
                        questionId
                )
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void givenMissingQuestion_whenGettingDetail_thenDoesNotQueryScoringPoints() {
        when(questionMapper.getQuestionById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.getQuestionScoringPointDetail(404L)
        );

        assertEquals("题目不存在", exception.getMessage());
        verify(questionMapper).getQuestionById(404L);
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void givenMapperEmptyScoringPointList_whenGettingDetail_thenReturnsEmptyList() {
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionScoringPointMapper.selectDetailByQuestionId(101L))
                .thenReturn(List.of());

        AdminQuestionDetailResponse response =
                questionAdminService.getQuestionScoringPointDetail(101L);

        assertEquals(List.of(), response.getScoringPoints());
    }

    @Test
    void givenMapperNullScoringPointList_whenGettingDetail_thenNormalizesToEmptyList() {
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionScoringPointMapper.selectDetailByQuestionId(101L))
                .thenReturn(null);

        AdminQuestionDetailResponse response =
                questionAdminService.getQuestionScoringPointDetail(101L);

        assertEquals(List.of(), response.getScoringPoints());
    }

    @Test
    void givenEnabledQuestion_whenDisabling_thenUpdatesOnlyQuestionMapper() {
        Question question = existingQuestion();
        question.setStatus(QuestionStatus.ENABLED);
        when(questionMapper.getQuestionById(101L)).thenReturn(question);
        when(questionMapper.updateQuestionStatus(
                101L,
                QuestionStatus.DISABLED
        )).thenReturn(1);

        questionAdminService.updateQuestionStatus(
                101L,
                statusRequest(QuestionStatus.DISABLED)
        );

        InOrder mapperOrder = inOrder(questionMapper);
        mapperOrder.verify(questionMapper).getQuestionById(101L);
        mapperOrder.verify(questionMapper).updateQuestionStatus(
                101L,
                QuestionStatus.DISABLED
        );
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void givenDisabledQuestion_whenEnabling_thenUpdatesOnlyQuestionMapper() {
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());
        when(questionMapper.updateQuestionStatus(
                101L,
                QuestionStatus.ENABLED
        )).thenReturn(1);

        questionAdminService.updateQuestionStatus(
                101L,
                statusRequest(QuestionStatus.ENABLED)
        );

        verify(questionMapper).updateQuestionStatus(
                101L,
                QuestionStatus.ENABLED
        );
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void givenQuestionAlreadyInTargetStatus_whenUpdatingStatus_thenReturnsWithoutUpdate() {
        when(questionMapper.getQuestionById(101L)).thenReturn(existingQuestion());

        questionAdminService.updateQuestionStatus(
                101L,
                statusRequest(QuestionStatus.DISABLED)
        );

        verify(questionMapper).getQuestionById(101L);
        verify(questionMapper, never()).updateQuestionStatus(
                any(),
                any()
        );
        verifyNoInteractions(questionScoringPointMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void givenInvalidQuestionId_whenUpdatingStatus_thenRejectsBeforeMapperAccess(
            Long questionId
    ) {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestionStatus(
                        questionId,
                        statusRequest(QuestionStatus.DISABLED)
                )
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void givenNullStatusRequest_whenUpdatingStatus_thenRejectsBeforeMapperAccess() {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestionStatus(101L, null)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void givenNullTargetStatus_whenUpdatingStatus_thenRejectsBeforeMapperAccess() {
        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestionStatus(
                        101L,
                        statusRequest(null)
                )
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void givenMissingQuestion_whenUpdatingStatus_thenDoesNotExecuteUpdate() {
        when(questionMapper.getQuestionById(404L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestionStatus(
                        404L,
                        statusRequest(QuestionStatus.DISABLED)
                )
        );

        assertEquals("题目不存在", exception.getMessage());
        verify(questionMapper).getQuestionById(404L);
        verify(questionMapper, never()).updateQuestionStatus(any(), any());
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void givenDatabaseFailure_whenUpdatingStatus_thenPropagatesOriginalException() {
        Question question = existingQuestion();
        question.setStatus(QuestionStatus.ENABLED);
        DataAccessResourceFailureException databaseException =
                new DataAccessResourceFailureException("database unavailable");
        when(questionMapper.getQuestionById(101L)).thenReturn(question);
        when(questionMapper.updateQuestionStatus(
                101L,
                QuestionStatus.DISABLED
        )).thenThrow(databaseException);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> questionAdminService.updateQuestionStatus(
                        101L,
                        statusRequest(QuestionStatus.DISABLED)
                )
        );

        assertSame(databaseException, thrown);
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void givenUnexpectedAffectedRows_whenUpdatingStatus_thenRejectsResult() {
        Question question = existingQuestion();
        question.setStatus(QuestionStatus.ENABLED);
        when(questionMapper.getQuestionById(101L)).thenReturn(question);
        when(questionMapper.updateQuestionStatus(
                101L,
                QuestionStatus.DISABLED
        )).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestionStatus(
                        101L,
                        statusRequest(QuestionStatus.DISABLED)
                )
        );

        assertEquals("状态更改失败", exception.getMessage());
        verifyNoInteractions(questionScoringPointMapper);
    }

    @Test
    void shouldDeclareCreateQuestionAsTransactional() throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "createQuestion",
                CreateQuestionRequest.class
        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    @Test
    void shouldDeclareUpdateQuestionAsTransactional() throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "updateQuestion",
                Long.class,
                UpdateQuestionRequest.class
        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    @Test
    void givenDetailMethod_whenInspectingTransaction_thenItIsReadOnly() throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "getQuestionScoringPointDetail",
                Long.class
        );

        Transactional transactional =
                method.getAnnotation(Transactional.class);
        assertTrue(transactional != null && transactional.readOnly());
    }

    @Test
    void givenStatusMethod_whenInspectingTransaction_thenItIsTransactional()
            throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "updateQuestionStatus",
                Long.class,
                UpdateQuestionStatusRequest.class
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

    private UpdateQuestionRequest validUpdateRequest() {
        return updateRequestWith(List.of(
                scoringPoint("评分点一", 60),
                scoringPoint("评分点二", 40)
        ));
    }

    private UpdateQuestionRequest updateRequestWith(
            List<ScoringPointRequest> scoringPoints
    ) {
        return UpdateQuestionRequest.builder()
                .category(QuestionCategory.JVM)
                .knowledgePoint("类加载机制")
                .difficulty(QuestionDifficulty.HARD)
                .questionContent("请说明类加载过程")
                .referenceAnswer("类加载过程参考答案")
                .scoringPoints(scoringPoints)
                .build();
    }

    private ScoringPointRequest scoringPoint(String content, Integer weight) {
        return new ScoringPointRequest(
                QuestionPointType.CORE,
                content,
                weight
        );
    }

    private Question existingQuestion() {
        return Question.builder()
                .id(101L)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("原题目")
                .referenceAnswer("原答案")
                .status(QuestionStatus.DISABLED)
                .build();
    }

    private AdminScoringPointDetail scoringPointDetail(
            QuestionPointType pointType,
            String content,
            int weight
    ) {
        return AdminScoringPointDetail.builder()
                .pointType(pointType)
                .content(content)
                .weight(weight)
                .build();
    }

    private UpdateQuestionStatusRequest statusRequest(QuestionStatus status) {
        return UpdateQuestionStatusRequest.builder()
                .status(status)
                .build();
    }

    private void assertScoringPoint(
            QuestionScoringPoint point,
            Long questionId,
            QuestionPointType pointType,
            String content,
            int weight,
            int sortOrder
    ) {
        assertAll(
                () -> assertEquals(questionId, point.getQuestionId()),
                () -> assertEquals(pointType, point.getPointType()),
                () -> assertEquals(content, point.getContent()),
                () -> assertEquals(weight, point.getWeight()),
                () -> assertEquals(sortOrder, point.getSortOrder()),
                () -> assertEquals(
                        QuestionPointStatus.ENABLED,
                        point.getStatus()
                )
        );
    }
}
