package com.sm.instagram.platform.support.ticket.models;

import org.springframework.context.MessageSource;
import java.util.Locale;

/**
 * Enum representing the categories of support tickets.
 * Categories help route tickets to the appropriate team or specialist.
 */
public enum TicketCategory {
    /**
     * Issues related to user accounts, login, registration, etc.
     */
    ACCOUNT_ISSUE,

    /**
     * Issues related to billing, payments, subscriptions, etc.
     */
    BILLING_PAYMENT,

    /**
     * Technical problems with the platform.
     */
    TECHNICAL_PROBLEM,

    /**
     * Requests for new features or improvements.
     */
    FEATURE_REQUEST,

    /**
     * Issues related to partnerships between influencers and companies.
     */
    PARTNERSHIP_ISSUE,

    /**
     * Issues related to content moderation, inappropriate content, etc.
     */
    CONTENT_MODERATION,

    /**
     * General inquiries that don't fit other categories.
     */
    GENERAL_INQUIRY,

    /**
     * Early access application.
     */
    EARLY_ACCESS_INTEREST,

    /**
     * IF other categories don't align with ticket topic
     */
    OTHER;

    /**
     * Get a human-readable label for this category based on the provided locale.
     *
     * @param messageSource The message source for localization
     * @param locale The locale to use for the translation
     * @return Localized category label
     */
    public String getLabel(MessageSource messageSource, Locale locale) {
        return messageSource.getMessage("support.ticket.category." + name(), null, locale);
    }

    /**
     * Get a user-friendly label for this category (without needing MessageSource).
     *
     * @return A user-friendly category label
     */
    public String getDisplayName() {
        return switch(this) {
            case ACCOUNT_ISSUE -> "Account Issue";
            case BILLING_PAYMENT -> "Billing & Payment";
            case TECHNICAL_PROBLEM -> "Technical Problem";
            case FEATURE_REQUEST -> "Feature Request";
            case PARTNERSHIP_ISSUE -> "Partnership Issue";
            case CONTENT_MODERATION -> "Content Moderation";
            case GENERAL_INQUIRY -> "General Inquiry";
            case EARLY_ACCESS_INTEREST -> "Early Access Interest";
            case OTHER -> "Other";
        };
    }
}