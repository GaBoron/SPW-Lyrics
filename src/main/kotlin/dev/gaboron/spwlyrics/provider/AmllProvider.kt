package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.LyricsContentFilter
import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import dev.gaboron.spwlyrics.domain.ProviderSearchStatus
import java.nio.file.Path
import java.time.Clock

class AmllProvider(
    cacheDirectory: Path,
    private val http: ProviderHttp,
    private val clock: Clock = Clock.systemUTC(),
    legacyIndexPath: Path? = null,
    indexHttp: ProviderHttp = http,
) : LyricsProvider, AutoCloseable {
    override val source: LyricsSource = LyricsSource.AMLL
    private val index = AmllIndexStore(cacheDirectory, indexHttp, clock, legacyIndexPath)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        index.search(query, keywords, limit)

    override fun searchManual(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        index.search(query, keywords, limit, manual = true)

    override fun searchManual(query: TrackQuery, keywords: String, limit: Int,
        onStatus: (ProviderSearchStatus) -> Unit): List<LyricsCandidate> = index.searchManual(query, keywords, limit, onStatus)

    override fun close() = index.close()

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val url = candidate.context["url"] ?: if (candidate.remoteId.endsWith(".ttml")) {
            "$RAW_BASE/${candidate.remoteId}"
        } else {
            "$LEGACY_RAW_BASE/${candidate.remoteId}.ttml"
        }
        LyricsContentFilter.clean(TtmlCodec().parse(http.get(url), source), candidate)
    }.getOrNull()

    companion object {
        const val INDEX_URL = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/metadata/raw-lyrics-index.jsonl"
        const val RAW_BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/raw-lyrics"
        internal const val LEGACY_RAW_BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics"
    }
}
