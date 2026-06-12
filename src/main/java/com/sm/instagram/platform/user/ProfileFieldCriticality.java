package com.sm.instagram.platform.user;

import java.util.Set;

/**
 * Defines field criticality for profile updates.
 *
 * Critical fields trigger account re-verification when changed.
 * Non-critical fields can be updated without triggering re-verification.
 */
public enum ProfileFieldCriticality {
    CRITICAL,
    NON_CRITICAL;

    /**
     * Fields that trigger re-verification when changed by non-admin users.
     * These fields are considered critical for identity verification.
     */
    private static final Set<String> CRITICAL_FIELDS = Set.of(
        "firstName",
        "lastName",
        "email",
        "phoneNumber",
        "name",
        "nip"
    );

    /**
     * Fields that do NOT trigger re-verification.
     * Users can freely update these without admin approval.
     */
    private static final Set<String> NON_CRITICAL_FIELDS = Set.of(
        "profilePicture",
        "companyDescription",
        "addresses",
        "addressesIds",
        "addressIds"
    );

    public static ProfileFieldCriticality forField(String fieldName) {
        if (CRITICAL_FIELDS.contains(fieldName)) {
            return CRITICAL;
        }
        return NON_CRITICAL;
    }

    public static boolean isCriticalField(String fieldName) {
        return CRITICAL_FIELDS.contains(fieldName);
    }

    public static Set<String> getCriticalFields() {
        return CRITICAL_FIELDS;
    }

    public static Set<String> getNonCriticalFields() {
        return NON_CRITICAL_FIELDS;
    }
}
