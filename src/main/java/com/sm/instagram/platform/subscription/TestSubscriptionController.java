package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.partnershipopportunities.CompensationType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.invoicing.InvoiceRetryService;
import com.sm.instagram.platform.subscription.invoicing.InvoicingPort;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Test-only endpoints for E2E subscription testing.
 * Manipulates subscription state directly, bypasses Stripe, triggers crons.
 *
 * <p><b>SECURITY:</b> Active in {@code e2e} and {@code dev} profiles only —
 * never in {@code prod} or {@code test}. Dev availability lets FE
 * integration tests exercise Stripe-bypassed subscription state in the dev
 * BE profile.
 */
@Slf4j
@RestController
@RequestMapping("/test/subscription")
@RequiredArgsConstructor
@Profile("(e2e | dev) & !prod & !test")
public class TestSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CompanySubscriptionRepository companySubscriptionRepo;
    private final SubscriptionPlanRepository subscriptionPlanRepo;
    private final SubscriptionEventRepository subscriptionEventRepo;
    private final BillingPeriodRepository billingPeriodRepo;
    private final InvoiceRecordRepository invoiceRecordRepo;
    private final InvoiceRetryService invoiceRetryService;
    private final InvoicingPort invoicingPort;
    private final UserRepository userRepo;
    private final CampaignLimitService campaignLimitService;
    private final PartnershipOpportunityRepository partnershipOpportunityRepo;

    @jakarta.persistence.PersistenceContext
    private transient jakarta.persistence.EntityManager entityManager;

    // =========================================================================
    // A: Set subscription state
    // =========================================================================

    @PostMapping("/set-state")
    @Transactional
    public ResponseEntity<Map<String, Object>> setState(@RequestBody Map<String, Object> request) {
        try {
            String email = (String) request.get("email");
            String statusStr = (String) request.get("status");
            String planName = (String) request.get("planName");

            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));
            SubscriptionPlan plan = subscriptionPlanRepo.findByName(planName)
                    .orElseThrow(() -> new RuntimeException("[E2E] Plan not found: " + planName));
            SubscriptionStatus status = SubscriptionStatus.valueOf(statusStr);

            var sub = companySubscriptionRepo.findByUserId(user.getId())
                    .orElseGet(() -> {
                        var s = new CompanySubscription();
                        s.setUser(user);
                        s.setNewestTermsAccepted(true);
                        s.setTrialUsed(false);
                        return s;
                    });

            sub.setCurrentPlan(plan);
            sub.setStatus(status);
            sub.setStripeCustomerId((String) request.get("stripeCustomerId"));
            sub.setStripeSubscriptionId((String) request.get("stripeSubscriptionId"));

            if (request.containsKey("trialUsed"))
                sub.setTrialUsed((Boolean) request.get("trialUsed"));
            if (request.containsKey("trialEndDate"))
                sub.setTrialEndDate(LocalDateTime.parse((String) request.get("trialEndDate")));
            if (request.containsKey("newestTermsAccepted"))
                sub.setNewestTermsAccepted((Boolean) request.get("newestTermsAccepted"));
            if (request.containsKey("graceDeadline"))
                sub.setGraceDeadline(LocalDateTime.parse((String) request.get("graceDeadline")));

            if (request.containsKey("previousPlanName")) {
                var prevPlan = subscriptionPlanRepo.findByName((String) request.get("previousPlanName")).orElse(null);
                sub.setPreviousPlan(prevPlan);
            }
            // Allow callers to seed previousState directly (e.g. when staging
            // a TERMS_PENDING subscription for accept-terms restoration tests).
            // The previous behavior derived previousState from statusStr,
            // which round-tripped to the new status — making it impossible
            // to test accept-terms-restores-previous-state without falling
            // back on global enterTermsPending().
            if (request.containsKey("previousStatus")) {
                sub.setPreviousState(
                        SubscriptionStatus.valueOf((String) request.get("previousStatus")));
            } else if (status != SubscriptionStatus.TERMS_PENDING) {
                // Non-TERMS_PENDING transitions clear previousState by
                // default (preserves the prior null-on-other-transitions
                // semantics).
                sub.setPreviousState(null);
            }
            if (request.containsKey("targetPlanName")) {
                sub.setTargetPlan(subscriptionPlanRepo.findByName((String) request.get("targetPlanName")).orElse(null));
            }

            sub = companySubscriptionRepo.save(sub);

            // Expire old billing periods, create fresh one
            billingPeriodRepo.findActiveByUserId(user.getId()).ifPresent(p -> {
                p.setStatus(BillingPeriodStatus.EXPIRED);
                billingPeriodRepo.save(p);
            });
            int months = request.containsKey("billingPeriodMonths") ? (Integer) request.get("billingPeriodMonths") : 1;
            var now = LocalDateTime.now();
            var period = new BillingPeriod();
            period.setUser(user);
            period.setPlan(plan);
            period.setStartDate(now);
            period.setEndDate(now.plusMonths(months));
            period.setStatus(BillingPeriodStatus.ACTIVE);
            period = billingPeriodRepo.save(period);

            log.info("[E2E] Set subscription state: userId={}, status={}, plan={}", user.getId(), status, planName);

            return ResponseEntity.ok(Map.of(
                    "userId", user.getId(),
                    "status", status.name(),
                    "planName", planName,
                    "subscriptionId", sub.getId(),
                    "billingPeriodId", period.getId(),
                    "billingPeriodStart", period.getStartDate().toString(),
                    "billingPeriodEnd", period.getEndDate().toString()
            ));
        } catch (Exception e) {
            log.error("[E2E] set-state failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // B: Get subscription state (raw internal)
    // =========================================================================

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState(@RequestParam String email) {
        try {
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));

            var subOpt = companySubscriptionRepo.findByUserId(user.getId());
            if (subOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("exists", false, "userId", user.getId()));
            }

            var sub = subOpt.get();
            var period = billingPeriodRepo.findActiveByUserId(user.getId()).orElse(null);
            long campaignsUsed = period != null
                    ? billingPeriodRepo.countCampaignsInPeriod(user.getId(), period.getStartDate(), period.getEndDate())
                    : 0;
            int effectiveLimit = subscriptionService.resolveEffectiveCampaignLimit(sub);

            return ResponseEntity.ok(Map.of(
                    "exists", true,
                    "userId", user.getId(),
                    "status", sub.getStatus().name(),
                    "currentPlanName", sub.getCurrentPlan().getName(),
                    "campaignLimit", sub.getCurrentPlan().getCampaignLimit(),
                    "effectiveCampaignLimit", effectiveLimit,
                    "campaignsUsedThisPeriod", campaignsUsed,
                    "trialUsed", sub.getTrialUsed(),
                    "newestTermsAccepted", sub.getNewestTermsAccepted(),
                    "hasStripeSubscription", sub.getStripeSubscriptionId() != null
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // C: Simulate webhook (bypass Stripe, call service directly)
    // =========================================================================

    @PostMapping("/simulate-webhook")
    @Transactional
    public ResponseEntity<Map<String, Object>> simulateWebhook(@RequestBody Map<String, Object> request) {
        try {
            String email = (String) request.get("email");
            String eventType = (String) request.get("eventType");

            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));
            var sub = companySubscriptionRepo.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("[E2E] No subscription for: " + email));

            String syntheticEventId = "e2e_evt_" + System.currentTimeMillis();
            String subId = sub.getStripeSubscriptionId() != null ? sub.getStripeSubscriptionId() : "e2e_sub_" + user.getId();

            // Set stripe subscription ID if not present (for webhook lookup)
            if (sub.getStripeSubscriptionId() == null) {
                sub.setStripeSubscriptionId(subId);
                if (sub.getStripeCustomerId() == null) sub.setStripeCustomerId("e2e_cus_" + user.getId());
                companySubscriptionRepo.save(sub);
            }

            switch (eventType) {
                case "checkout.session.completed" -> {
                    String planName = (String) request.getOrDefault("planName", "BUSINESS");
                    var targetPlan = subscriptionPlanRepo.findByName(planName).orElseThrow();

                    // Direct transition (avoids Stripe.retrieveSubscription call)
                    billingPeriodRepo.findActiveByUserId(user.getId()).ifPresent(p -> {
                        p.setStatus(BillingPeriodStatus.EXPIRED);
                        billingPeriodRepo.save(p);
                    });
                    sub.setPreviousPlan(sub.getCurrentPlan());
                    sub.setCurrentPlan(targetPlan);
                    sub.setStatus(switch (planName) {
                        case "BUSINESS" -> SubscriptionStatus.BUSINESS_ACTIVE;
                        case "ENTERPRISE" -> SubscriptionStatus.ENTERPRISE_ACTIVE;
                        default -> SubscriptionStatus.FREE_ACTIVE;
                    });
                    companySubscriptionRepo.save(sub);

                    var now = LocalDateTime.now();
                    var period = new BillingPeriod();
                    period.setUser(user); period.setPlan(targetPlan);
                    period.setStartDate(now); period.setEndDate(now.plusMonths(1));
                    period.setStatus(BillingPeriodStatus.ACTIVE);
                    billingPeriodRepo.save(period);

                    var event = new SubscriptionEvent();
                    event.setUser(user); event.setEventType(SubscriptionEventType.SUBSCRIPTION_CREATED);
                    event.setPlanFrom(sub.getPreviousPlan().getName()); event.setPlanTo(planName);
                    event.setStripeEventId(syntheticEventId);
                    subscriptionEventRepo.save(event);
                }
                case "invoice.paid" -> {
                    long amount = request.containsKey("amountPaidCents") ? ((Number) request.get("amountPaidCents")).longValue() : 2900L;
                    String currency = (String) request.getOrDefault("currency", "pln");
                    subscriptionService.handleInvoicePaid(syntheticEventId, subId, null, amount, currency);
                }
                case "invoice.payment_failed" -> subscriptionService.handlePaymentFailed(syntheticEventId, subId);
                case "customer.subscription.deleted" -> subscriptionService.handleSubscriptionDeleted(syntheticEventId, subId);
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            }

            var updated = companySubscriptionRepo.findByUserId(user.getId()).orElseThrow();
            log.info("[E2E] Simulated webhook: userId={}, eventType={}, resultingStatus={}", user.getId(), eventType, updated.getStatus());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "eventType", eventType,
                    "syntheticEventId", syntheticEventId,
                    "resultingStatus", updated.getStatus().name()
            ));
        } catch (Exception e) {
            log.error("[E2E] simulate-webhook failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // D: Reset subscription (cleanup)
    // =========================================================================

    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));

            companySubscriptionRepo.findByUserId(user.getId()).ifPresent(sub -> {
                // Delete events, invoices, billing periods, then subscription
                subscriptionEventRepo.deleteAll(subscriptionEventRepo.findAll().stream()
                        .filter(e -> e.getUser().getId().equals(user.getId())).toList());
                invoiceRecordRepo.deleteAll(invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(user.getId()));
                billingPeriodRepo.deleteAll(billingPeriodRepo.findAll().stream()
                        .filter(bp -> bp.getUser().getId().equals(user.getId())).toList());
                companySubscriptionRepo.delete(sub);
            });

            log.info("[E2E] Reset subscription: userId={}", user.getId());
            return ResponseEntity.ok(Map.of("success", true, "userId", user.getId()));
        } catch (Exception e) {
            log.error("[E2E] reset failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // E: Trigger cron processors
    // =========================================================================

    @PostMapping("/trigger-cron")
    public ResponseEntity<Map<String, Object>> triggerCron(@RequestBody Map<String, String> request) {
        try {
            String processor = request.get("processor");
            switch (processor) {
                case "expiredTrials" -> subscriptionService.processExpiredTrials();
                case "expiredDowngrades" -> subscriptionService.processExpiredDowngrades();
                case "expiredGracePeriods" -> subscriptionService.processExpiredGracePeriods();
                case "trialExpiryInTermsPending" -> subscriptionService.processTrialExpiryInTermsPending();
                case "all" -> {
                    subscriptionService.processExpiredTrials();
                    subscriptionService.processExpiredDowngrades();
                    subscriptionService.processExpiredGracePeriods();
                    subscriptionService.processTrialExpiryInTermsPending();
                }
                default -> throw new IllegalArgumentException("Unknown processor: " + processor);
            }
            log.info("[E2E] Triggered cron: {}", processor);
            return ResponseEntity.ok(Map.of("success", true, "processor", processor));
        } catch (Exception e) {
            log.error("[E2E] trigger-cron failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // F: Get invoices
    // =========================================================================

    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> getInvoices(@RequestParam String email) {
        try {
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));
            var invoices = invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(user.getId());
            var invoiceList = invoices.stream().map(inv -> Map.<String, Object>of(
                    "id", inv.getId(),
                    "status", inv.getStatus().name(),
                    "amountPln", inv.getAmountPln(),
                    "retryCount", inv.getRetryCount()
            )).toList();
            return ResponseEntity.ok(Map.of("userId", user.getId(), "invoiceCount", invoices.size(), "invoices", invoiceList));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // G: Trigger invoice retry
    // =========================================================================

    @PostMapping("/trigger-invoice-retry")
    public ResponseEntity<Map<String, Object>> triggerInvoiceRetry() {
        try {
            invoiceRetryService.retryFailedInvoices();
            log.info("[E2E] Invoice retry triggered");
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // H: Enter TERMS_PENDING
    // =========================================================================

    @PostMapping("/enter-terms-pending")
    public ResponseEntity<Map<String, Object>> enterTermsPending(@RequestBody(required = false) Map<String, String> request) {
        try {
            int count = subscriptionService.enterTermsPending();
            log.info("[E2E] Entered TERMS_PENDING for {} subscriptions", count);
            return ResponseEntity.ok(Map.of("success", true, "subscriptionsAffected", count));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // J: Create campaign (bypasses full PO validation for limit testing)
    // =========================================================================

    @PostMapping("/create-campaign")
    @Transactional
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody Map<String, String> request) {
        try {
            String campaignName = request.getOrDefault("campaignName", "E2E Test Campaign");

            // Get authenticated user from security context
            String firebaseUid = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
            User user = userRepo.findByFirebaseUserId(firebaseUid)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found for UID: " + firebaseUid));

            // Resolve subscription + limit + period for the user.
            var subscription = subscriptionService.getOrCreateSubscription(user.getId());
            int limit = subscriptionService.resolveEffectiveCampaignLimit(subscription);
            var period = billingPeriodRepo.findActiveByUserIdForUpdate(user.getId())
                    .orElseThrow(() -> new RuntimeException("[E2E] No active billing period"));

            // Atomic count-and-insert: a single SQL statement that performs the
            // INSERT only if the running count is below the limit. INSERT...SELECT
            // with a WHERE on COUNT() runs the count and the insert in the same
            // statement / same lock, so the next request always sees prior commits
            // (no Hibernate auto-flush quirks, no race between separate count + insert
            // statements). executeUpdate() returns 1 on insert, 0 when blocked.
            //
            // Note: PartnershipOpportunity.city_id and address_id are NULL-able in
            // the actual schema (see common/001-schema/002-tables.sql); the JPA-level
            // @JoinColumn(nullable = false) annotations are advisory only here —
            // intentionally bypassed for this minimal E2E fixture.
            LocalDateTime now = LocalDateTime.now();
            int rowsInserted = entityManager.createNativeQuery(
                    "INSERT INTO partnership_opportunity (id, company_id, name, title, details, " +
                    "compensation_type, compensation_amount_min, followers_min, followers_max, " +
                    "start_date, end_date, active, version, created_time, last_update_time) " +
                    "SELECT nextval('partnership_opportunity_seq'), :companyId, :name, :title, " +
                    "       'E2E test', 'CASH', 100, 1, 1000000, :startDate, :endDate, true, 0, " +
                    "       :createdTime, :lastUpdateTime " +
                    "WHERE (SELECT COUNT(*) FROM partnership_opportunity " +
                    "       WHERE company_id = :companyId AND created_time BETWEEN :periodStart AND :periodEnd) < :limit")
                    .setParameter("companyId", user.getId())
                    .setParameter("name", campaignName)
                    .setParameter("title", campaignName)
                    .setParameter("startDate", now.plusDays(1))
                    .setParameter("endDate", now.plusDays(30))
                    .setParameter("createdTime", now)
                    .setParameter("lastUpdateTime", now)
                    .setParameter("periodStart", period.getStartDate())
                    .setParameter("periodEnd", period.getEndDate())
                    .setParameter("limit", limit)
                    .executeUpdate();

            if (rowsInserted == 0) {
                log.info("[E2E] Campaign blocked by limit: userId={}, limit={}, plan={}",
                        user.getId(), limit, subscription.getCurrentPlan().getName());
                return ResponseEntity.status(409).body(Map.of(
                        "success", false, "error", "CAMPAIGN_LIMIT",
                        "limit", limit));
            }

            log.info("[E2E] Campaign created: userId={}, name={}", user.getId(), campaignName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "campaignName", campaignName));
        } catch (Exception e) {
            log.error("[E2E] create-campaign failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // I: Accept terms
    // =========================================================================

    @PostMapping("/accept-terms")
    public ResponseEntity<Map<String, Object>> acceptTerms(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("[E2E] User not found: " + email));
            subscriptionService.acceptTerms(user.getId());
            var sub = companySubscriptionRepo.findByUserId(user.getId()).orElse(null);
            String restored = sub != null ? sub.getStatus().name() : "none";
            log.info("[E2E] Accepted terms: userId={}, restoredStatus={}", user.getId(), restored);
            return ResponseEntity.ok(Map.of("success", true, "userId", user.getId(), "restoredStatus", restored));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
