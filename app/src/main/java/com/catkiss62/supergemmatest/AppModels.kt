package com.catkiss62.supergemmatest

data class ChatMessage(
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT, LOCAL_MODEL, ERROR }
}

data class ImportedModelInfo(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class PreparedImage(
    val jpegBytes: ByteArray,
    val displayName: String,
    val width: Int,
    val height: Int,
)

enum class LocalBackend(val label: String) {
    CPU("CPU（较稳）"),
    GPU("GPU（较快，可能不兼容）"),
}
