package com.catkiss62.supergemmatest

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ModelImporter {
    data class SourceInfo(val name: String, val size: Long)

    fun inspect(context: Context, uri: Uri): SourceInfo {
        var name = uri.lastPathSegment ?: "model.litertlm"
        var size = -1L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                    ?.let { name = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        require(name.lowercase().endsWith(".litertlm")) { "请选择扩展名为 .litertlm 的模型文件。" }
        require(size in MIN_MODEL_SIZE..MAX_MODEL_SIZE) {
            "模型文件大小异常：${humanSize(size)}；本测试允许 100MB～8GB。"
        }
        return SourceInfo(name, size)
    }

    fun import(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): ImportedModelInfo {
        val source = inspect(context, uri)
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val importsDir = File(root, "imports").apply { mkdirs() }
        val required = source.size + 512L * 1024L * 1024L
        val available = StatFs(importsDir.absolutePath).availableBytes
        require(available > required) {
            "可用空间不足。导入需要约 ${humanSize(required)}，当前只有 ${humanSize(available)}。"
        }

        val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val finalFile = File(importsDir, safeName)
        val partialFile = File(importsDir, "$safeName.partial")
        partialFile.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        var lastProgressAt = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(partialFile).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    copied += count
                    val now = System.currentTimeMillis()
                    if (now - lastProgressAt >= 200L) {
                        lastProgressAt = now
                        onProgress((copied.toDouble() / source.size.toDouble()).toFloat().coerceIn(0f, 1f))
                    }
                }
                output.fd.sync()
            }
        } ?: error("无法打开模型文件。")

        require(copied == source.size) {
            partialFile.delete()
            "复制不完整：预期 ${humanSize(source.size)}，实际 ${humanSize(copied)}。"
        }
        if (finalFile.exists() && !finalFile.delete()) {
            partialFile.delete()
            error("无法替换旧模型文件。")
        }
        check(partialFile.renameTo(finalFile)) { "模型导入完成，但无法保存最终文件。" }
        onProgress(1f)
        return ImportedModelInfo(
            path = finalFile.absolutePath,
            fileName = source.name,
            sizeBytes = copied,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> "未知"
        bytes >= 1024L * 1024L * 1024L -> "%.2fGB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1fKB".format(bytes / 1024.0)
    }

    private const val MIN_MODEL_SIZE = 100L * 1024L * 1024L
    private const val MAX_MODEL_SIZE = 8L * 1024L * 1024L * 1024L
}
