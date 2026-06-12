package com.sm.instagram.platform.address;

/**
 * Enum representing the source type of an address.
 * This helps determine how the address should be handled when reused.
 */
public enum AddressSourceType {
    /**
     * Address was copied from a user's personal address.
     * These addresses maintain independence and historical accuracy.
     */
    COPIED_FROM_USER,
    
    /**
     * Address represents a business location that can be shared.
     * Multiple opportunities can reference the same business location.
     */
    BUSINESS_LOCATION,
    
    /**
     * Address was created specifically for this opportunity.
     * Behaves like COPIED_FROM_USER but wasn't derived from user address.
     */
    CUSTOM
}
