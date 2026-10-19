package dev.gaboron.spwlyrics.integration.metadataui

/** Ordered row selection is independent of text-field shortcuts and DOM edits. */
internal object MetadataLyricSelection {
    fun select(state: MetadataEditorState, id: Int, ctrl: Boolean, shift: Boolean): MetadataEditorState {
        val ids = state.structured?.lines?.map { it.id }.orEmpty()
        if (id !in ids) return state
        val anchor = state.selectionAnchor?.takeIf { it in ids } ?: state.selectedLineId ?: id
        val selected = when {
            shift -> {
                val range = ids.subList(minOf(ids.indexOf(anchor), ids.indexOf(id)), maxOf(ids.indexOf(anchor), ids.indexOf(id)) + 1).toSet()
                if (ctrl) state.selectedLineIds + range else range
            }
            ctrl -> if (id in state.selectedLineIds) state.selectedLineIds - id else state.selectedLineIds + id
            else -> setOf(id)
        }
        return state.copy(selectedLineIds = selected,
            selectedLineId = id.takeIf { it in selected } ?: ids.lastOrNull { it in selected },
            selectionAnchor = if (shift) anchor else id)
    }
}
