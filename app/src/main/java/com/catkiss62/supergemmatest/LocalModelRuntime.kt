package com.catkiss62.supergemmatest

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalModelRuntime : Closeable {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var loadedPath: String? = null
        private set

    suspend fun load(context: Context, modelPath: String, backend: LocalBackend) = mutex.withLock {
        closeUnlocked()
        require(File(modelPath).isFile) { "导入的模型文件已经不存在，请重新导入。" }
        val textBackend = if (backend == LocalBackend.GPU) Backend.GPU() else Backend.CPU()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = textBackend,
            // Gemma 4 Android vision currently needs the GPU vision executor.
            visionBackend = Backend.GPU(),
            cacheDir = context.cacheDir.absolutePath,
        )
        val newEngine = Engine(config)
        try {
            newEngine.initialize()
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "你是本地视觉分析模型。准确、客观地描述图片，不臆测未显示的内容。",
                    ),
                ),
            )
            engine = newEngine
            conversation = newConversation
            loadedPath = modelPath
        } catch (error: Throwable) {
            runCatching { newEngine.close() }
            throw error
        }
    }

    suspend fun recognize(image: PreparedImage, prompt: String): String = mutex.withLock {
        val activeConversation = conversation ?: error("模型尚未加载。")
        val response = activeConversation.sendMessage(
            Contents.of(
                Content.ImageBytes(image.jpegBytes),
                Content.Text(prompt),
            ),
        )
        response.toString().ifBlank { "模型未返回文本。" }
    }

    override fun close() {
        closeUnlocked()
    }

    private fun closeUnlocked() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        loadedPath = null
    }
}
