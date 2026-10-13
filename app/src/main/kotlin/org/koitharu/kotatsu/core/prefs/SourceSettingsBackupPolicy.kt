package org.koitharu.kotatsu.core.prefs

import org.koitharu.kotatsu.settings.utils.validation.DomainValidator
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Explicit allowlist: never persist cookies, authorization, Cloudflare UA or future secret keys. */
object SourceSettingsBackupPolicy {
    private val flags = setOf(
        SourceSettings.KEY_SLOWDOWN, SourceSettings.KEY_NO_CAPTCHA, SourceSettings.KEY_NO_AUTO_CAPTCHA
    )
    private val strings = setOf(SourceSettings.KEY_SORT_ORDER, SourceSettings.KEY_DOMAIN)

    fun sanitize(values: Map<String, *>): Map<String, Any> =
        values.mapNotNull { (key, value) ->
            when {
                key in flags && value is Boolean -> key to value
                key == SourceSettings.KEY_DOMAIN && value is String &&
                    DomainValidator.isValidDomain(value) -> key to value
                key == SourceSettings.KEY_SORT_ORDER && value is String &&
                    SortOrder.entries.any { it.name == value } -> key to value
                else -> null
            }
        }.toMap()
}
