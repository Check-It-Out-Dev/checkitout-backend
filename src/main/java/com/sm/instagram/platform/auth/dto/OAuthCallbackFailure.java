package com.sm.instagram.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the Instagram OAuth callback sends when the caller is not a browser it can redirect.
 *
 * <p>The callback answers a browser with 302 and no body. A non-browser client gets this instead,
 * and it is deliberately not the application's error envelope: the shape predates it and is what
 * the mobile clients read. Typing it says so rather than leaving the document to guess.
 */
@Schema(description = "An OAuth callback that could not be completed, for a caller that cannot be redirected")
public record OAuthCallbackFailure(boolean success, String error) {
}
