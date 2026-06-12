package com.sm.instagram.platform.auth.entity;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Tracks Meta data deletion requests that may be deferred
 * due to active collaborations.
 */
@Entity
@Table(name = "pending_data_deletion_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingDataDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pending_data_deletion_request_gen")
    @SequenceGenerator(
            name = "pending_data_deletion_request_gen",
            sequenceName = "pending_data_deletion_request_seq",
            allocationSize = 50
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "confirmation_code", nullable = false, unique = true, length = 36)
    private String confirmationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeletionRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blockers", columnDefinition = "jsonb")
    private String blockers;
}
