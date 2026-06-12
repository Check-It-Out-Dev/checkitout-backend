package com.sm.instagram.platform.usersocialconnection;

/**
 * Represents the status of a user's social media connection.
 */
public enum ConnectionStatus {
    /**
     * Connection is active and valid
     */
    CONNECTED,
    /**
     * Connection token has expired and needs renewal
     */
    EXPIRED,
    /**
     * Connection has been explicitly revoked by the user or platform
     */
    REVOKED,
    /**
     * Connection was deauthorized via Meta platform callback.
     * The user removed our app from their Instagram settings.
     */
    DISCONNECTED
}
