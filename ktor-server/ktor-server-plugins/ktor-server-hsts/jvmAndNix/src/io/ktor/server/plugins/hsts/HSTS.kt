/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.hsts

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 *  A configuration for the [HSTS] plugin.
 */
@KtorDsl
public class HSTSConfig {
    /**
     * Specifies the `preload` HSTS directive, which allows you to include your domain name
     * in the HSTS preload list.
     */
    public var preload: Boolean = false

    /**
     * Specifies the `includeSubDomains` directive, which applies this policy to any subdomains as well.
     */
    public var includeSubDomains: Boolean = true

    /**
     * Specifies how long (in seconds) the client should keep the host in a list of known HSTS hosts:
     */
    public var maxAgeInSeconds: Long = DEFAULT_HSTS_MAX_AGE
        set(newMaxAge) {
            check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Allows you to add custom directives supported by a specific user agent.
     */
    public val customDirectives: MutableMap<String, String?> = HashMap()
}

internal const val DEFAULT_HSTS_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

/**
 * A plugin that appends the `Strict-Transport-Security` HTTP header to every response.
 *
 * The [HSTS] configuration below specifies how long the client
 * should keep the host in a list of known HSTS hosts:
 * ```kotlin
 * install(HSTS) {
 *     maxAgeInSeconds = 10
 * }
 * ```
 * You can learn more from [HSTS](https://ktor.io/docs/hsts.html).
 */
public val HSTS: RouteScopedPlugin<HSTSConfig> = createRouteScopedPlugin("HSTS", ::HSTSConfig) {
    /**
     * A constructed `Strict-Transport-Security` header value.
     */
    val headerValue: String = buildString {
        append("max-age=")
        append(pluginConfig.maxAgeInSeconds)

        if (pluginConfig.includeSubDomains) {
            append("; includeSubDomains")
        }
        if (pluginConfig.preload) {
            append("; preload")
        }

        if (pluginConfig.customDirectives.isNotEmpty()) {
            pluginConfig.customDirectives.entries.joinTo(this, separator = "; ", prefix = "; ") {
                if (it.value != null) {
                    "${it.key.escapeIfNeeded()}=${it.value?.escapeIfNeeded()}"
                } else {
                    it.key.escapeIfNeeded()
                }
            }
        }
    }

    onCall { call ->
        if (call.request.origin.run { scheme == "https" && port == 443 }) {
            call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
        }
    }
}