package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, Answer> answers) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String type, Double score, String choice,
                         Map<String, Double> probabilities, Double confidence,
                         Map<String, JsonNode> legend) {
    }
}
