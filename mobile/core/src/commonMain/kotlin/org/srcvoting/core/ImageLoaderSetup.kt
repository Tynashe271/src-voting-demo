package org.srcvoting.core

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory

/**
 * Candidate photos are plain public URLs (Neon Object Storage or the admin container's local
 * disk), so this reuses a fresh Ktor engine rather than the session-cookied StudentApiClient/
 * AdminApiClient instances -- no auth needed, and image loading shouldn't share a cookie jar with
 * API calls. Call once per process, before the first AsyncImage is composed.
 */
fun initImageLoader(context: PlatformContext) {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
}
