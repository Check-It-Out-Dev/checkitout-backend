package com.sm.instagram.platform.common.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serializable;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ItemNotFoundException extends TranslatableException {

    public ItemNotFoundException(String message, Serializable... args) {
        super(message, args);
    }
    public ItemNotFoundException(String entityName) {
        super("error.business.item_not_found", entityName);
    }
}
