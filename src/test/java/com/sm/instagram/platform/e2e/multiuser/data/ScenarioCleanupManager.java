package com.sm.instagram.platform.e2e.multiuser.data;

import io.cucumber.spring.ScenarioScope;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Stack;

/**
 * Manages cleanup actions for scenario-scoped resources.
 * Actions are executed in LIFO order (last registered = first cleaned) when the scenario ends.
 *
 * <p>This ensures that resources are cleaned up in reverse dependency order.
 * For example, if you create a Campaign and then an Application for that Campaign,
 * the Application cleanup will run before the Campaign cleanup.
 *
 * <p>Usage example:
 * <pre>
 * &#64;Autowired
 * private ScenarioCleanupManager cleanup;
 *
 * public Campaign createCampaign(CreateCampaignRequest request) {
 *     Campaign campaign = campaignRepository.save(entity);
 *
 *     // Register cleanup - will run when scenario ends
 *     cleanup.onCleanup(() -> {
 *         campaignRepository.deleteById(campaign.getId());
 *         log.info("Cleaned up campaign: {}", campaign.getId());
 *     });
 *
 *     return campaign;
 * }
 * </pre>
 */
@Component
@ScenarioScope
@Slf4j
public class ScenarioCleanupManager {

    private final Stack<CleanupAction> cleanupActions = new Stack<>();

    /**
     * Registers a cleanup action to be executed after the scenario.
     * Actions execute in LIFO order (last registered = first cleaned).
     *
     * @param action the cleanup action to register
     */
    public void onCleanup(Runnable action) {
        cleanupActions.push(new CleanupAction(action, null));
    }

    /**
     * Registers a named cleanup action for better debugging.
     *
     * @param description description of what's being cleaned up
     * @param action the cleanup action to register
     */
    public void onCleanup(String description, Runnable action) {
        cleanupActions.push(new CleanupAction(action, description));
        log.debug("[E2E] Registered cleanup: {}", description);
    }

    /**
     * Returns the number of pending cleanup actions.
     *
     * @return cleanup action count
     */
    public int pendingCleanupCount() {
        return cleanupActions.size();
    }

    /**
     * Checks if there are any pending cleanup actions.
     *
     * @return true if cleanups are pending
     */
    public boolean hasPendingCleanup() {
        return !cleanupActions.isEmpty();
    }

    /**
     * Called by Spring when scenario scope ends.
     * Executes all cleanup actions in LIFO order.
     */
    @PreDestroy
    public void cleanup() {
        if (cleanupActions.isEmpty()) {
            log.debug("[E2E] No cleanup actions to execute");
            return;
        }

        log.info("[E2E] Running {} cleanup actions", cleanupActions.size());
        int successCount = 0;
        int failCount = 0;

        while (!cleanupActions.isEmpty()) {
            CleanupAction action = cleanupActions.pop();
            try {
                action.action.run();
                successCount++;
                if (action.description != null) {
                    log.debug("[E2E] Cleanup succeeded: {}", action.description);
                }
            } catch (Exception e) {
                failCount++;
                if (action.description != null) {
                    log.warn("[E2E] Cleanup failed for '{}': {}", action.description, e.getMessage());
                } else {
                    log.warn("[E2E] Cleanup action failed: {}", e.getMessage());
                }
            }
        }

        log.info("[E2E] Cleanup complete: {} succeeded, {} failed", successCount, failCount);
    }

    /**
     * Internal record to hold cleanup action with optional description.
     */
    private record CleanupAction(Runnable action, String description) {}
}
