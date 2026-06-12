package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.user.*;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for UserService integration tests.
 * Provides common fixtures and helper methods for testing user-related functionality.
 */
public abstract class UserServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected UserPreferencesRepository userPreferencesRepository;

    /**
     * Creates a user with a specific account status.
     */
    protected User createUserWithStatus(String firebaseUid, UserType userType, AccountStatus status) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(status);
        user.setEmail(firebaseUid + "@test.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPhoneNumber("+48123456789");
        return userRepository.save(user);
    }

    /**
     * Creates a user with addresses.
     */
    protected User createUserWithAddresses(String firebaseUid, UserType userType, List<Address> addresses) {
        User user = createUserWithStatus(firebaseUid, userType, AccountStatus.ACTIVE);

        for (Address address : addresses) {
            address.setUser(user);
            addressRepository.save(address);
        }

        user.setAddresses(new ArrayList<>(addresses));
        return userRepository.save(user);
    }

    /**
     * Creates a pending admin user (admin without 2FA).
     */
    protected User createPendingAdminUser(String firebaseUid) {
        return createUserWithStatus(firebaseUid, UserType.PENDING_ADMIN, AccountStatus.IN_VALIDATION);
    }

    /**
     * Creates an address for a user.
     */
    protected Address createAddressForUser(User user, boolean isPrimary, String addressType) {
        Address address = new Address();
        address.setUser(user);
        address.setPrimary(isPrimary);
        address.setAddressType(addressType);
        address.setStreet("Test Street " + System.currentTimeMillis());
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setState("Mazowieckie");
        return addressRepository.save(address);
    }

    /**
     * Creates a complete user profile (all required fields for profile completeness).
     */
    protected User createCompleteUserProfile(String firebaseUid, UserType userType) {
        User user = createUserWithStatus(firebaseUid, userType, AccountStatus.ACTIVE);
        user.setFirstName("Complete");
        user.setLastName("User");
        user.setEmail(firebaseUid + "@complete.com");
        user.setPhoneNumber("+48999888777");
        user = userRepository.save(user);

        // Create primary address with all required fields
        Address primaryAddress = createAddressForUser(user, true, "MAIN");
        user.getAddresses().add(primaryAddress);

        return user;
    }

    /**
     * Creates user preferences for a user.
     */
    protected UserPreferences createUserPreferences(User user) {
        UserPreferences preferences = new UserPreferences();
        preferences.setUser(user);
        preferences.setNotificationEmailEnabled(true);
        preferences.setNotificationPushEnabled(true);
        preferences.setNotificationSmsEnabled(false);
        preferences.setDarkModeEnabled(false);
        preferences.setLanguage("en");
        preferences.setTimezone("Europe/Warsaw");
        return userPreferencesRepository.save(preferences);
    }

    /**
     * Creates a second admin user (for testing last admin scenarios).
     */
    protected User createSecondAdmin(String firebaseUid) {
        return createUserWithStatus(firebaseUid, UserType.ADMIN, AccountStatus.ACTIVE);
    }
}
