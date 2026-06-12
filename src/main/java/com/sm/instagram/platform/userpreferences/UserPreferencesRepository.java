package com.sm.instagram.platform.userpreferences;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.user.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferencesRepository extends BaseRepository<UserPreferences, Long> {
    UserPreferences findByUser(User user);
    UserPreferences findByUserId(Long userId);
}