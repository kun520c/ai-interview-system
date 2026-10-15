package com.kun.aiinterview.knowledge.retrieval;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "rag.retrieval")
public class RetrievalProperties {

    private boolean cosineThresholdEnabled = false;

    // Milvus returns Float32 COSINE scores; use the same precision for the inclusive cutoff.
    private float minCosineScore = 0.70F;

    @AssertTrue(message = "最低COSINE分数必须是[-1, 1]范围内的有限数值")
    public boolean isMinCosineScoreValid() {
        return Float.isFinite(minCosineScore)
                && minCosineScore >= -1.0F && minCosineScore <= 1.0F;
    }
}
