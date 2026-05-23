package com.vn.security.core.security;

/**
 * Cache names used by identity-related integrations.
 *
 * <p>The starter no longer owns a concrete user repository, but permission and
 * role writes still evict this cache name so consumer identity adapters can use
 * the same convention if they cache users by login.</p>
 */
public final class UserCacheNames {

    public static final String USERS_BY_LOGIN = "usersByLogin";

    private UserCacheNames() {}
}
