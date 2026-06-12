package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "applied_opportunity_status_history")
public class AppliedOpportunityStatusHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "applied_opportunity_status_history_generator")
    @SequenceGenerator(
        name = "applied_opportunity_status_history_generator",
        sequenceName = "applied_opportunity_status_history_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_opportunity_id", nullable = false)
    @NotNull(message = "Applied opportunity cannot be null")
    private AppliedOpportunity appliedOpportunity;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private OpportunityStatus previousStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    @NotNull(message = "New status cannot be null")
    private OpportunityStatus newStatus;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedByUser;
    
    @Column(name = "changed_by_firebase_id")
    @Size(max = 255, message = "Firebase ID cannot exceed 255 characters")
    private String changedByFirebaseId;
    
    @Column(name = "changed_at", nullable = false, updatable = false)
    @CreationTimestamp
    @NotNull(message = "Changed at timestamp cannot be null")
    private LocalDateTime changedAt;
    
    @Column(name = "change_reason")
    @Size(max = 500, message = "Change reason cannot exceed 500 characters")
    private String changeReason;
    
    @Column(name = "notes")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;
    
    // Convenience constructor for creating new history entries
    public AppliedOpportunityStatusHistory(AppliedOpportunity appliedOpportunity, 
                                         OpportunityStatus previousStatus,
                                         OpportunityStatus newStatus,
                                         User changedByUser,
                                         String changedByFirebaseId,
                                         String changeReason) {
        this.appliedOpportunity = appliedOpportunity;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedByUser = changedByUser;
        this.changedByFirebaseId = changedByFirebaseId;
        this.changeReason = changeReason;
        this.changedAt = LocalDateTime.now();
    }
    
    // Convenience constructor without user entity (when only Firebase ID is available)
    public AppliedOpportunityStatusHistory(AppliedOpportunity appliedOpportunity, 
                                         OpportunityStatus previousStatus,
                                         OpportunityStatus newStatus,
                                         String changedByFirebaseId,
                                         String changeReason) {
        this.appliedOpportunity = appliedOpportunity;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedByFirebaseId = changedByFirebaseId;
        this.changeReason = changeReason;
        this.changedAt = LocalDateTime.now();
    }
}
