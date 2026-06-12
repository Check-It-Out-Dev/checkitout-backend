package com.sm.instagram.platform.subscription.entity;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "invoice_record")
public class InvoiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_record_generator")
    @SequenceGenerator(
            name = "invoice_record_generator",
            sequenceName = "invoice_record_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_period_id")
    private BillingPeriod billingPeriod;

    @Column(name = "fakturownia_invoice_id")
    private Long fakturowniaInvoiceId;

    @Column(name = "invoice_type", nullable = false, length = 20)
    private String invoiceType = "STANDARD";

    @Column(name = "amount_pln", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPln;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 5;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
