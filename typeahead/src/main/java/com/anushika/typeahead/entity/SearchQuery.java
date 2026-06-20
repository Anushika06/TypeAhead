package com.anushika.typeahead.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_queries")
@Getter
@Setter
@NoArgsConstructor
public class SearchQuery {

    @Id
    @Column(name = "query", nullable = false)
    private String query;

    @Column(name = "total_count", nullable = false)
    private Long totalCount;

    @Column(name = "trend_score", nullable = false)
    private Double trendScore;

    // Tracks the last time decay was applied to trend_score.
    // Used by the decay scheduler to apply: trend_score = trend_score * 0.9
    @Column(name = "last_decay_at", nullable = false)
    private LocalDateTime lastDecayAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ranking_score is intentionally NOT a field here.
    // It is derived at query time as: log(total_count + 1) + log(trend_score + 1)
    // Storing it would create consistency issues across decay cycles.
}
