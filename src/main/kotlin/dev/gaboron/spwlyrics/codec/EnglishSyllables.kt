package dev.gaboron.spwlyrics.codec

import kotlinx.serialization.json.*
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Liang pattern matching, using the same English (US) pattern data as the upstream editor. */
internal object EnglishSyllables {
    private val definition by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/hyphen/en-us.json.gz"))
        GZIPInputStream(stream).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonArray }
    }

    fun split(word: String): List<String> {
        if (word.length < 5) return listOf(word)
        val lower = word.lowercase(Locale.ROOT).replace('’', '\'')
        val exceptions = definition[2].jsonObject
        val boundaries = exceptions[lower]?.jsonArray?.map { it.jsonPrimitive.int } ?: boundaries(lower)
        return (listOf(0) + boundaries + word.length).zipWithNext { from, to -> word.substring(from, to) }
    }

    private fun boundaries(word: String): List<Int> {
        val text = ".$word."
        val weights = IntArray(word.length + 1)
        val table = definition[0].jsonArray
        val root = definition[1].jsonObject
        for (start in 0 until text.length - 2) {
            val base = (start - 1).coerceAtLeast(0)
            var branch = root
            for (end in start until text.length) {
                val node = branch[text[end].toString()] ?: break
                val index = when (node) {
                    is JsonArray -> node[1].jsonPrimitive.int
                    is JsonPrimitive -> node.int
                    else -> null
                }
                if (index != null) table[index].jsonArray.forEachIndexed { offset, weight ->
                    if (base + offset < weights.size) weights[base + offset] = maxOf(weights[base + offset], weight.jsonPrimitive.int)
                }
                branch = when (node) {
                    is JsonArray -> node[0].jsonObject
                    is JsonObject -> node
                    else -> break
                }
            }
        }
        return (2..word.length - 2).filter { weights[it] % 2 == 1 }
    }
}
