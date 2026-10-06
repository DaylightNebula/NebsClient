package com.nebs.core.launcher

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/** Evaluates Mojang's `rules` lists and resolves rule-gated argument lists. */
internal object Rules {
    /** No rules means allowed; otherwise the last matching rule decides, defaulting to disallowed. */
    fun allowed(rules: List<Rule>?, platform: Platform, features: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, platform, features)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun matches(rule: Rule, platform: Platform, features: Map<String, Boolean>): Boolean {
        rule.os?.let { os ->
            if (os.name != null && os.name != platform.os) return false
            // Mojang uses "x86" to mean a 32-bit JVM.
            if (os.arch != null && (if (os.arch == "x86") platform.is64Bit else os.arch != platform.arch)) return false
        }
        rule.features?.forEach { (feature, required) ->
            if ((features[feature] ?: false) != required) return false
        }
        return true
    }

    fun resolve(
        args: List<JsonElement>,
        platform: Platform,
        features: Map<String, Boolean>,
        placeholders: Map<String, String>,
    ): List<String> = args.flatMap { element ->
        val values = when (element) {
            is JsonPrimitive -> listOf(element.content)
            is JsonObject -> {
                val rules = element["rules"]?.let { metaJson.decodeFromJsonElement<List<Rule>>(it) }
                if (!allowed(rules, platform, features)) emptyList()
                else when (val value = element["value"]) {
                    is JsonArray -> value.map { it.jsonPrimitive.content }
                    is JsonPrimitive -> listOf(value.content)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
        values.map { substitute(it, placeholders) }
    }

    private val PLACEHOLDER = Regex("""\$\{([a-zA-Z_]+)}""")

    fun substitute(value: String, placeholders: Map<String, String>): String =
        PLACEHOLDER.replace(value) { match ->
            placeholders[match.groupValues[1]] ?: throw IllegalStateException("No value for launch placeholder ${match.value}")
        }
}
