package dev.gaboron.spwlyrics.integration

import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

internal object ObjectGraphProbe {
    fun find(
        roots: Iterable<Any>,
        maxDepth: Int,
        matches: (Any) -> Boolean,
        mayTraverse: (Class<*>) -> Boolean,
    ): Any? {
        data class Node(val value: Any, val depth: Int)

        val queue = ArrayDeque<Node>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        roots.forEach { queue.add(Node(it, 0)) }
        while (queue.isNotEmpty()) {
            val (value, depth) = queue.removeFirst()
            if (!visited.add(value)) continue
            if (matches(value)) return value
            if (depth >= maxDepth) continue

            classHierarchy(value.javaClass).flatMap { it.declaredFields.asSequence() }
                .filterNot { Modifier.isStatic(it.modifiers) }
                .filter { mayTraverse(it.type) }
                .forEach { field ->
                    val nested = runCatching {
                        field.trySetAccessible()
                        field.get(value)
                    }.getOrNull() ?: return@forEach
                    if (mayTraverse(nested.javaClass)) queue.add(Node(nested, depth + 1))
                }
        }
        return null
    }

    private fun classHierarchy(type: Class<*>): Sequence<Class<*>> = generateSequence(type) { current ->
        current.superclass?.takeUnless { it == Any::class.java }
    }
}
