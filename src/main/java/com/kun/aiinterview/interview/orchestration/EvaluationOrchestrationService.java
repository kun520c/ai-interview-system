package com.kun.aiinterview.interview.orchestration;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalAdapter;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.decision.EvaluationDecision;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScore;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.service.EvaluationContextService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.kun.aiinterview.knowledge.service.RagTracePersistenceService;

import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = {
                "milvus.enabled",
                "deepseek.enabled"
        },
        havingValue = "true"
)
public class EvaluationOrchestrationService {

    private static final int RETRIEVAL_TOP_K = 5;

    private final EvaluationContextService contextService;

    private final EvaluationRetrievalAdapter retrievalAdapter;

    private final EvaluationPromptBuilder promptBuilder;

    private final DeepSeekEvaluationClient deepSeekEvaluationClient;

    private final LlmEvaluationValidator validator;

    private final EvaluationScoreCalculator scoreCalculator;

    private final EvaluationStandard evaluationStandard;

    private final FollowUpPolicy followUpPolicy;

    private final AnswerEvaluationMapper answerEvaluationMapper;

    private final ObjectMapper objectMapper;

    private final RagTracePersistenceService ragTracePersistenceService;

    public EvaluationOrchestrationService(
            EvaluationContextService contextService,
            EvaluationRetrievalAdapter retrievalAdapter,
            EvaluationPromptBuilder promptBuilder,
            DeepSeekEvaluationClient deepSeekEvaluationClient,
            LlmEvaluationValidator validator,
            EvaluationScoreCalculator scoreCalculator,
            EvaluationStandard evaluationStandard,
            FollowUpPolicy followUpPolicy,
            AnswerEvaluationMapper answerEvaluationMapper,
            ObjectMapper objectMapper,
            RagTracePersistenceService ragTracePersistenceService
    ) {
        this.contextService = contextService;
        this.retrievalAdapter = retrievalAdapter;
        this.promptBuilder = promptBuilder;
        this.deepSeekEvaluationClient =
                deepSeekEvaluationClient;
        this.validator = validator;
        this.scoreCalculator = scoreCalculator;
        this.evaluationStandard = evaluationStandard;
        this.followUpPolicy = followUpPolicy;
        this.answerEvaluationMapper =
                answerEvaluationMapper;
        this.objectMapper = objectMapper;
        this.ragTracePersistenceService = ragTracePersistenceService;
    }

    public EvaluationOrchestrationResult evaluate(
            Long answerId,
            boolean hasNextMainQuestion
    ){

        if(answerId == null){
            throw new IllegalArgumentException(
                    "answerId不能为空"
            );
        }

        AnswerEvaluation existingEvaluation =
                answerEvaluationMapper
                        .getByAnswerId(answerId);

        if(existingEvaluation != null){
            return toResult(existingEvaluation);
        }

        EvaluationContext context =
                contextService.buildContext(answerId);

        String retrievalBatchId =
                UUID.randomUUID().toString();

        EvaluationRetrievalResult retrievalResult =
                retrievalAdapter.retrieve(
                        context,
                        RETRIEVAL_TOP_K
                );

        ragTracePersistenceService.persist(
                retrievalBatchId,
                context.currentAnswerId(),
                retrievalResult
        );

        EvaluationPrompt prompt =
                promptBuilder.build(
                        context,
                        retrievalResult
                );

        DeepSeekEvaluationResult llmResult =
                deepSeekEvaluationClient.evaluate(
                        prompt
                );

        ValidatedEvaluationSuggestion validated =
                validator.validate(
                        context,
                        llmResult.suggestion()
                );

        EvaluationScore score =
                scoreCalculator.calculate(
                        validated
                );

        EvaluationLevel level =
                evaluationStandard.resolveLevel(
                        score.totalScore()
                );

        EvaluationDecision decision =
                followUpPolicy.decide(
                        context,
                        validated,
                        score,
                        hasNextMainQuestion
                );

        AnswerEvaluation evaluation =
                buildAnswerEvaluation(
                        context,
                        validated,
                        score,
                        level,
                        decision,
                        retrievalBatchId,
                        prompt,
                        llmResult
                );

        int affectedRows =
                answerEvaluationMapper
                        .insertEvaluation(evaluation);

        if(affectedRows != 1){
            throw new IllegalStateException(
                    "AnswerEvaluation写入失败"
            );
        }

        if(evaluation.getId() == null){
            throw new IllegalStateException(
                    "AnswerEvaluation主键未回填"
            );
        }

        return toResult(evaluation);
    }

    private AnswerEvaluation buildAnswerEvaluation(
            EvaluationContext context,
            ValidatedEvaluationSuggestion validated,
            EvaluationScore score,
            EvaluationLevel level,
            EvaluationDecision decision,
            String retrievalBatchId,
            EvaluationPrompt prompt,
            DeepSeekEvaluationResult llmResult
    ){
        return AnswerEvaluation.builder()

                .answerId(
                        context.currentAnswerId()
                )

                .mainInterviewQuestionId(
                        context.mainInterviewQuestionId()
                )

                .evaluationPhase(
                        decision.evaluationPhase()
                )

                .correctnessScore(
                        score.correctnessScore()
                )

                .completenessScore(
                        score.completenessScore()
                )

                .depthScore(
                        score.depthScore()
                )

                .clarityScore(
                        score.clarityScore()
                )

                .practiceScore(
                        score.practiceScore()
                )

                .totalScore(
                        score.totalScore()
                )

                .level(
                        level.name()
                )

                .strengths(
                        toJson(
                                validated.strengths()
                        )
                )

                .missingPoints(
                        toJson(
                                validated.missingPoints()
                        )
                )

                .correction(
                        validated.correction()
                )

                .scoringPointResults(
                        toJson(
                                validated
                                        .scoringPointResults()
                        )
                )

                .followUpRecommended(
                        validated.followUpRecommended()
                )

                .suggestedFollowUp(
                        validated.suggestedFollowUp()
                )

                .decisionAction(
                        decision.decisionAction()
                )

                .retrievalBatchId(
                        retrievalBatchId
                )

                .llmModel(
                        llmResult.model()
                )

                .promptVersion(
                        prompt.promptVersion()
                )

                .evaluationStandardVersion(
                        EvaluationStandard.VERSION
                )

                .rawResult(
                        llmResult.rawJson()
                )

                .build();
    }

    private String toJson(
            Object value
    ){

        try {

            return objectMapper.writeValueAsString(
                    value
            );
        }catch (JsonProcessingException exception){
            throw new IllegalStateException(
                    "评价结果JSON序列化失败",
                    exception
            );
        }
    }

    private EvaluationOrchestrationResult toResult(
            AnswerEvaluation evaluation
    ){

        EvaluationLevel level;

        try {

            level = EvaluationLevel.valueOf(
                    evaluation.getLevel()
            );

        }catch (IllegalArgumentException | NullPointerException exception){

            throw new IllegalStateException(
                    "AnswerEvaluation中的level非法",
                    exception
            );
        }

        return new EvaluationOrchestrationResult(

                evaluation.getId(),

                evaluation.getAnswerId(),

                evaluation
                        .getMainInterviewQuestionId(),

                evaluation.getEvaluationPhase(),

                evaluation.getDecisionAction(),

                evaluation.getTotalScore(),

                level,

                Boolean.TRUE.equals(
                        evaluation
                                .getFollowUpRecommended()
                ),

                evaluation.getSuggestedFollowUp(),

                evaluation.getRetrievalBatchId()
        );
    }
}
