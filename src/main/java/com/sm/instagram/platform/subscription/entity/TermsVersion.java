package com.sm.instagram.platform.subscription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "terms_version")
public class TermsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "terms_version_generator")
    @SequenceGenerator(
            name = "terms_version_generator",
            sequenceName = "terms_version_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @Column(name = "version", nullable = false, unique = true)
    private Integer version;

    @Column(name = "content_hash", nullable = false, length = 100)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> pricingSnapshot;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays = 38;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
