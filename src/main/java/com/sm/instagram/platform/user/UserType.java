package com.sm.instagram.platform.user;

import com.sm.instagram.platform.dictionary.DictionaryService;

import java.util.Locale;

public enum UserType {
    ADMIN,           // Full admin with 2FA configured
    PENDING_ADMIN,   // Admin without 2FA - limited access
    INFLUENCER,      // Influencer user type
    COMPANY;         // Company user type

    public String getLabel(DictionaryService dictionaryService, Locale locale) {
        String key = "USER_TYPE_" + name();
        return dictionaryService.getTranslation(key, locale.getLanguage())
                .orElse(name());
    }
}
