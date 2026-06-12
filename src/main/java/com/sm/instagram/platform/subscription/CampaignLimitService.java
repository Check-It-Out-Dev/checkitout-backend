package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.subscription.entity.CompanySubscription;
import com.sm.instagram.platform.subscription.repository.BillingPeriodRepository;
import com.sm.instagram.platform.subscription.repository.CompanySubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignLimitService {

    private final CompanySubscriptionRepository companySubscriptionRepo;
    private final BillingPeriodRepository billingPeriodRepo;
    private final SubscriptionService subscriptionService;

    @Transactional
    public void enforceLimit(Long companyUserId) {
        var subscription = subscriptionService.getOrCreateSubscription(companyUserId);
        int limit = subscriptionService.resolveEffectiveCampaignLimit(subscription);

        var period = billingPeriodRepo.findActiveByUserIdForUpdate(companyUserId)
                .orElseThrow(() -> new EntityNotFoundException("No active billing period for user: " + companyUserId));

        long count = billingPeriodRepo.countCampaignsInPeriod(
                companyUserId, period.getStartDate(), period.getEndDate());

        if (count >= limit) {
            log.info("Campaign limit reached: userId={}, count={}, limit={}, plan={}",
                    companyUserId, count, limit, subscription.getCurrentPlan().getName());
            throw new CampaignLimitExceededException(limit, subscription.getCurrentPlan().getName());
        }
    }
}
