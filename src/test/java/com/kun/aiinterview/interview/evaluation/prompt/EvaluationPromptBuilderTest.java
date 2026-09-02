package com.kun.aiinterview.interview.evaluation.prompt;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationPromptBuilderTest {

    private static final String RETRIEVAL_QUERY =
            "THIS_QUERY_MUST_NOT_ENTER_PROMPT";

    private final EvaluationPromptBuilder promptBuilder =
            new EvaluationPromptBuilder();

    @Test
    void givenMainContextAndEligibleEvidence_whenBuild_thenIncludesOnlyMainEvaluationData() {
        RetrievedChunk evidence = evidence(
                1,
                0.91D,
                "RAG-TITLE-MAIN",
                "RAG-SOURCE-MAIN",
                "RAG-CONTENT-MAIN"
        );

        EvaluationPrompt prompt = promptBuilder.build(
                mainContext(),
                retrievalResult(List.of(), List.of(evidence))
        );

        assertThat(prompt.promptVersion())
                .isEqualTo("evaluation-prompt-v1");
        assertThat(prompt.userPrompt())
                .contains(
                        "MAIN_ANSWER",
                        "JAVA_COLLECTION",
                        "KNOWLEDGE-POINT-HASHMAP",
                        "MAIN-QUESTION-CONTENT",
                        "REFERENCE-ANSWER-SNAPSHOT",
                        "scoringPointId=101",
                        "type=CORE",
                        "weight=60",
                        "SCORING-POINT-ONE",
                        "scoringPointId=102",
                        "type=KEY",
                        "weight=40",
                        "SCORING-POINT-TWO",
                        "MAIN-ANSWER-CONTENT",
                        "rank=1",
                        "similarity=0.910000",
                        "RAG-TITLE-MAIN",
                        "category=SPRING",
                        "RAG-SOURCE-MAIN",
                        "RAG-CONTENT-MAIN"
                )
                .doesNotContain(
                        "===== FOLLOW UP QUESTION =====",
                        "===== FOLLOW UP ANSWER =====",
                        "===== FOLLOW UP TARGET POINT IDS ====="
                );
    }

    @Test
    void givenFollowUpContextAndEligibleEvidence_whenBuild_thenIncludesCompleteMainAndFollowUpData() {
        EvaluationPrompt prompt = promptBuilder.build(
                followUpContext(),
                retrievalResult(
                        List.of(),
                        List.of(evidence(
                                2,
                                0.82D,
                                "RAG-TITLE-FOLLOW-UP",
                                "RAG-SOURCE-FOLLOW-UP",
                                "RAG-CONTENT-FOLLOW-UP"
                        ))
                )
        );

        assertThat(prompt.userPrompt()).contains(
                "FOLLOW_UP_ANSWER",
                "MAIN-QUESTION-CONTENT",
                "MAIN-ANSWER-CONTENT",
                "scoringPointId=101",
                "SCORING-POINT-ONE",
                "scoringPointId=102",
                "SCORING-POINT-TWO",
                "===== FOLLOW UP QUESTION =====",
                "FOLLOW-UP-QUESTION-CONTENT",
                "===== FOLLOW UP ANSWER =====",
                "FOLLOW-UP-ANSWER-CONTENT",
                "===== FOLLOW UP TARGET POINT IDS =====",
                "[102]",
                "RAG-TITLE-FOLLOW-UP",
                "RAG-SOURCE-FOLLOW-UP",
                "RAG-CONTENT-FOLLOW-UP"
        );
    }

    @Test
    void givenNoEligibleEvidence_whenBuild_thenStatesThatNoRagEvidenceIsAvailable() {
        EvaluationPrompt prompt = promptBuilder.build(
                mainContext(),
                retrievalResult(List.of(), List.of())
        );

        assertThat(prompt.userPrompt())
                .contains("无可用 RAG Evidence")
                .doesNotContain("RAG-CONTENT-MAIN");
    }

    @Test
    void givenMultipleEvidenceItems_whenBuild_thenPreservesInputOrderAndEvidenceDetails() {
        RetrievedChunk first = evidence(
                7,
                0.712345D,
                "FIRST-EVIDENCE-TITLE",
                "FIRST-EVIDENCE-SOURCE",
                "FIRST-EVIDENCE-CONTENT"
        );
        RetrievedChunk second = evidence(
                2,
                0.934567D,
                "SECOND-EVIDENCE-TITLE",
                "SECOND-EVIDENCE-SOURCE",
                "SECOND-EVIDENCE-CONTENT"
        );

        String userPrompt = promptBuilder.build(
                mainContext(),
                retrievalResult(List.of(), List.of(first, second))
        ).userPrompt();

        assertThat(userPrompt).contains(
                "rank=7",
                "similarity=0.712345",
                "FIRST-EVIDENCE-CONTENT",
                "rank=2",
                "similarity=0.934567",
                "SECOND-EVIDENCE-CONTENT"
        );
        assertThat(userPrompt.indexOf("FIRST-EVIDENCE-CONTENT"))
                .isLessThan(userPrompt.indexOf("SECOND-EVIDENCE-CONTENT"));
    }

    @Test
    void givenRawHitDiffersFromEligibleEvidence_whenBuild_thenDoesNotExposeRawHit() {
        VectorSearchHit rawHit = new VectorSearchHit(
                "RAW_ONLY_VECTOR_ID",
                999L,
                9,
                "RAW_ONLY_EMBEDDING_VERSION",
                0.99D
        );
        RetrievedChunk eligibleEvidence = evidence(
                1,
                0.75D,
                "ELIGIBLE-TITLE",
                "ELIGIBLE-SOURCE",
                "ELIGIBLE-CONTENT"
        );

        String userPrompt = promptBuilder.build(
                mainContext(),
                retrievalResult(List.of(rawHit), List.of(eligibleEvidence))
        ).userPrompt();

        assertThat(userPrompt)
                .contains("ELIGIBLE-CONTENT")
                .doesNotContain(
                        "RAW_ONLY_VECTOR_ID",
                        "RAW_ONLY_EMBEDDING_VERSION"
                );
    }

    @Test
    void givenRetrievalQuery_whenBuild_thenDoesNotUseQueryAsEvaluationEvidence() {
        EvaluationPrompt prompt = promptBuilder.build(
                mainContext(),
                retrievalResult(
                        List.of(),
                        List.of(evidence(
                                1,
                                0.80D,
                                "ELIGIBLE-TITLE",
                                "ELIGIBLE-SOURCE",
                                "ELIGIBLE-CONTENT"
                        ))
                )
        );

        assertThat(prompt.userPrompt())
                .doesNotContain(RETRIEVAL_QUERY);
    }

    @Test
    void givenBuiltPrompt_whenInspectingSystemPrompt_thenDefinesEvaluationJsonContractOnly() {
        String systemPrompt = promptBuilder.build(
                mainContext(),
                retrievalResult(List.of(), List.of())
        ).systemPrompt();

        assertThat(systemPrompt).contains(
                "只提供评价建议",
                "correctnessScore",
                "completenessScore",
                "depthScore",
                "clarityScore",
                "practiceScore",
                "0 到 20",
                "scoringPointResults",
                "evidence",
                "用户回答",
                "不是 RAG Evidence",
                "followUpRecommended",
                "suggestedFollowUp",
                "MAIN_ANSWER",
                "FOLLOW_UP_ANSWER",
                "合法 JSON",
                "不要输出 Markdown",
                "不要输出代码围栏"
        );

        String jsonContract = jsonContract(systemPrompt);
        assertThat(jsonContract)
                .contains(
                        "\"correctnessScore\"",
                        "\"completenessScore\"",
                        "\"depthScore\"",
                        "\"clarityScore\"",
                        "\"practiceScore\"",
                        "\"scoringPointResults\"",
                        "\"followUpRecommended\"",
                        "\"suggestedFollowUp\""
                )
                .doesNotContain(
                        "\"totalScore\"",
                        "\"decisionAction\"",
                        "\"evaluationPhase\""
                );
    }

    @Test
    void givenNullContext_whenBuild_thenRejects() {
        assertThatThrownBy(() -> promptBuilder.build(
                null,
                retrievalResult(List.of(), List.of())
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationContext");
    }

    @Test
    void givenNullRetrievalResult_whenBuild_thenRejects() {
        assertThatThrownBy(() -> promptBuilder.build(mainContext(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationRetrievalResult");
    }

    private String jsonContract(String systemPrompt) {
        String marker = "JSON 格式必须为：";
        int contractStart = systemPrompt.indexOf(marker);

        assertThat(contractStart).isNotNegative();
        return systemPrompt.substring(contractStart + marker.length());
    }

    private EvaluationRetrievalResult retrievalResult(
            List<VectorSearchHit> rawHits,
            List<RetrievedChunk> evidence
    ) {
        return new EvaluationRetrievalResult(
                RETRIEVAL_QUERY,
                5,
                "embedding-model",
                "embedding-profile-v1",
                rawHits,
                evidence
        );
    }

    private RetrievedChunk evidence(
            int vectorRank,
            double similarityScore,
            String title,
            String source,
            String content
    ) {
        return new RetrievedChunk(
                vectorRank,
                similarityScore,
                100L + vectorRank,
                200L + vectorRank,
                1,
                vectorRank,
                "eligible-vector-" + vectorRank,
                title,
                KnowledgeCategory.SPRING,
                source,
                content
        );
    }

    private EvaluationContext mainContext() {
        return context(EvaluationMode.MAIN_ANSWER);
    }

    private EvaluationContext followUpContext() {
        return context(EvaluationMode.FOLLOW_UP_ANSWER);
    }

    private EvaluationContext context(EvaluationMode mode) {
        boolean followUp = mode == EvaluationMode.FOLLOW_UP_ANSWER;

        return new EvaluationContext(
                followUp ? 202L : 201L,
                followUp ? 302L : 301L,
                201L,
                301L,
                mode,
                QuestionCategory.JAVA_COLLECTION,
                "KNOWLEDGE-POINT-HASHMAP",
                "MAIN-QUESTION-CONTENT",
                "REFERENCE-ANSWER-SNAPSHOT",
                List.of(
                        new ScoringPointSnapshot(
                                101L,
                                QuestionPointType.CORE,
                                "SCORING-POINT-ONE",
                                60
                        ),
                        new ScoringPointSnapshot(
                                102L,
                                QuestionPointType.KEY,
                                "SCORING-POINT-TWO",
                                40
                        )
                ),
                "MAIN-ANSWER-CONTENT",
                followUp ? "FOLLOW-UP-QUESTION-CONTENT" : null,
                followUp ? "FOLLOW-UP-ANSWER-CONTENT" : null,
                followUp ? List.of(102L) : List.of()
        );
    }
}
