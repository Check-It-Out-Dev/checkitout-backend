package com.sm.instagram.platform.common.base;

public interface UpdaterTracking {

    void setUpdaterId(String userId);

    default void setAutoUpdaterId(String userId) {
        setUpdaterId(userId);
    }
}


