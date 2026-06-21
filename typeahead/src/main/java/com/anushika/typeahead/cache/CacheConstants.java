package com.anushika.typeahead.cache;

/**
 * Global constants for the cache package.
 *
 * Centralizing these values ensures that changes to the namespace, ranking limits,
 * or prefix lengths are applied uniformly across the entire system.
 */
public final class CacheConstants {

    /** Redis key namespace for all prefix caches. */
    public static final String PREFIX_NAMESPACE = "prefix:";

    /** Maximum number of suggestions to store / retrieve per prefix. */
    public static final int TOP_K = 10;

    /** Minimum prefix length to cache or query. */
    public static final int MIN_PREFIX_LENGTH = 3;

    private CacheConstants() {
        // Prevent instantiation
    }
}
