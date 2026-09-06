package com.fbanalyzer.sentiment;

import com.fbanalyzer.model.SentimentLevel;
import com.fbanalyzer.model.SentimentScore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaderAnalyzerTest {

    private static VaderAnalyzer analyzer;

    @BeforeAll
    static void setUp() {
        analyzer = VaderAnalyzer.createDefault();
    }

    @Test
    void shouldScoreStronglyPositiveText() {
        SentimentScore score = analyzer.analyze("The product is amazing, absolutely fantastic and great!");

        assertThat(score.compound()).isGreaterThan(0.5);
        assertThat(score.level()).isEqualTo(SentimentLevel.POSITIVE);
        assertThat(score.isNegative()).isFalse();
    }

    @Test
    void shouldScoreStronglyNegativeText() {
        SentimentScore score = analyzer.analyze("This service is terrible, horrible, and completely broken. Total scam!");

        assertThat(score.compound()).isLessThan(-0.5);
        assertThat(score.level()).isEqualTo(SentimentLevel.CRITICAL_NEGATIVE);
        assertThat(score.isNegative()).isTrue();
    }

    @Test
    void shouldScoreMildlyNegativeText() {
        SentimentScore score = analyzer.analyze("The package was delayed.");

        assertThat(score.compound()).isLessThan(-0.05);
        assertThat(score.compound()).isGreaterThanOrEqualTo(-0.5);
        assertThat(score.level()).isEqualTo(SentimentLevel.WARNING_NEGATIVE);
        assertThat(score.isNegative()).isTrue();
    }

    @Test
    void shouldScoreNeutralText() {
        SentimentScore score = analyzer.analyze("The store opens at 9am tomorrow on Main Street.");

        assertThat(score.compound()).isBetween(-0.05, 0.05);
        assertThat(score.level()).isEqualTo(SentimentLevel.NEUTRAL);
        assertThat(score.isNegative()).isFalse();
    }

    @Test
    void shouldHandleNegationCorrectly() {
        SentimentScore directBad = analyzer.analyze("The app is bad.");
        SentimentScore notBad = analyzer.analyze("The app is not bad.");

        // "not bad" should have higher valence than "bad"
        assertThat(notBad.compound()).isGreaterThan(directBad.compound());
        assertThat(notBad.isNegative()).isFalse();
    }

    @Test
    void shouldBoostWithExclamations() {
        SentimentScore regular = analyzer.analyze("This is awful");
        SentimentScore amplified = analyzer.analyze("This is awful!!!");

        assertThat(amplified.compound()).isLessThan(regular.compound());
    }

    @Test
    void shouldBoostWithAllCaps() {
        SentimentScore regular = analyzer.analyze("This is terrible");
        SentimentScore amplified = analyzer.analyze("This is TERRIBLE");

        assertThat(amplified.compound()).isLessThan(regular.compound());
    }

    @Test
    void shouldHandleNullOrBlank() {
        SentimentScore nullScore = analyzer.analyze(null);
        SentimentScore blankScore = analyzer.analyze("   ");

        assertThat(nullScore.compound()).isEqualTo(0.0);
        assertThat(nullScore.level()).isEqualTo(SentimentLevel.NEUTRAL);
        assertThat(blankScore.compound()).isEqualTo(0.0);
    }
}
