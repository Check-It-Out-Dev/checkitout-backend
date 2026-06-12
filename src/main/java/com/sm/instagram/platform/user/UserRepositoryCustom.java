package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.repository.MultiBagFetchRepository;

/**
 * Custom repository methods for User to handle complex fetching scenarios
 * and avoid MultipleBagFetchException by using multiple queries.
 */
public interface UserRepositoryCustom extends MultiBagFetchRepository<User, Long> {
    // Additional custom methods specific to User can be added here
}
