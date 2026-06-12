package com.sm.instagram.platform.appliedopportunities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.contenttype.ContentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "applied_opportunity_content")
public class AppliedOpportunityContent implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "applied_opportunity_content_generator")
    @SequenceGenerator(
        name = "applied_opportunity_content_generator",
        sequenceName = "applied_opportunity_content_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_opportunity_id", nullable = false)
    @NotNull(message = "Applied opportunity cannot be blank")
    @JsonIgnore
    private AppliedOpportunity appliedOpportunity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_type_id", nullable = false)
    @NotNull(message = "Content type cannot be blank")
    @JsonIgnore
    private ContentType contentType;

    @Positive(message = "Content count must be positive")
    @Column(name = "content_count")
    private Integer contentCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "urls", columnDefinition = "jsonb")
    private List<String> urls;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(name = "description")
    private String description;

    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    @Column(name = "tags")
    private String tags;

    // Metadata fields
    @Column(name = "content_creation_date")
    private LocalDateTime contentCreationDate;

    @Column(name = "submission_date")
    private LocalDateTime submissionDate;

    @Column(name = "approval_status")
    @Enumerated(EnumType.STRING)
    private ContentApprovalStatus approvalStatus = ContentApprovalStatus.PENDING;

    @Size(max = 500, message = "Approval notes cannot exceed 500 characters")
    @Column(name = "approval_notes")
    private String approvalNotes;

    // Engagement metrics (can be updated later)
    @Column(name = "likes_count")
    private Long likesCount;

    @Column(name = "comments_count")
    private Long commentsCount;

    @Column(name = "views_count")
    private Long viewsCount;

    @Column(name = "shares_count")
    private Long sharesCount;

    // Social media link (where content was posted)
    @Size(max = 1000, message = "Social media link cannot exceed 1000 characters")
    @Column(name = "social_media_link")
    private String socialMediaLink;

    @Version
    @Column(name = "version")
    private Long version;

    // Standard tracking fields
    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdTime;

    @UpdateTimestamp
    private LocalDateTime lastUpdateTime = LocalDateTime.now();

    @Size(max = 255, message = "Firebase User ID cannot exceed 255 characters")
    private String updaterId;
}
