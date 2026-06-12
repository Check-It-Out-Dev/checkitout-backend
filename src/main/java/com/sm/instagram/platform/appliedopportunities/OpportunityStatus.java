package com.sm.instagram.platform.appliedopportunities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lifecycle states of an {@link AppliedOpportunity}.
 *
 * <p>The constant <i>name</i> is the persisted form ({@code @Enumerated(
 * EnumType.STRING)} on the entity), so renaming any constant here requires a
 * data migration on the {@code applied_opportunity.opportunity_status}
 * column. The other fields ({@code colorTheme}, {@code icon},
 * {@code aliases}, {@code description}) are presentation metadata consumed
 * by the legend / detail UI; changes there are safe.
 *
 * <p>The state machine is:
 * <pre>
 *   APPLIED ── accept ──▶ ACCEPTED_BY_COMPANY ── accept ──▶ ACCEPTED_BY_INFLUENCER
 *      │                          │                                 │
 *      └─ reject ─▶ REJECTED_BY_COMPANY    REJECTED_BY_INFLUENCER ◀─┘ (reject)
 *
 *   ACCEPTED_BY_INFLUENCER ──▶ CONTENT_SEND_TO_ACCEPT
 *                                       │
 *                          ┌─ accept ──┴── reject ─┐
 *                          ▼                       ▼
 *                  CONTENT_APPROVED         CONTENT_REJECTED
 *                          │                       │
 *                          ▼                accept │ reject
 *                   CONTENT_POSTED      CONTENT_SEND_TO_ACCEPT or REJECTED_BY_INFLUENCER
 *                          │
 *                  ┌─ accept ─┴─ reject ─┐
 *                  ▼                     ▼
 *              TO_BE_PAID         CONTENT_POSTED_REJECTED ──▶ CONTENT_POSTED
 *                  │
 *                  ▼ (accept only)
 *                 DONE
 * </pre>
 *
 * <p>Terminal states are the three "ended" ones: {@link #REJECTED_BY_COMPANY},
 * {@link #REJECTED_BY_INFLUENCER}, {@link #DONE}.
 */
@Getter
@AllArgsConstructor
public enum OpportunityStatus {

    APPLIED("primary", "user", List.of("waiting"),
            "Influencer has applied for the opportunity and is waiting for company response"),

    ACCEPTED_BY_COMPANY("success", "building", List.of("approved", "accepted"),
            "Company has accepted the application and influencer needs to respond"),

    REJECTED_BY_COMPANY("danger", "x-circle", List.of("rejected", "declined"),
            "Company has rejected the application - collaboration ended"),

    ACCEPTED_BY_INFLUENCER("success", "user-check", List.of("confirmed"),
            "Influencer has accepted and now needs to create content"),

    REJECTED_BY_INFLUENCER("danger", "user-x", List.of("declined"),
            "Influencer has rejected the opportunity - collaboration ended"),

    CONTENT_SEND_TO_ACCEPT("warning", "upload", List.of("pending-review"),
            "Content has been submitted and is awaiting company approval"),

    CONTENT_APPROVED("success", "check-circle", List.of("approved"),
            "Content has been approved and can now be posted"),

    CONTENT_REJECTED("danger", "x-circle", List.of("needs-revision"),
            "Content was rejected and needs to be revised and resubmitted"),

    CONTENT_POSTED("info", "share", List.of("published"),
            "Content has been posted on social media and awaits verification"),

    CONTENT_POSTED_REJECTED("warning", "alert-triangle", List.of("invalid-post"),
            "Posted content was deemed invalid and needs to be corrected"),

    TO_BE_PAID("warning", "dollar-sign", List.of("payment-pending"),
            "Content is approved and payment is being processed"),

    DONE("success", "check-circle-2", List.of("completed", "finished"),
            "Collaboration completed successfully - both parties can rate");

    private static final Set<OpportunityStatus> TERMINAL_STATES = EnumSet.of(
            REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER, DONE);

    private final String colorTheme;
    private final String icon;
    private final List<String> aliases;
    private final String description;

    @JsonCreator
    public static OpportunityStatus fromString(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    /**
     * All non-terminal states — the ones that still represent an in-flight
     * cooperation, the ones the dashboard should surface.
     */
    public static Stream<OpportunityStatus> getActiveStatuses() {
        return Arrays.stream(values()).filter(s -> !TERMINAL_STATES.contains(s));
    }

    /**
     * All terminal states — collaboration concluded by rejection, completion,
     * or both-side acceptance leading to {@link #DONE}.
     */
    public static Stream<OpportunityStatus> getCompletedStatuses() {
        return TERMINAL_STATES.stream();
    }

    /**
     * Compute the next state for a given accept/reject decision.
     *
     * <p>Some transitions are accept-only — e.g. once content is approved
     * the only forward motion is "post it"; rejecting at that point is
     * meaningless and raises {@link IllegalArgumentException}. Likewise
     * {@link #ACCEPTED_BY_INFLUENCER} and {@link #TO_BE_PAID} only proceed
     * forward.
     *
     * <p>{@link #CONTENT_REJECTED} is the only state with two non-terminal
     * outgoing edges: the influencer can either resubmit
     * ({@code accept = true}) or resign ({@code accept = false}).
     */
    public static OpportunityStatus getNextStatus(OpportunityStatus currentStatus, boolean accept) {
        return switch (currentStatus) {
            case APPLIED -> accept ? ACCEPTED_BY_COMPANY : REJECTED_BY_COMPANY;
            case ACCEPTED_BY_COMPANY -> accept ? ACCEPTED_BY_INFLUENCER : REJECTED_BY_INFLUENCER;
            case ACCEPTED_BY_INFLUENCER -> {
                requireAccept(accept,
                        "Cannot reject opportunity at this stage. Only transition to content submission is allowed.");
                yield CONTENT_SEND_TO_ACCEPT;
            }
            case CONTENT_SEND_TO_ACCEPT -> accept ? CONTENT_APPROVED : CONTENT_REJECTED;
            case CONTENT_APPROVED, CONTENT_POSTED_REJECTED -> {
                requireAccept(accept, "Cannot set status to 'rejected' at this stage.");
                yield CONTENT_POSTED;
            }
            case CONTENT_POSTED -> accept ? TO_BE_PAID : CONTENT_POSTED_REJECTED;
            case TO_BE_PAID -> {
                requireAccept(accept, "Cannot reject opportunity at this stage.");
                yield DONE;
            }
            case CONTENT_REJECTED -> accept ? CONTENT_SEND_TO_ACCEPT : REJECTED_BY_INFLUENCER;
            default -> throw new IllegalStateException(
                    "Current status (" + currentStatus + ") does not allow further transitions.");
        };
    }

    private static void requireAccept(boolean accept, String message) {
        if (!accept) {
            throw new IllegalArgumentException(message);
        }
    }

    @JsonValue
    public String getValue() {
        return name();
    }

    /**
     * Resolve the localized display label, falling back to the enum name when
     * the dictionary has no entry for the requested locale.
     */
    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "OPPORTUNITY_STATUS_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }

    /**
     * Resolve the localized long-form description, falling back to the
     * English default baked into the enum.
     */
    public String getDescription(DictionaryService dictionaryService, Locale locale) {
        String key = "OPPORTUNITY_STATUS_" + name() + "_DESC";
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(description);
    }

    /**
     * Whether {@code this} state can legally advance to {@code newStatus}.
     * The implementation mirrors {@link #getNextStatus(OpportunityStatus,
     * boolean)} — both define the same edge set.
     */
    public boolean canTransitionTo(OpportunityStatus newStatus) {
        return switch (this) {
            case APPLIED ->
                    newStatus == ACCEPTED_BY_COMPANY || newStatus == REJECTED_BY_COMPANY;
            case ACCEPTED_BY_COMPANY ->
                    newStatus == ACCEPTED_BY_INFLUENCER || newStatus == REJECTED_BY_INFLUENCER;
            case ACCEPTED_BY_INFLUENCER -> newStatus == CONTENT_SEND_TO_ACCEPT;
            case CONTENT_REJECTED ->
                    newStatus == CONTENT_SEND_TO_ACCEPT || newStatus == REJECTED_BY_INFLUENCER;
            case CONTENT_SEND_TO_ACCEPT ->
                    newStatus == CONTENT_APPROVED || newStatus == CONTENT_REJECTED;
            case CONTENT_APPROVED, CONTENT_POSTED_REJECTED -> newStatus == CONTENT_POSTED;
            case CONTENT_POSTED ->
                    newStatus == TO_BE_PAID || newStatus == CONTENT_POSTED_REJECTED;
            case TO_BE_PAID -> newStatus == DONE;
            default -> false;
        };
    }

    /**
     * Enumerate every state {@code this} can transition to.
     */
    public List<OpportunityStatus> getPossibleTransitions() {
        return Arrays.stream(values())
                .filter(this::canTransitionTo)
                .toList();
    }

    /**
     * Whether this state ends the cooperation lifecycle — no further
     * transitions are legal. Equivalent to membership in
     * {@link #TERMINAL_STATES}.
     */
    public boolean isTerminalStatus() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * Whether this state represents a successful completion (as opposed to a
     * rejection).
     */
    public boolean isSuccessfulCompletion() {
        return this == DONE;
    }
}
