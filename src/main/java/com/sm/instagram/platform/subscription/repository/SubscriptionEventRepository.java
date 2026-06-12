package com.sm.instagram.platform.subscription.repository;

import com.sm.instagram.platform.subscription.entity.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    boolean existsByStripeEventId(String stripeEventId);
}
