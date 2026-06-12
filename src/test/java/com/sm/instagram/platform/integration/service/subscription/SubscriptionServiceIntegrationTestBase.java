package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.subscription.CampaignLimitService;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Integration test base for subscription module.
 *
 * <p>Extends {@link BaseServiceIntegrationTest} which provides:
 * <ul>
 *   <li>Real PostgreSQL + Redis via TestContainers</li>
 *   <li>Pre-created testCompany, testInfluencer, testAdmin users</li>
 *   <li>authenticateAs() for SecurityContext</li>
 *   <li>@Transactional rollback after each test</li>
 * </ul>
 *
 * <p>Adds subscription-specific beans and helper methods.
 * Liquibase seeds 3 plans (FREE, BUSINESS, ENTERPRISE) automatically.
 */
public abstract class SubscriptionServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected SubscriptionService subscriptionService;

    @Autowired
    protected CampaignLimitService campaignLimitService;

    @Autowired
    protected StripeService stripeService;

    @Autowired
    protected CompanySubscriptionRepository companySubscriptionRepo;

    @Autowired
    protected SubscriptionPlanRepository subscriptionPlanRepo;

    @Autowired
    protected SubscriptionEventRepository subscriptionEventRepo;

    @Autowired
    protected BillingPeriodRepository billingPeriodRepo;

    @Autowired
    protected InvoiceRecordRepository invoiceRecordRepo;

    @Autowired
    protected TermsVersionRepository termsVersionRepo;

    protected SubscriptionPlan freePlan;
    protected SubscriptionPlan businessPlan;
    protected SubscriptionPlan enterprisePlan;

    @BeforeEach
    void setUpSubscriptionData() {
        freePlan = subscriptionPlanRepo.findByName("FREE")
                .orElseThrow(() -> new AssertionError("FREE plan not seeded by Liquibase"));
        businessPlan = subscriptionPlanRepo.findByName("BUSINESS")
                .orElseThrow(() -> new AssertionError("BUSINESS plan not seeded by Liquibase"));
        enterprisePlan = subscriptionPlanRepo.findByName("ENTERPRISE")
                .orElseThrow(() -> new AssertionError("ENTERPRISE plan not seeded by Liquibase"));
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    protected CompanySubscription createSubscriptionForUser(User user, SubscriptionPlan plan, SubscriptionStatus status) {
        var sub = new CompanySubscription();
        sub.setUser(user);
        sub.setCurrentPlan(plan);
        sub.setStatus(status);
        sub.setTrialUsed(false);
        sub.setNewestTermsAccepted(true);
        return companySubscriptionRepo.save(sub);
    }

    protected BillingPeriod createBillingPeriodForUser(User user, SubscriptionPlan plan,
                                                        LocalDateTime start, LocalDateTime end,
                                                        BillingPeriodStatus status) {
        var period = new BillingPeriod();
        period.setUser(user);
        period.setPlan(plan);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setStatus(status);
        return billingPeriodRepo.save(period);
    }

    protected SubscriptionEvent createEventForUser(User user, SubscriptionEventType type,
                                                    String stripeEventId) {
        var event = new SubscriptionEvent();
        event.setUser(user);
        event.setEventType(type);
        event.setStripeEventId(stripeEventId);
        return subscriptionEventRepo.save(event);
    }

    protected InvoiceRecord createInvoiceForUser(User user, InvoiceStatus status,
                                                  BigDecimal amount, int retryCount) {
        var invoice = new InvoiceRecord();
        invoice.setUser(user);
        invoice.setAmountPln(amount);
        invoice.setStatus(status);
        invoice.setRetryCount(retryCount);
        if (retryCount > 0) {
            invoice.setLastAttemptAt(LocalDateTime.now().minusMinutes(30));
        }
        return invoiceRecordRepo.save(invoice);
    }
}
