package com.sm.instagram.platform.appliedopportunities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ContentApprovalStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    NEEDS_REVISION("needs_revision"),
    SUBMITTED("submitted");

    private final String value;

    ContentApprovalStatus(String value) {
        this.value = value;
    }

    @JsonCreator
    public static ContentApprovalStatus fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    public boolean canTransitionTo(ContentApprovalStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == SUBMITTED || newStatus == APPROVED || newStatus == REJECTED;
            case SUBMITTED -> newStatus == APPROVED || newStatus == REJECTED || newStatus == NEEDS_REVISION;
            case NEEDS_REVISION -> newStatus == SUBMITTED || newStatus == REJECTED;
            case APPROVED -> newStatus == REJECTED;
            case REJECTED -> newStatus == PENDING || newStatus == SUBMITTED;
        };
    }

    @Override
    public String toString() {
        return value;
    }
}
