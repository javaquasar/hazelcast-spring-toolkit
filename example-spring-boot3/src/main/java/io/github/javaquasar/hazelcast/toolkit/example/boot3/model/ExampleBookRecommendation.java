package io.github.javaquasar.hazelcast.toolkit.example.boot3.model;

import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact
public class ExampleBookRecommendation {

    private String bookId;
    private String reason;
    private Integer score;
    private String recommendedBy;
    private String[] relatedGenres;

    protected ExampleBookRecommendation() {
    }

    public ExampleBookRecommendation(
            String bookId,
            String reason,
            Integer score,
            String recommendedBy,
            String[] relatedGenres) {
        this.bookId = bookId;
        this.reason = reason;
        this.score = score;
        this.recommendedBy = recommendedBy;
        this.relatedGenres = relatedGenres;
    }

    public String getBookId() {
        return bookId;
    }

    public String getReason() {
        return reason;
    }

    public Integer getScore() {
        return score;
    }

    public String getRecommendedBy() {
        return recommendedBy;
    }

    public String[] getRelatedGenres() {
        return relatedGenres;
    }
}
