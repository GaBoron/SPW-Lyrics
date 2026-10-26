package dev.gaboron.spwlyrics.domain

/** A main paragraph and its nested background rows occupy one slot when the main row is moved. */
internal object LyricRowOrder {
    private fun contains(parent: EditableLyricLine, child: EditableLyricLine) =
        child.path.size >= parent.path.size && child.path.take(parent.path.size) == parent.path

    private fun paragraph(rows: List<EditableLyricLine>, row: EditableLyricLine): EditableLyricLine =
        rows.filter { contains(it, row) }.minByOrNull { it.path.size } ?: row

    fun group(rows: List<EditableLyricLine>, id: Int): Set<Int> {
        val source = rows.first { it.id == id }
        return rows.filter { contains(source, it) }.map { it.id }.toSet()
    }

    fun move(rows: List<EditableLyricLine>, id: Int, targetId: Int, after: Boolean): List<Int> {
        val moving = group(rows, id)
        if (targetId in moving) return rows.map { it.id }
        val source = paragraph(rows, rows.first { it.id == id })
        val target = paragraph(rows, rows.first { it.id == targetId })
        require(source.path.dropLast(1) == target.path.dropLast(1)) { "歌词不属于同一个分组。" }
        val remaining = rows.filter { it.id !in moving }
        val targetGroup = remaining.filter { contains(target, it) }.map { it.id }.toSet()
        val index = if (after) remaining.indexOfLast { it.id in targetGroup } + 1
            else remaining.indexOfFirst { it.id in targetGroup }
        return remaining.map { it.id }.toMutableList().apply {
            addAll(index, rows.filter { it.id in moving }.map { it.id })
        }
    }
}
