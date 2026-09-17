package com.kun.aiinterview.interview.report.deepseek;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekJsonCompletionResult;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.report.prompt.InterviewReportPrompt;
import com.kun.aiinterview.interview.report.prompt.InterviewReportPromptBuilder;
import com.kun.aiinterview.interview.service.InterviewReportGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "deepseek",
        name = "enabled",
        havingValue = "true"
)
public class DeepSeekInterviewReportGenerator
        implements InterviewReportGenerator {

    static final int MAX_SUMMARY_LENGTH = 2000;
    static final int MAX_LIST_ITEM_COUNT = 5;
    static final int MAX_LIST_ITEM_LENGTH = 500;

    private final DeepSeekEvaluationClient deepSeekClient;
    private final DeepSeekProperties properties;
    private final InterviewReportPromptBuilder promptBuilder;
    private final EvaluationStandard evaluationStandard;
    private final ObjectMapper objectMapper;

    public DeepSeekInterviewReportGenerator(
            DeepSeekEvaluationClient deepSeekClient,
            DeepSeekProperties properties,
            InterviewReportPromptBuilder promptBuilder,
            EvaluationStandard evaluationStandard,
            ObjectMapper objectMapper
    ) {
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.evaluationStandard = evaluationStandard;
        this.objectMapper = objectMapper;
    }

    @Override
    public InterviewReportGenerationResult generate(
            InterviewSession session,
            List<AnswerEvaluation> evaluations,
            BigDecimal overallScore
    ) {
        InterviewReportPrompt prompt = promptBuilder.build(
                session,
                evaluations,
                overallScore
        );
        DeepSeekJsonCompletionResult completion =
                deepSeekClient.completeJson(
                        prompt.systemPrompt(),
                        prompt.userPrompt()
                );
        DeepSeekInterviewReportResponse response =
                parseResponse(completion.rawJson());

        return new InterviewReportGenerationResult(
                evaluationStandard
                        .resolveLevel(overallScore)
                        .name(),
                validateSummary(response.summary()),
                validateTextList(
                        "strengths",
                        response.strengths()
                ),
                validateTextList(
                        "weaknesses",
                        response.weaknesses()
                ),
                validateTextList(
                        "suggestions",
                        response.suggestions()
                ),
                properties.getModel(),
                prompt.promptVersion()
        );
    }

    private DeepSeekInterviewReportResponse parseResponse(
            String rawJson
    ) {
        try {
            DeepSeekInterviewReportResponse response =
                    objectMapper.readValue(
                            rawJson,
                            DeepSeekInterviewReportResponse.class
                    );
            if (response == null) {
                throw new ExternalServiceException(
                        "DeepSeek Report JSON解析结果不能为空"
                );
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw new ExternalServiceException(
                    "DeepSeek Report结果不是可解析的JSON对象",
                    exception
            );
        }
    }

    private String validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new ExternalServiceException(
                    "DeepSeek Report summary不能为空"
            );
        }

        String normalized = summary.strip();
        if (normalized.length() > MAX_SUMMARY_LENGTH) {
            throw new ExternalServiceException(
                    "DeepSeek Report summary长度不能超过"
                            + MAX_SUMMARY_LENGTH
            );
        }
        return normalized;
    }

    private List<String> validateTextList(
            String fieldName,
            List<String> values
    ) {
        if (values == null) {
            throw new ExternalServiceException(
                    "DeepSeek Report " + fieldName + "不能为空"
            );
        }
        if (values.isEmpty()
                || values.size() > MAX_LIST_ITEM_COUNT) {
            throw new ExternalServiceException(
                    "DeepSeek Report " + fieldName
                            + "数量必须在1到"
                            + MAX_LIST_ITEM_COUNT + "之间"
            );
        }

        List<String> normalized = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value == null || value.isBlank()) {
                throw new ExternalServiceException(
                        "DeepSeek Report " + fieldName
                                + "包含空白内容，索引：" + index
                );
            }

            String normalizedValue = value.strip();
            if (normalizedValue.length() > MAX_LIST_ITEM_LENGTH) {
                throw new ExternalServiceException(
                        "DeepSeek Report " + fieldName
                                + "单项长度不能超过"
                                + MAX_LIST_ITEM_LENGTH
                                + "，索引：" + index
                );
            }
            normalized.add(normalizedValue);
        }
        return List.copyOf(normalized);
    }
}
