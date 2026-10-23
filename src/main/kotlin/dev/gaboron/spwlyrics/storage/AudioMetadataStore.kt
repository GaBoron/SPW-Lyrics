package dev.gaboron.spwlyrics.storage

import dev.gaboron.spwlyrics.domain.AudioMetadata
import dev.gaboron.spwlyrics.domain.AudioMetadataField
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.Locale

internal data class AudioFileRevision(val size: Long, val modified: FileTime, val identity: String?)
internal data class AudioMetadataSnapshot(val path: Path, val revision: AudioFileRevision, val metadata: AudioMetadata)
internal data class AudioMetadataSaved(val snapshot: AudioMetadataSnapshot, val notice: String = "")

internal interface AudioMetadataStore {
    fun read(path: Path): AudioMetadataSnapshot
    fun save(original: AudioMetadataSnapshot, edited: AudioMetadata): AudioMetadataSaved
}

/** Verify edits in system temporary storage, then commit or roll back the original file. */
internal class FileAudioMetadataStore : AudioMetadataStore {
    override fun read(path: Path): AudioMetadataSnapshot {
        val target = path.toRealPath()
        require(Files.isRegularFile(target)) { "请选择本地歌曲文件。" }
        require(extension(target) in SUPPORTED_EXTENSIONS) {
            "暂不支持此音频格式；请选择 MP3、FLAC、M4A、MP4、OGG、WAV 或 AIFF。"
        }
        val before = revision(target)
        val audio = AudioFileIO.read(target.toFile())
        val tag = audio.tag
        val values = AudioMetadataField.entries.associateWith { field ->
            // Unsupported fields must be visible as empty, but are never written unless edited.
            runCatching { tag?.getAll(field.key())?.joinToString("\n").orEmpty() }.getOrDefault("")
        }
        val metadata = AudioMetadata(values, tag?.let(EmbeddedLyricsTags::read).orEmpty())
        check(before == revision(target)) { "歌曲文件在读取期间发生变化，请重新读取。" }
        return AudioMetadataSnapshot(target, before, metadata)
    }

    @Synchronized
    override fun save(original: AudioMetadataSnapshot, edited: AudioMetadata): AudioMetadataSaved {
        val target = original.path
        require(edited != original.metadata) { "没有需要写入的修改。" }
        require(edited.lyrics.length <= 4_000_000) { "歌词过大，最多支持 400 万字符。" }
        require(Files.isWritable(target)) { "歌曲不可写，请检查文件权限。" }
        ensureUnchanged(original)
        val workspace = AudioTagWorkspace(extension(target))
        val stage = workspace.stage
        try {
            Files.copy(target, stage, COPY_ATTRIBUTES)
            ensureUnchanged(original)
            val audio = AudioFileIO.read(stage.toFile())
            // Adding ID3v2 to an ID3v1-only MP3 must carry its existing song information forward.
            if (audio is MP3File && !audio.hasID3v2Tag() && audio.hasID3v1Tag()) {
                audio.setID3v2Tag(ID3v24Tag(audio.iD3v1Tag))
            }
            val tag = audio.tagOrCreateAndSetDefault
            val changed = AudioMetadataField.entries.filter { edited[it] != original.metadata[it] }
            changed.forEach { field ->
                val value = edited[field]
                if (value.isEmpty()) tag.deleteField(field.key())
                else {
                    val values = value.split('\n')
                    // Most jaudiotagger formats consume only the first vararg in setField.
                    tag.setField(field.key(), values.first())
                    values.drop(1).forEach { tag.addField(field.key(), it) }
                }
            }
            val lyricsChanged = edited.lyrics != original.metadata.lyrics
            if (lyricsChanged) EmbeddedLyricsTags.write(tag, edited.lyrics)
            audio.commit()
            val verified = read(stage).metadata
            check(changed.all { verified[it] == edited[it] } && (!lyricsChanged || verified.lyrics == edited.lyrics)) {
                "写入后的标签校验失败，原歌曲未修改。"
            }
            ensureUnchanged(original)
            Files.copy(target, workspace.recovery, COPY_ATTRIBUTES)
            ensureUnchanged(original)
            val saved = AudioTagFileCommit.commit(stage, target, workspace.recovery, { ensureUnchanged(original) }) {
                read(target).also { snapshot ->
                    check(changed.all { snapshot.metadata[it] == edited[it] } && (!lyricsChanged || snapshot.metadata.lyrics == edited.lyrics)) {
                        "歌曲标签读回校验失败。"
                    }
                }
            }
            return AudioMetadataSaved(saved, workspace.recycle())
        } catch (error: Exception) {
            val leftover = if (error is AudioTagRollbackFailure) "" else workspace.recycle()
            throw IllegalStateException("保存失败：${error.message?.trimEnd('。') ?: "请关闭占用歌曲的程序后重试"}。$leftover", error)
        }
    }

    private fun ensureUnchanged(original: AudioMetadataSnapshot) {
        check(original.revision == revision(original.path)) { "歌曲文件已被其他程序修改，请重新读取后再保存。" }
    }

    private fun revision(path: Path): AudioFileRevision = Files.readAttributes(path, BasicFileAttributes::class.java).let {
        AudioFileRevision(it.size(), it.lastModifiedTime(), it.fileKey()?.toString())
    }

    private fun extension(path: Path) = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun AudioMetadataField.key(): FieldKey = when (this) {
        AudioMetadataField.TITLE -> FieldKey.TITLE
        AudioMetadataField.ARTIST -> FieldKey.ARTIST
        AudioMetadataField.ALBUM -> FieldKey.ALBUM
        AudioMetadataField.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
        AudioMetadataField.YEAR -> FieldKey.YEAR
        AudioMetadataField.GENRE -> FieldKey.GENRE
        AudioMetadataField.TRACK -> FieldKey.TRACK
        AudioMetadataField.DISC -> FieldKey.DISC_NO
        AudioMetadataField.COMPOSER -> FieldKey.COMPOSER
        AudioMetadataField.LYRICIST -> FieldKey.LYRICIST
        AudioMetadataField.LANGUAGE -> FieldKey.LANGUAGE
        AudioMetadataField.COPYRIGHT -> FieldKey.COPYRIGHT
        AudioMetadataField.COMMENT -> FieldKey.COMMENT
    }

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "wav", "aif", "aiff")
    }
}
