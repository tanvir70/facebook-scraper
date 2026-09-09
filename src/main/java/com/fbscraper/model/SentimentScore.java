package com.fbscraper.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fbscraper.enums.SentimentLevel;

public record SentimentScore(
        double compound,
        double positive,
        double neutral,
        double negative,
        SentimentLevel level
) {
    @JsonCreator
    public static SentimentScore fromJson(
            @JsonProperty("compound") double compound,
            @JsonProperty("positive") double positive,
            @JsonProperty("neutral") double neutral,
            @JsonProperty("negative") JsonNode negativeNode,
            @JsonProperty("level") SentimentLevel level
    ) {
        double neg = (negativeNode != null && negativeNode.isNumber()) ? negativeNode.asDouble() : 0.0;
        return new SentimentScore(compound, positive, neutral, neg, level);
    }

    @JsonIgnore
    public boolean isNegative() {
        return level == SentimentLevel.CRITICAL_NEGATIVE || level == SentimentLevel.WARNING_NEGATIVE;
    }
}
