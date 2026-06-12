package com.sm.instagram.platform.subscription.entity;

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
@Table(name = "subscription_plan")
public class SubscriptionPlan {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 20, unique = true)
    private String name;

    @Column(name = "price_pln", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePln;

    @Column(name = "campaign_limit", nullable = false)
    private Integer campaignLimit;

    @Column(name = "stripe_price_id", length = 100)
    private String stripePriceId;

    @Column(name = "stripe_product_id", length = 100)
    private String stripeProductId;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
