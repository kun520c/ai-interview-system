package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.QuestionPageQuery;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import com.kun.aiinterview.question.vo.AdminQuestionListItem;
import com.kun.aiinterview.question.vo.AdminQuestionPageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAdminPaginationServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionScoringPointMapper questionScoringPointMapper;

    @InjectMocks
    private QuestionAdminService questionAdminService;

    @Test
    void shouldRejectNullQueryBeforeMapperAccess() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> questionAdminService.getQuestionPage(null)
        );

        assertEquals("分页查询参数不能为空", exception.getMessage());
        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @ParameterizedTest(name = "pageNum={0}, pageSize={1}")
    @MethodSource("invalidPageArguments")
    void shouldRejectInvalidPageArgumentsBeforeMapperAccess(
            Integer pageNum,
            Integer pageSize
    ) {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.getQuestionPage(query)
        );

        verifyNoInteractions(questionMapper, questionScoringPointMapper);
    }

    @Test
    void shouldApplyDefaultPageValuesWithoutMutatingCallerQuery() {
        QuestionPageQuery callerQuery = QuestionPageQuery.builder()
                .pageNum(null)
                .pageSize(null)
                .category(QuestionCategory.JVM)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(0L);
        ArgumentCaptor<QuestionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        AdminQuestionPageResponse response =
                questionAdminService.getQuestionPage(callerQuery);

        verify(questionMapper).countQuestion(queryCaptor.capture());
        QuestionPageQuery mapperQuery = queryCaptor.getValue();
        assertAll(
                () -> assertNotSame(callerQuery, mapperQuery),
                () -> assertEquals(1, mapperQuery.getPageNum()),
                () -> assertEquals(10, mapperQuery.getPageSize()),
                () -> assertEquals(
                        QuestionCategory.JVM,
                        mapperQuery.getCategory()
                ),
                () -> assertEquals(1, response.getPageNum()),
                () -> assertEquals(10, response.getPageSize()),
                () -> assertEquals(null, callerQuery.getPageNum()),
                () -> assertEquals(null, callerQuery.getPageSize())
        );
    }

    @Test
    void shouldTrimKeywordForBothCountAndPageSelection() {
        QuestionPageQuery callerQuery = QuestionPageQuery.builder()
                .pageNum(2)
                .pageSize(10)
                .keyword("  HashMap  ")
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(1L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());
        ArgumentCaptor<QuestionPageQuery> countQueryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);
        ArgumentCaptor<QuestionPageQuery> selectQueryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        questionAdminService.getQuestionPage(callerQuery);

        verify(questionMapper).countQuestion(countQueryCaptor.capture());
        verify(questionMapper).selectQuestionPage(
                selectQueryCaptor.capture(),
                anyLong(),
                anyInt()
        );
        assertAll(
                () -> assertEquals(
                        "HashMap",
                        countQueryCaptor.getValue().getKeyword()
                ),
                () -> assertEquals(
                        "HashMap",
                        selectQueryCaptor.getValue().getKeyword()
                ),
                () -> assertEquals("  HashMap  ", callerQuery.getKeyword())
        );
    }

    @Test
    void shouldConvertBlankKeywordToNullForBothMapperCalls() {
        QuestionPageQuery callerQuery = QuestionPageQuery.builder()
                .pageNum(1)
                .pageSize(10)
                .keyword(" \t ")
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(1L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());
        ArgumentCaptor<QuestionPageQuery> countQueryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);
        ArgumentCaptor<QuestionPageQuery> selectQueryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        questionAdminService.getQuestionPage(callerQuery);

        verify(questionMapper).countQuestion(countQueryCaptor.capture());
        verify(questionMapper).selectQuestionPage(
                selectQueryCaptor.capture(),
                anyLong(),
                anyInt()
        );
        assertAll(
                () -> assertEquals(null, countQueryCaptor.getValue().getKeyword()),
                () -> assertEquals(null, selectQueryCaptor.getValue().getKeyword()),
                () -> assertEquals(" \t ", callerQuery.getKeyword())
        );
    }

    @Test
    void shouldPreserveAlreadyNormalizedKeyword() {
        QuestionPageQuery callerQuery = QuestionPageQuery.builder()
                .pageNum(1)
                .pageSize(10)
                .keyword("JVM")
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(0L);
        ArgumentCaptor<QuestionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        questionAdminService.getQuestionPage(callerQuery);

        verify(questionMapper).countQuestion(queryCaptor.capture());
        assertEquals("JVM", queryCaptor.getValue().getKeyword());
    }

    @ParameterizedTest(name = "page {0} size {1} -> offset {2}")
    @MethodSource("offsetArguments")
    void shouldCalculateExpectedOffsetWithLongSemantics(
            int pageNum,
            int pageSize,
            long expectedOffset
    ) {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(1L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());
        ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);

        questionAdminService.getQuestionPage(query);

        verify(questionMapper).selectQuestionPage(
                any(QuestionPageQuery.class),
                offsetCaptor.capture(),
                anyInt()
        );
        assertEquals(expectedOffset, offsetCaptor.getValue());
    }

    @Test
    void shouldReturnEmptyPageAndSkipSelectionWhenTotalIsZero() {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(3)
                .pageSize(20)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(0L);

        AdminQuestionPageResponse response =
                questionAdminService.getQuestionPage(query);

        assertAll(
                () -> assertNotNull(response.getRecords()),
                () -> assertTrue(response.getRecords().isEmpty()),
                () -> assertEquals(0L, response.getTotal()),
                () -> assertEquals(0L, response.getTotalPages()),
                () -> assertEquals(3, response.getPageNum()),
                () -> assertEquals(20, response.getPageSize())
        );
        verify(questionMapper, never()).selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        );
    }

    @Test
    void shouldReturnRecordsAndRoundTotalPagesUp() {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(2)
                .pageSize(10)
                .category(QuestionCategory.JAVA_COLLECTION)
                .difficulty(QuestionDifficulty.MEDIUM)
                .status(QuestionStatus.ENABLED)
                .build();
        List<AdminQuestionListItem> records = List.of(
                AdminQuestionListItem.builder().id(11L).build()
        );
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(21L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(records);

        AdminQuestionPageResponse response =
                questionAdminService.getQuestionPage(query);

        assertAll(
                () -> assertSame(records, response.getRecords()),
                () -> assertEquals(21L, response.getTotal()),
                () -> assertEquals(3L, response.getTotalPages()),
                () -> assertEquals(2, response.getPageNum()),
                () -> assertEquals(10, response.getPageSize())
        );
        verify(questionMapper).selectQuestionPage(
                any(QuestionPageQuery.class),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(10)
        );
    }

    @Test
    void shouldNotAddAnExtraPageWhenTotalIsExactlyDivisible() {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(1)
                .pageSize(10)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(20L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());

        AdminQuestionPageResponse response =
                questionAdminService.getQuestionPage(query);

        assertEquals(2L, response.getTotalPages());
    }

    @Test
    void shouldReturnEmptyRecordsAndRealTotalsWhenPageIsBeyondLastPage() {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .pageNum(100)
                .pageSize(10)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(15L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());

        AdminQuestionPageResponse response =
                questionAdminService.getQuestionPage(query);

        assertAll(
                () -> assertNotNull(response.getRecords()),
                () -> assertTrue(response.getRecords().isEmpty()),
                () -> assertEquals(15L, response.getTotal()),
                () -> assertEquals(2L, response.getTotalPages()),
                () -> assertEquals(100, response.getPageNum()),
                () -> assertEquals(10, response.getPageSize())
        );
    }

    @Test
    void shouldCallCountBeforeSelectionAndUseOnlyCountForEmptyResult() {
        QuestionPageQuery nonEmptyQuery = QuestionPageQuery.builder()
                .pageNum(1)
                .pageSize(10)
                .build();
        when(questionMapper.countQuestion(any(QuestionPageQuery.class)))
                .thenReturn(1L);
        when(questionMapper.selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        )).thenReturn(List.of());

        questionAdminService.getQuestionPage(nonEmptyQuery);

        InOrder inOrder = inOrder(questionMapper);
        inOrder.verify(questionMapper).countQuestion(any(QuestionPageQuery.class));
        inOrder.verify(questionMapper).selectQuestionPage(
                any(QuestionPageQuery.class),
                anyLong(),
                anyInt()
        );
    }

    @Test
    void shouldDeclarePaginationTransactionAsReadOnly() throws Exception {
        Method method = QuestionAdminService.class.getMethod(
                "getQuestionPage",
                QuestionPageQuery.class
        );

        Transactional transactional =
                method.getAnnotation(Transactional.class);

        assertAll(
                () -> assertNotNull(transactional),
                () -> assertTrue(transactional.readOnly())
        );
    }

    private static Stream<Arguments> invalidPageArguments() {
        return Stream.of(
                Arguments.of(0, 10),
                Arguments.of(-1, 10),
                Arguments.of(1, 0),
                Arguments.of(1, -1),
                Arguments.of(1, 51),
                Arguments.of(1, 100)
        );
    }

    private static Stream<Arguments> offsetArguments() {
        return Stream.of(
                Arguments.of(1, 10, 0L),
                Arguments.of(2, 10, 10L),
                Arguments.of(3, 20, 40L),
                Arguments.of(Integer.MAX_VALUE, 50, 107_374_182_300L)
        );
    }
}
