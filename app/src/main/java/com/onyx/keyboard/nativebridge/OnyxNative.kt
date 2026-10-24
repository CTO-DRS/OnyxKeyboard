package com.onyx.keyboard.nativebridge

/**
 * JNI bridge to the Onyx native performance core (C++17, built via CMake/NDK).
 * Pure-Kotlin fallbacks exist in [com.onyx.keyboard.intelligence.SuggestionEngine]
 * in case the library cannot be loaded on an exotic device.
 */
object OnyxNative {

    @Volatile
    var available: Boolean = false
        private set

    fun ensureLoaded(): Boolean {
        if (available) return true
        return try {
            System.loadLibrary("onyx_native")
            available = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Bounded Levenshtein distance. Returns [maxDistance] + 1 when known to exceed the bound. */
    external fun editDistance(a: String, b: String, maxDistance: Int): Int

    /** Strips harakat/tatweel and unifies alef/ya/ha/hamza variants for fuzzy Arabic matching. */
    external fun normalizeArabic(input: String): String

    /** Fast avalanche hash (Murmur3 finalizer) used for word bucketing. */
    external fun mixHash(input: String, seed: Long): Long
}
