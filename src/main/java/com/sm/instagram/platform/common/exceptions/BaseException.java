package com.sm.instagram.platform.common.exceptions;

import lombok.extern.slf4j.Slf4j;

@Deprecated
@Slf4j
public abstract class BaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected BaseException(String message) {
        super(message);
        log.info(this.getMessage(), this);
    }

    protected BaseException(String message, Throwable cause) {
        super(message, cause);
        log.info(this.getMessage(), this);
    }
}