package com.sm.instagram.platform.subscription.entity;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_subscription")
public class CompanySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_subscription_generator")
    @SequenceGenerator(
            name = "company_subscription_generator",
            sequenceName = "company_subscription_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_plan_id", nullable = false)
    private SubscriptionPlan currentPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_plan_id")
    private SubscriptionPlan previousPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 30)
    private SubscriptionStatus previousState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_plan_id")
    private SubscriptionPlan targetPlan;

    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;

    @Column(name = "trial_used", nullable = false)
    private Boolean trialUsed = false;

    @Column(name = "stripe_customer_id", length = 100)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 100)
    private String stripeSubscriptionId;

    @Column(name = "stripe_schedule_id", length = 100)
    private String stripeScheduleId;

    @Column(name = "newest_terms_accepted", nullable = false)
    private Boolean newestTermsAccepted = true;

    @Column(name = "grace_deadline")
    private LocalDateTime graceDeadline;

    @Column(name = "updater_id")
    private Long updaterId;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "last_update_time", nullable = false)
    private LocalDateTime lastUpdateTime;
}
