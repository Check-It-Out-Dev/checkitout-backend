package com.sm.instagram.platform.subscription.dto;

import com.sm.instagram.platform.subscription.entity.InvoiceRecord;
import com.sm.instagram.platform.subscription.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InvoiceRecordDtoOut {

    private Long id;
    private String invoiceType;
    private BigDecimal amountPln;
    private InvoiceStatus status;
    private int retryCount;
    private int maxRetries;
    private String errorMessage;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createdTime;

    public static InvoiceRecordDtoOut fromEntity(InvoiceRecord entity) {
        return InvoiceRecordDtoOut.builder()
                .id(entity.getId())
                .invoiceType(entity.getInvoiceType())
                .amountPln(entity.getAmountPln())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .maxRetries(entity.getMaxRetries())
                .errorMessage(entity.getErrorMessage())
                .lastAttemptAt(entity.getLastAttemptAt())
                .createdTime(entity.getCreatedTime())
                .build();
    }
}
