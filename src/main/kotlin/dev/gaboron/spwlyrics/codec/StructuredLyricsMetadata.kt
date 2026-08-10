package dev.gaboron.spwlyrics.codec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object StructuredLyricsMetadata {
    fun creditText(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null
        return runCatching {
            val root = Json.parseToJsonElement(trimmed) as? JsonObject ?: return@runCatching null
            if (root["t"] !is JsonPrimitive) return@runCatching null
            val parts = root["c"] as? JsonArray ?: return@runCatching null
            parts.mapNotNull { item ->
                ((item as? JsonObject)?.get("tx") as? JsonPrimitive)?.content
            }.joinToString("").takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
