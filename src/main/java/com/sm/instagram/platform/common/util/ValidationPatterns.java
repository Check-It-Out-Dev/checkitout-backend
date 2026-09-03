package com.sm.instagram.platform.common.util;

public class ValidationPatterns {
    /**
     * An absolute https URL, or a path rooted at this application.
     *
     * <p>The https branch is what production stores: every persisted media URL
     * is minted by the backend and points at its own storage bucket. The
     * relative branch exists because a path carries no host at all — it can
     * only ever resolve to the application serving it, so it cannot be used to
     * point a stored link at somewhere hostile (the substitution risk that
     * hardened these fields in the first place). The dev-lite simulator serves
     * uploads from the application itself and therefore stores such paths.
     */
    public static final String HTTPS_URL_PATTERN =
            "^(?:https://[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,63}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"
                    + "|/(?:[-a-zA-Z0-9()@:%_\\+.~#?&=]+/?)*)$";

    private ValidationPatterns() {
    }
}
