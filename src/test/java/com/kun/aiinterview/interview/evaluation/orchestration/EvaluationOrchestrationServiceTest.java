package com.kun.aiinterview.interview.evaluation.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.decision.EvaluationDecision;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpTargetResolver;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalAdapter;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScore;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import com.kun.aiinterview.interview.service.EvaluationContextService;
import com.kun.aiinterview.knowledge.service.RagTracePersistenceService;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationOrchestrationServiceTest {

    private static final long ANSWER_ID = 1001L;
    private static final long FOLLOW_UP_ANSWER_ID = 2002L;
    private static final long MAIN_QUESTION_ID = 2001L;
    private static final long FOLLOW_UP_QUESTION_ID = 2002L;
    private static final String PROMPT_VERSION = "evaluation-prompt-v1";
    private static final String RAW_JSON = "{\"source\":\"deepseek\",\"answer\":true}";

    @Mock
    private EvaluationContextService contextService;

    @Mock
    private EvaluationRetrievalAdapter retrievalAdapter;

    @Mock
    private RagTracePersistenceService ragTracePersistenceService;

    @Mock
    private EvaluationPromptBuilder promptBuilder;

    @Mock
    private DeepSeekEvaluationClient deepSeekEvaluationClient;

    @Mock
    private LlmEvaluationValidator validator;

    @Mock
    private EvaluationScoreCalculator scoreCalculator;

    @Mock
    private EvaluationStandard evaluationStandard;

    @Mock
    private FollowUpPolicy followUpPolicy;

    @Spy
    private FollowUpTargetResolver followUpTargetResolver;

    @Mock
    private AnswerEvaluationMapper answerEvaluationMapper;

    private ObjectMapper objectMapper;
    private EvaluationOrchestrationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = serviceWith(objectMapper);
    }

    @Test
    void shouldOrchestrateMainInitialFollowUpAndMapEveryPersistenceField() throws Exception {
        EvaluationScore score = new EvaluationScore(12, 12, 12, 12, 11, 59);
        EvaluationDecision decision = new EvaluationDecision(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                List.of(101L)
        );
        PipelineFixture fixture = stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                score,
                EvaluationLevel.WEAK,
                decision,
                true
        );
        populateGeneratedKey(9001L);

        EvaluationOrchestrationResult result = service.evaluate(ANSWER_ID, true);

        assertThat(result.evaluationId()).isEqualTo(9001L);
        assertThat(result.answerId()).isEqualTo(ANSWER_ID);
        assertThat(result.mainInterviewQuestionId()).isEqualTo(MAIN_QUESTION_ID);
        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.INITIAL);
        assertThat(result.decisionAction()).isEqualTo(DecisionAction.FOLLOW_UP);
        assertThat(result.totalScore()).isEqualTo(59);
        assertThat(result.level()).isEqualTo(EvaluationLevel.WEAK);
        assertThat(result.followUpRecommended()).isTrue();
        assertThat(result.suggestedFollowUp()).isEqualTo("请进一步解释扩容机制");
        assertThat(result.followUpTargetPointIds())
                .containsExactly(101L);
        assertThat(result.retrievalBatchId()).isNotBlank();
        assertThat(UUID.fromString(result.retrievalBatchId()).toString())
                .isEqualTo(result.retrievalBatchId());
        assertThat(result.retrievalBatchId()).hasSizeLessThanOrEqualTo(64);

        ArgumentCaptor<String> traceBatchIdCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(ragTracePersistenceService).persist(
                traceBatchIdCaptor.capture(),
                eq(fixture.context().currentAnswerId()),
                same(fixture.retrievalResult())
        );

        ArgumentCaptor<AnswerEvaluation> captor =
                ArgumentCaptor.forClass(AnswerEvaluation.class);
        verify(answerEvaluationMapper).insertEvaluation(captor.capture());
        AnswerEvaluation evaluation = captor.getValue();

        assertThat(evaluation.getId()).isEqualTo(9001L);
        assertThat(evaluation.getAnswerId()).isEqualTo(fixture.context().currentAnswerId());
        assertThat(evaluation.getMainInterviewQuestionId())
                .isEqualTo(fixture.context().mainInterviewQuestionId());
        assertThat(evaluation.getEvaluationPhase()).isEqualTo(decision.evaluationPhase());
        assertThat(evaluation.getCorrectnessScore()).isEqualTo(score.correctnessScore());
        assertThat(evaluation.getCompletenessScore()).isEqualTo(score.completenessScore());
        assertThat(evaluation.getDepthScore()).isEqualTo(score.depthScore());
        assertThat(evaluation.getClarityScore()).isEqualTo(score.clarityScore());
        assertThat(evaluation.getPracticeScore()).isEqualTo(score.practiceScore());
        assertThat(evaluation.getTotalScore()).isEqualTo(score.totalScore());
        assertThat(evaluation.getLevel()).isEqualTo(EvaluationLevel.WEAK.name());
        assertThat(objectMapper.readTree(evaluation.getStrengths()).equals(
                objectMapper.readTree(objectMapper.writeValueAsString(
                        fixture.validated().strengths()
                ))
        )).isTrue();
        assertThat(objectMapper.readTree(evaluation.getMissingPoints()).equals(
                objectMapper.readTree(objectMapper.writeValueAsString(
                        fixture.validated().missingPoints()
                ))
        )).isTrue();
        assertThat(evaluation.getCorrection()).isEqualTo(fixture.validated().correction());
        assertThat(objectMapper.readTree(evaluation.getScoringPointResults()).equals(
                objectMapper.readTree(objectMapper.writeValueAsString(
                        fixture.validated().scoringPointResults()
                ))
        )).isTrue();
        assertThat(evaluation.getFollowUpRecommended()).isTrue();
        assertThat(evaluation.getSuggestedFollowUp())
                .isEqualTo(fixture.validated().suggestedFollowUp());
        assertThat(evaluation.getDecisionAction()).isEqualTo(decision.decisionAction());
        assertThat(traceBatchIdCaptor.getValue())
                .isEqualTo(evaluation.getRetrievalBatchId())
                .isEqualTo(result.retrievalBatchId());
        assertThat(evaluation.getLlmModel()).isEqualTo(fixture.llmResult().model());
        assertThat(evaluation.getPromptVersion()).isEqualTo(fixture.prompt().promptVersion());
        assertThat(evaluation.getEvaluationStandardVersion())
                .isEqualTo(EvaluationStandard.VERSION);
        assertThat(evaluation.getRawResult()).isEqualTo(RAW_JSON);
        assertThat(evaluation.getCreatedAt()).isNull();

        InOrder order = inOrder(
                answerEvaluationMapper,
                contextService,
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy
        );
        order.verify(answerEvaluationMapper).getByAnswerId(ANSWER_ID);
        order.verify(contextService).buildContext(ANSWER_ID);
        order.verify(retrievalAdapter).retrieve(fixture.context(), 5);
        order.verify(ragTracePersistenceService).persist(
                traceBatchIdCaptor.getValue(),
                fixture.context().currentAnswerId(),
                fixture.retrievalResult()
        );
        order.verify(promptBuilder).build(
                eq(fixture.context()),
                same(fixture.retrievalResult())
        );
        order.verify(deepSeekEvaluationClient).evaluate(fixture.prompt());
        order.verify(validator).validate(fixture.context(), fixture.llmSuggestion());
        order.verify(scoreCalculator).calculate(fixture.validated());
        order.verify(evaluationStandard).resolveLevel(59);
        order.verify(followUpPolicy).decide(
                fixture.context(),
                fixture.validated(),
                score,
                true
        );
        order.verify(answerEvaluationMapper).insertEvaluation(any(AnswerEvaluation.class));
    }

    @Test
    void shouldPersistLlmRecommendationEvenWhenJavaDecisionIsFinalNextMain() {
        EvaluationScore score = new EvaluationScore(18, 17, 17, 17, 16, 85);
        PipelineFixture fixture = stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                score,
                EvaluationLevel.GOOD,
                new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                true
        );
        populateGeneratedKey(9002L);

        EvaluationOrchestrationResult result = service.evaluate(ANSWER_ID, true);

        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.FINAL);
        assertThat(result.decisionAction()).isEqualTo(DecisionAction.NEXT_MAIN);
        assertThat(result.followUpRecommended()).isTrue();
        assertThat(result.level()).isEqualTo(EvaluationLevel.GOOD);
        verify(followUpPolicy).decide(
                fixture.context(), fixture.validated(), score, true
        );
    }

    @Test
    void shouldPersistMainFinalFinish() {
        PipelineFixture fixture = stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(18, 18, 18, 18, 18, 90),
                EvaluationLevel.EXCELLENT,
                new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.FINISH,
                        List.of()
                ),
                false
        );
        populateGeneratedKey(9003L);

        EvaluationOrchestrationResult result = service.evaluate(ANSWER_ID, false);

        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.FINAL);
        assertThat(result.decisionAction()).isEqualTo(DecisionAction.FINISH);
        assertThat(result.level()).isEqualTo(EvaluationLevel.EXCELLENT);
        verify(followUpPolicy).decide(
                fixture.context(), fixture.validated(), fixture.score(), false
        );
    }

    @Test
    void shouldPersistFollowUpFinalAgainstOriginalMainQuestion() {
        PipelineFixture fixture = stubHappyPipeline(
                FOLLOW_UP_ANSWER_ID,
                EvaluationMode.FOLLOW_UP_ANSWER,
                new EvaluationScore(16, 16, 16, 16, 16, 80),
                EvaluationLevel.GOOD,
                new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                false
        );
        populateGeneratedKey(9004L);

        EvaluationOrchestrationResult result = service.evaluate(FOLLOW_UP_ANSWER_ID, true);

        ArgumentCaptor<String> traceBatchIdCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(ragTracePersistenceService).persist(
                traceBatchIdCaptor.capture(),
                eq(FOLLOW_UP_ANSWER_ID),
                same(fixture.retrievalResult())
        );
        ArgumentCaptor<AnswerEvaluation> captor =
                ArgumentCaptor.forClass(AnswerEvaluation.class);
        verify(answerEvaluationMapper).insertEvaluation(captor.capture());
        assertThat(fixture.context().currentAnswerId()).isEqualTo(FOLLOW_UP_ANSWER_ID);
        assertThat(fixture.context().mainAnswerId()).isEqualTo(ANSWER_ID);
        assertThat(fixture.context().currentInterviewQuestionId())
                .isEqualTo(FOLLOW_UP_QUESTION_ID);
        assertThat(captor.getValue().getAnswerId()).isEqualTo(FOLLOW_UP_ANSWER_ID);
        assertThat(captor.getValue().getMainInterviewQuestionId())
                .isEqualTo(MAIN_QUESTION_ID)
                .isNotEqualTo(FOLLOW_UP_QUESTION_ID);
        assertThat(result.mainInterviewQuestionId()).isEqualTo(MAIN_QUESTION_ID);
        assertThat(result.answerId()).isEqualTo(FOLLOW_UP_ANSWER_ID);
        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.FINAL);
        assertThat(result.decisionAction()).isEqualTo(DecisionAction.NEXT_MAIN);
        assertThat(traceBatchIdCaptor.getValue())
                .isEqualTo(captor.getValue().getRetrievalBatchId())
                .isEqualTo(result.retrievalBatchId());
    }

    @Test
    void shouldReturnExistingEvaluationWithoutInvokingPipelineOrInsert() {
        AnswerEvaluation existing = existingEvaluation("GOOD");
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID)).thenReturn(existing);

        EvaluationOrchestrationResult result = service.evaluate(ANSWER_ID, true);

        assertThat(result.evaluationId()).isEqualTo(7001L);
        assertThat(result.answerId()).isEqualTo(ANSWER_ID);
        assertThat(result.mainInterviewQuestionId()).isEqualTo(MAIN_QUESTION_ID);
        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.FINAL);
        assertThat(result.decisionAction()).isEqualTo(DecisionAction.NEXT_MAIN);
        assertThat(result.totalScore()).isEqualTo(85);
        assertThat(result.level()).isEqualTo(EvaluationLevel.GOOD);
        assertThat(result.followUpRecommended()).isTrue();
        assertThat(result.suggestedFollowUp()).isEqualTo("历史候选追问");
        assertThat(result.followUpTargetPointIds()).isEmpty();
        assertThat(result.retrievalBatchId()).isEqualTo("existing-batch-id");
        verifyNoInteractions(
                contextService,
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy,
                followUpTargetResolver
        );
        verify(answerEvaluationMapper, never()).insertEvaluation(any());
    }

    @Test
    void shouldRecoverFollowUpTargetsFromExistingEvaluationWithoutDeepSeek()
            throws Exception {
        List<LlmEvaluationSuggestion.ScoringPointResult>
                persistedResults = List.of(
                new LlmEvaluationSuggestion.ScoringPointResult(
                        101L,
                        false,
                        null
                )
        );
        AnswerEvaluation existing =
                existingFollowUpEvaluation(
                        objectMapper.writeValueAsString(
                                persistedResults
                        )
                );
        EvaluationContext context = fixture(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(12, 12, 12, 12, 11, 59),
                true
        ).context();

        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(existing);
        when(contextService.buildContext(ANSWER_ID))
                .thenReturn(context);
        EvaluationOrchestrationResult result =
                service.evaluate(ANSWER_ID, true);

        assertThat(result.evaluationPhase())
                .isEqualTo(EvaluationPhase.INITIAL);
        assertThat(result.decisionAction())
                .isEqualTo(DecisionAction.FOLLOW_UP);
        assertThat(result.suggestedFollowUp())
                .isEqualTo("历史候选追问");
        assertThat(result.followUpTargetPointIds())
                .containsExactly(101L);
        verify(contextService).buildContext(ANSWER_ID);
        verify(followUpTargetResolver).resolve(
                same(context),
                eq(persistedResults)
        );
        verifyNoInteractions(
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy
        );
        verify(answerEvaluationMapper, never())
                .insertEvaluation(any());
    }

    @Test
    void shouldRejectInvalidPersistedScoringPointResultsWithoutDeepSeek() {
        AnswerEvaluation existing =
                existingFollowUpEvaluation("{");
        EvaluationContext context = fixture(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(12, 12, 12, 12, 11, 59),
                true
        ).context();

        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(existing);
        when(contextService.buildContext(ANSWER_ID))
                .thenReturn(context);

        assertThatThrownBy(
                () -> service.evaluate(ANSWER_ID, true)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "AnswerEvaluation中的scoringPointResults JSON非法"
                )
                .hasCauseInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(
                followUpTargetResolver,
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy
        );
        verify(answerEvaluationMapper, never())
                .insertEvaluation(any());
    }

    @Test
    void shouldRejectNullAnswerIdBeforeMapperOrPipelineInteraction() {
        assertThatThrownBy(() -> service.evaluate(null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("answerId不能为空");

        verifyNoInteractions(
                answerEvaluationMapper,
                contextService,
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy,
                followUpTargetResolver
        );
    }

    @ParameterizedTest
    @EnumSource(FailureStage.class)
    void shouldPropagateEveryUpstreamFailureWithoutInserting(FailureStage stage) {
        RuntimeException expected = stubFailure(stage);

        Throwable actual = catchThrowable(() -> service.evaluate(ANSWER_ID, true));

        assertThat(actual).isSameAs(expected);
        verify(answerEvaluationMapper, never()).insertEvaluation(any());

        if (stage == FailureStage.CONTEXT || stage == FailureStage.RETRIEVAL) {
            verifyNoInteractions(ragTracePersistenceService);
        } else {
            verify(ragTracePersistenceService).persist(
                    anyString(),
                    eq(ANSWER_ID),
                    any(EvaluationRetrievalResult.class)
            );
        }

        switch (stage) {
            case CONTEXT -> verifyNoInteractions(
                    retrievalAdapter,
                    ragTracePersistenceService,
                    promptBuilder,
                    deepSeekEvaluationClient,
                    validator,
                    scoreCalculator,
                    evaluationStandard,
                    followUpPolicy
            );
            case RETRIEVAL -> verifyNoInteractions(
                    ragTracePersistenceService,
                    promptBuilder,
                    deepSeekEvaluationClient,
                    validator,
                    scoreCalculator,
                    evaluationStandard,
                    followUpPolicy
            );
            case TRACE -> verifyNoInteractions(
                    promptBuilder,
                    deepSeekEvaluationClient,
                    validator,
                    scoreCalculator,
                    evaluationStandard,
                    followUpPolicy
            );
            case PROMPT -> verifyNoInteractions(
                    deepSeekEvaluationClient,
                    validator,
                    scoreCalculator,
                    evaluationStandard,
                    followUpPolicy
            );
            case DEEPSEEK -> verifyNoInteractions(
                    validator,
                    scoreCalculator,
                    evaluationStandard,
                    followUpPolicy
            );
            default -> {
                // Later-stage tests only need to prove trace ran and evaluation was not inserted.
            }
        }
    }

    @Test
    void shouldStopBeforeInsertWhenJsonSerializationFails() throws Exception {
        PipelineFixture fixture = stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(12, 12, 12, 12, 11, 59),
                EvaluationLevel.WEAK,
                new EvaluationDecision(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FOLLOW_UP,
                        List.of(101L)
                ),
                true
        );
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("test serialization failure") { });
        EvaluationOrchestrationService failingService = serviceWith(failingObjectMapper);

        assertThatThrownBy(() -> failingService.evaluate(ANSWER_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("评价结果JSON序列化失败")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(followUpPolicy).decide(
                fixture.context(), fixture.validated(), fixture.score(), true
        );
        verify(answerEvaluationMapper, never()).insertEvaluation(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldRejectUnexpectedAffectedRowCount(int affectedRows) {
        stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(16, 16, 16, 16, 16, 80),
                EvaluationLevel.GOOD,
                new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                false
        );
        when(answerEvaluationMapper.insertEvaluation(any())).thenReturn(affectedRows);

        assertThatThrownBy(() -> service.evaluate(ANSWER_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation写入失败");
    }

    @Test
    void shouldRejectSuccessfulInsertWithoutGeneratedKey() {
        stubHappyPipeline(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(16, 16, 16, 16, 16, 80),
                EvaluationLevel.GOOD,
                new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                false
        );
        when(answerEvaluationMapper.insertEvaluation(any())).thenReturn(1);

        assertThatThrownBy(() -> service.evaluate(ANSWER_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation主键未回填");
    }

    @Test
    void shouldRejectExistingEvaluationWithUnknownLevel() {
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(existingEvaluation("UNKNOWN"));

        assertThatThrownBy(() -> service.evaluate(ANSWER_ID, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation中的level非法")
                .hasCauseInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                contextService,
                retrievalAdapter,
                ragTracePersistenceService,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy,
                followUpTargetResolver
        );
        verify(answerEvaluationMapper, never()).insertEvaluation(any());
    }

    private EvaluationOrchestrationService serviceWith(ObjectMapper mapper) {
        return new EvaluationOrchestrationService(
                contextService,
                retrievalAdapter,
                promptBuilder,
                deepSeekEvaluationClient,
                validator,
                scoreCalculator,
                evaluationStandard,
                followUpPolicy,
                followUpTargetResolver,
                answerEvaluationMapper,
                mapper,
                ragTracePersistenceService
        );
    }

    private PipelineFixture stubHappyPipeline(
            long answerId,
            EvaluationMode mode,
            EvaluationScore score,
            EvaluationLevel level,
            EvaluationDecision decision,
            boolean followUpRecommended
    ) {
        PipelineFixture fixture = fixture(answerId, mode, score, followUpRecommended);
        when(answerEvaluationMapper.getByAnswerId(answerId)).thenReturn(null);
        when(contextService.buildContext(answerId)).thenReturn(fixture.context());
        when(retrievalAdapter.retrieve(fixture.context(), 5))
                .thenReturn(fixture.retrievalResult());
        when(promptBuilder.build(fixture.context(), fixture.retrievalResult()))
                .thenReturn(fixture.prompt());
        when(deepSeekEvaluationClient.evaluate(fixture.prompt()))
                .thenReturn(fixture.llmResult());
        when(validator.validate(fixture.context(), fixture.llmSuggestion()))
                .thenReturn(fixture.validated());
        when(scoreCalculator.calculate(fixture.validated())).thenReturn(score);
        when(evaluationStandard.resolveLevel(score.totalScore())).thenReturn(level);
        when(followUpPolicy.decide(
                eq(fixture.context()),
                eq(fixture.validated()),
                eq(score),
                anyBoolean()
        )).thenReturn(decision);
        return fixture;
    }

    private void populateGeneratedKey(long generatedId) {
        doAnswer(invocation -> {
            AnswerEvaluation evaluation = invocation.getArgument(0);
            evaluation.setId(generatedId);
            return 1;
        }).when(answerEvaluationMapper).insertEvaluation(any(AnswerEvaluation.class));
    }

    private RuntimeException stubFailure(FailureStage stage) {
        PipelineFixture fixture = fixture(
                ANSWER_ID,
                EvaluationMode.MAIN_ANSWER,
                new EvaluationScore(16, 16, 16, 16, 16, 80),
                false
        );
        RuntimeException failure = new IllegalStateException("failure at " + stage);
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID)).thenReturn(null);

        if (stage == FailureStage.CONTEXT) {
            when(contextService.buildContext(ANSWER_ID)).thenThrow(failure);
            return failure;
        }
        when(contextService.buildContext(ANSWER_ID)).thenReturn(fixture.context());

        if (stage == FailureStage.RETRIEVAL) {
            when(retrievalAdapter.retrieve(fixture.context(), 5)).thenThrow(failure);
            return failure;
        }
        when(retrievalAdapter.retrieve(fixture.context(), 5))
                .thenReturn(fixture.retrievalResult());

        if (stage == FailureStage.TRACE) {
            doThrow(failure).when(ragTracePersistenceService).persist(
                    anyString(),
                    eq(ANSWER_ID),
                    same(fixture.retrievalResult())
            );
            return failure;
        }

        if (stage == FailureStage.PROMPT) {
            when(promptBuilder.build(fixture.context(), fixture.retrievalResult()))
                    .thenThrow(failure);
            return failure;
        }
        when(promptBuilder.build(fixture.context(), fixture.retrievalResult()))
                .thenReturn(fixture.prompt());

        if (stage == FailureStage.DEEPSEEK) {
            when(deepSeekEvaluationClient.evaluate(fixture.prompt())).thenThrow(failure);
            return failure;
        }
        when(deepSeekEvaluationClient.evaluate(fixture.prompt()))
                .thenReturn(fixture.llmResult());

        if (stage == FailureStage.VALIDATOR) {
            when(validator.validate(fixture.context(), fixture.llmSuggestion()))
                    .thenThrow(failure);
            return failure;
        }
        when(validator.validate(fixture.context(), fixture.llmSuggestion()))
                .thenReturn(fixture.validated());

        if (stage == FailureStage.SCORE) {
            when(scoreCalculator.calculate(fixture.validated())).thenThrow(failure);
            return failure;
        }
        when(scoreCalculator.calculate(fixture.validated())).thenReturn(fixture.score());

        if (stage == FailureStage.STANDARD) {
            when(evaluationStandard.resolveLevel(fixture.score().totalScore()))
                    .thenThrow(failure);
            return failure;
        }
        when(evaluationStandard.resolveLevel(fixture.score().totalScore()))
                .thenReturn(EvaluationLevel.GOOD);

        when(followUpPolicy.decide(
                fixture.context(), fixture.validated(), fixture.score(), true
        )).thenThrow(failure);
        return failure;
    }

    private PipelineFixture fixture(
            long answerId,
            EvaluationMode mode,
            EvaluationScore score,
            boolean followUpRecommended
    ) {
        boolean followUp = mode == EvaluationMode.FOLLOW_UP_ANSWER;
        EvaluationContext context = new EvaluationContext(
                answerId,
                followUp ? FOLLOW_UP_QUESTION_ID : MAIN_QUESTION_ID,
                followUp ? ANSWER_ID : answerId,
                MAIN_QUESTION_ID,
                mode,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                List.of(new ScoringPointSnapshot(
                        101L,
                        QuestionPointType.CORE,
                        "说明扩容机制",
                        100
                )),
                "MAIN 回答",
                followUp ? "请进一步解释扩容机制" : null,
                followUp ? "FOLLOW_UP 回答" : null,
                followUp ? List.of(101L) : List.of()
        );
        EvaluationRetrievalResult retrievalResult = new EvaluationRetrievalResult(
                "HashMap 扩容机制",
                5,
                "test-embedding-model",
                "test-embedding-v1",
                List.of(),
                List.of()
        );
        EvaluationPrompt prompt = new EvaluationPrompt(
                PROMPT_VERSION,
                "test system prompt",
                "test user prompt"
        );
        String suggestedFollowUp = followUpRecommended ? "请进一步解释扩容机制" : null;
        LlmEvaluationSuggestion.ScoringPointResult pointResult =
                new LlmEvaluationSuggestion.ScoringPointResult(
                        101L,
                        true,
                        "用户提到了扩容"
                );
        LlmEvaluationSuggestion llmSuggestion = new LlmEvaluationSuggestion(
                score.correctnessScore(),
                score.completenessScore(),
                score.depthScore(),
                score.clarityScore(),
                score.practiceScore(),
                List.of("核心概念准确"),
                List.of("可补充实现细节"),
                "补充完整扩容过程",
                List.of(pointResult),
                followUpRecommended,
                suggestedFollowUp
        );
        DeepSeekEvaluationResult llmResult = new DeepSeekEvaluationResult(
                "test-model",
                "stop",
                RAW_JSON,
                llmSuggestion
        );
        ValidatedEvaluationSuggestion validated = new ValidatedEvaluationSuggestion(
                score.correctnessScore(),
                score.completenessScore(),
                score.depthScore(),
                score.clarityScore(),
                score.practiceScore(),
                List.of("核心概念准确"),
                List.of("可补充实现细节"),
                "补充完整扩容过程",
                List.of(pointResult),
                followUpRecommended,
                suggestedFollowUp
        );
        return new PipelineFixture(
                context,
                retrievalResult,
                prompt,
                llmSuggestion,
                llmResult,
                validated,
                score
        );
    }

    private AnswerEvaluation existingEvaluation(String level) {
        return AnswerEvaluation.builder()
                .id(7001L)
                .answerId(ANSWER_ID)
                .mainInterviewQuestionId(MAIN_QUESTION_ID)
                .evaluationPhase(EvaluationPhase.FINAL)
                .totalScore(85)
                .level(level)
                .followUpRecommended(true)
                .suggestedFollowUp("历史候选追问")
                .scoringPointResults("""
                        [
                          {
                            "scoringPointId": 101,
                            "covered": true,
                            "evidence": "历史覆盖证据"
                          }
                        ]
                        """)
                .decisionAction(DecisionAction.NEXT_MAIN)
                .retrievalBatchId("existing-batch-id")
                .build();
    }

    private AnswerEvaluation existingFollowUpEvaluation(
            String scoringPointResults
    ) {
        return AnswerEvaluation.builder()
                .id(7002L)
                .answerId(ANSWER_ID)
                .mainInterviewQuestionId(MAIN_QUESTION_ID)
                .evaluationPhase(EvaluationPhase.INITIAL)
                .totalScore(59)
                .level(EvaluationLevel.WEAK.name())
                .followUpRecommended(true)
                .suggestedFollowUp("历史候选追问")
                .scoringPointResults(scoringPointResults)
                .decisionAction(DecisionAction.FOLLOW_UP)
                .retrievalBatchId("existing-follow-up-batch-id")
                .build();
    }

    private enum FailureStage {
        CONTEXT,
        RETRIEVAL,
        TRACE,
        PROMPT,
        DEEPSEEK,
        VALIDATOR,
        SCORE,
        STANDARD,
        POLICY
    }

    private record PipelineFixture(
            EvaluationContext context,
            EvaluationRetrievalResult retrievalResult,
            EvaluationPrompt prompt,
            LlmEvaluationSuggestion llmSuggestion,
            DeepSeekEvaluationResult llmResult,
            ValidatedEvaluationSuggestion validated,
            EvaluationScore score
    ) {
    }
}
