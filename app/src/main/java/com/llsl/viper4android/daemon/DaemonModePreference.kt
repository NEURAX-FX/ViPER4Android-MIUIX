package com.llsl.viper4android.daemon

/**
 * Which backend is allowed to own audio state application.
 *
 * The choice decides where the driver's parameters come from, so a wrong or
 * silently changed value is not a cosmetic issue: it can leave the user with no
 * processing at all. Each constant therefore carries a stable [token] that is
 * what gets persisted - the ordinal would shift the meaning of every saved
 * value the moment this enum is reordered.
 */
enum class DaemonModePreference(
    val token: String,
) {
    /** Try the root daemon, fall back to the direct driver path when it refuses. */
    Auto("auto"),

    /** Daemon only; report degraded rather than falling back. */
    DaemonOnly("daemon_only"),

    /** Never touch the daemon. */
    DriverOnly("driver_only"),
    ;

    companion object {
        val DEFAULT: DaemonModePreference = Auto

        /**
         * Resolves a persisted token, falling back to [DEFAULT].
         *
         * Anything unrecognised - a value written by a newer build, a truncated
         * write, or a legacy ordinal - resolves to the default instead of
         * throwing, because a preference read happens on the path that decides
         * whether audio gets processed at all.
         */
        fun fromToken(token: String?): DaemonModePreference =
            entries.firstOrNull { it.token == token } ?: DEFAULT
    }
}
