package com.sm.instagram.platform.appliedopportunities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppliedOpportunityStatusHistoryDtoOut {
    
    private Long id;
    private Long appliedOpportunityId;
    private OpportunityStatus previousStatus;
    private OpportunityStatus newStatus;
    private Long changedByUserId;
    private String changedByFirebaseId;
    private String changedByUserName; // Computed field for user display name
    private LocalDateTime changedAt;
    private String changeReason;
    private String notes;
    
    // Convenience method to create DTO from entity
    public static AppliedOpportunityStatusHistoryDtoOut fromEntity(AppliedOpportunityStatusHistory entity) {
        AppliedOpportunityStatusHistoryDtoOut dto = new AppliedOpportunityStatusHistoryDtoOut();
        dto.setId(entity.getId());
        dto.setAppliedOpportunityId(entity.getAppliedOpportunity().getId());
        dto.setPreviousStatus(entity.getPreviousStatus());
        dto.setNewStatus(entity.getNewStatus());
        dto.setChangedByFirebaseId(entity.getChangedByFirebaseId());
        dto.setChangedAt(entity.getChangedAt());
        dto.setChangeReason(entity.getChangeReason());
        dto.setNotes(entity.getNotes());
        
        // Set user information if available
        if (entity.getChangedByUser() != null) {
            dto.setChangedByUserId(entity.getChangedByUser().getId());
            dto.setChangedByUserName(entity.getChangedByUser().getFirstName() + " " + entity.getChangedByUser().getLastName());
        } else if ("SYSTEM".equals(entity.getChangedByFirebaseId())) {
            dto.setChangedByUserName("System");
        } else {
            dto.setChangedByUserName("Unknown User");
        }
        
        return dto;
    }
}
