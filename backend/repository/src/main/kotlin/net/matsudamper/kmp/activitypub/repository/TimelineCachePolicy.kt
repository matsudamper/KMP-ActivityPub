package net.matsudamper.kmp.activitypub.repository

object TimelineCachePolicy {
    const val CACHE_TTL_SECONDS = 7L * 24 * 60 * 60
    const val ACTIVITY_UPDATE_INTERVAL_SECONDS = 60L * 60
    const val FANOUT_ACTIVE_WINDOW_SECONDS = CACHE_TTL_SECONDS + ACTIVITY_UPDATE_INTERVAL_SECONDS
}
