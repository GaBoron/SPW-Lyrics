package dev.gaboron.spwlyrics.provider

import dev.gaboron.spwlyrics.codec.TtmlCodec
import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.TrackQuery
import java.nio.file.Path
import java.time.Clock

class AmllProvider(
    cacheDirectory: Path,
    private val http: ProviderHttp,
    private val clock: Clock = Clock.systemUTC(),
    legacyIndexPath: Path? = null,
) : LyricsProvider {
    override val source: LyricsSource = LyricsSource.AMLL
    private val index = AmllIndexStore(cacheDirectory, http, clock, legacyIndexPath)

    override fun search(query: TrackQuery, keywords: String, limit: Int): List<LyricsCandidate> =
        index.search(query, keywords, limit)

    override fun fetch(candidate: LyricsCandidate): LyricsDocument? = runCatching {
        val url = candidate.context["url"] ?: "$RAW_BASE/${candidate.remoteId}.ttml"
        TtmlCodec().parse(http.get(url), source)
    }.getOrNull()?.takeIf { it.lines.isNotEmpty() }

    companion object {
        const val INDEX_URL = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics/index.jsonl"
        const val RAW_BASE = "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/am-lyrics"
    }
}
