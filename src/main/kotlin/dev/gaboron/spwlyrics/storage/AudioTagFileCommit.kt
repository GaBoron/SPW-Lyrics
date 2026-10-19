package dev.gaboron.spwlyrics.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

internal class AudioTagRollbackFailure(recovery: Path, cause: Exception) :
    IllegalStateException("回退原文件失败，请停止播放后从系统临时恢复文件恢复：$recovery", cause)

/** Supports players which allow file writes but hold a handle that prevents replacing the directory entry. */
internal object AudioTagFileCommit {
    fun <T> commit(stage: Path, target: Path, recovery: Path, ensureUnchanged: () -> Unit, verify: () -> T): T {
        var mutated = false
        var output: FileChannel? = null
        try {
            ensureUnchanged()
            try {
                Files.move(stage, target, ATOMIC_MOVE, REPLACE_EXISTING)
                mutated = true
            } catch (blocked: FileSystemException) {
                ensureUnchanged()
                // Do not truncate when opening: a write lock must fail before changing any bytes.
                output = try { FileChannel.open(target, WRITE) } catch (error: Exception) {
                    throw IllegalStateException("歌曲正被播放器或同步程序占用，原文件未修改。请切换歌曲或等待同步完成后重试。", error)
                }
                ensureUnchanged()
                FileChannel.open(stage, READ).use { input ->
                    mutated = true
                    copy(input, output)
                }
            }
            return verify()
        } catch (error: Exception) {
            if (mutated) {
                try {
                    if (output != null) FileChannel.open(recovery, READ).use { copy(it, output) }
                    else FileChannel.open(target, WRITE).use { channel -> FileChannel.open(recovery, READ).use { copy(it, channel) } }
                    check(Files.mismatch(recovery, target) == -1L) { "原文件恢复校验失败。" }
                    Files.setLastModifiedTime(target, Files.getLastModifiedTime(recovery))
                } catch (restore: Exception) {
                    error.addSuppressed(restore)
                    throw AudioTagRollbackFailure(recovery, error)
                }
                throw IllegalStateException("写入或校验失败，已回退原文件；草稿保留，可重试。", error)
            }
            throw error
        } finally {
            runCatching { output?.close() }
        }
    }

    private fun copy(input: FileChannel, output: FileChannel) {
        input.position(0)
        output.position(0)
        val buffer = ByteBuffer.allocate(128 * 1024)
        while (true) {
            buffer.clear()
            if (input.read(buffer) < 0) break
            buffer.flip()
            while (buffer.hasRemaining()) output.write(buffer)
        }
        output.truncate(input.size())
        output.force(true)
    }
}
